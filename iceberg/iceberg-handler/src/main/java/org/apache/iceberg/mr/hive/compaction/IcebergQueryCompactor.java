/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iceberg.mr.hive.compaction;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.metastore.Warehouse;
import org.apache.hadoop.hive.metastore.api.CompactionType;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.txn.entities.CompactionInfo;
import org.apache.hadoop.hive.ql.Context.RewritePolicy;
import org.apache.hadoop.hive.ql.DriverUtils;
import org.apache.hadoop.hive.ql.ErrorMsg;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.HiveUtils;
import org.apache.hadoop.hive.ql.metadata.VirtualColumn;
import org.apache.hadoop.hive.ql.parse.TransformSpec;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.ql.txn.compactor.CompactorContext;
import org.apache.hadoop.hive.ql.txn.compactor.QueryCompactor;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hive.iceberg.org.apache.orc.storage.common.TableName;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Table;
import org.apache.iceberg.hive.HiveSchemaUtil;
import org.apache.iceberg.mr.hive.HiveIcebergFilterFactory;
import org.apache.iceberg.mr.hive.IcebergTableUtil;
import org.apache.iceberg.mr.hive.compaction.evaluator.CompactionEvaluator;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IcebergQueryCompactor extends QueryCompactor  {

  private static final Logger LOG = LoggerFactory.getLogger(IcebergQueryCompactor.class.getName());

  @Override
  public boolean run(CompactorContext context) throws IOException, HiveException, InterruptedException {

    String compactTableName = TableName.getDbTable(context.getTable().getDbName(), context.getTable().getTableName());
    Map<String, String> tblProperties = context.getTable().getParameters();
    LOG.debug("Initiating compaction for the {} table", compactTableName);

    HiveConf conf = new HiveConf(context.getConf());
    CompactionInfo ci = context.getCompactionInfo();
    String compactionQuery = buildCompactionQuery(context, compactTableName, conf);

    SessionState sessionState = setupQueryCompactionSession(conf, ci, tblProperties);
    String compactionTarget = "table " + HiveUtils.unparseIdentifier(compactTableName) +
        (ci.partName != null ? ", partition " + HiveUtils.unparseIdentifier(ci.partName) : "");

    try {
      DriverUtils.runOnDriver(conf, sessionState, compactionQuery);
      LOG.info("Completed compaction for {}", compactionTarget);
      return true;
    } catch (HiveException e) {
      LOG.error("Failed compacting {}", compactionTarget, e);
      throw e;
    } finally {
      sessionState.setCompaction(false);
    }
  }

  private String buildCompactionQuery(CompactorContext context, String compactTableName, HiveConf conf)
      throws HiveException {
    CompactionInfo ci = context.getCompactionInfo();
    org.apache.hadoop.hive.ql.metadata.Table table = Hive.get(conf).getTable(context.getTable().getDbName(),
        context.getTable().getTableName());
    Table icebergTable = IcebergTableUtil.getTable(conf, table.getTTable());
    String orderBy = ci.orderByClause == null ? "" : ci.orderByClause;
    String fileSizePredicate = null;
    String compactionQuery;

    if (ci.type == CompactionType.MINOR) {
      long fileSizeInBytesThreshold = CompactionEvaluator.getFragmentSizeBytes(table.getParameters());
      fileSizePredicate = String.format("%1$s in (select file_path from %2$s.files where file_size_in_bytes < %3$d)",
          VirtualColumn.FILE_PATH.getName(), compactTableName, fileSizeInBytesThreshold);
      conf.setLong(CompactorContext.COMPACTION_FILE_SIZE_THRESHOLD, fileSizeInBytesThreshold);
      // IOW query containing a join with Iceberg .files metadata table fails with exception that Iceberg AVRO format
      // doesn't support vectorization, hence disabling it in this case.
      conf.setBoolVar(ConfVars.HIVE_VECTORIZATION_ENABLED, false);
    }

    if (ci.partName == null) {
      if (!icebergTable.spec().isPartitioned()) {
        HiveConf.setVar(conf, ConfVars.REWRITE_POLICY, RewritePolicy.FULL_TABLE.name());
        compactionQuery = String.format("insert overwrite table %s select * from %<s %2$s %3$s", compactTableName,
            fileSizePredicate == null ? "" : "where " + fileSizePredicate, orderBy);
      } else if (icebergTable.specs().size() > 1) {
        // Compacting partitions of old partition specs on a partitioned table with partition evolution
        HiveConf.setVar(conf, ConfVars.REWRITE_POLICY, RewritePolicy.PARTITION.name());
        // A single filter on a virtual column causes errors during compilation,
        // added another filter on file_path as a workaround.
        compactionQuery = String.format("insert overwrite table %1$s select * from %1$s " +
                "where %2$s != %3$d and %4$s is not null %5$s %6$s",
            compactTableName, VirtualColumn.PARTITION_SPEC_ID.getName(), icebergTable.spec().specId(),
            VirtualColumn.FILE_PATH.getName(), fileSizePredicate == null ? "" : "and " + fileSizePredicate, orderBy);
      } else {
        // Partitioned table without partition evolution with partition spec as null in the compaction request - this
        // code branch is not supposed to be reachable
        throw new HiveException(ErrorMsg.COMPACTION_NO_PARTITION);
      }
    } else {
      HiveConf.setBoolVar(conf, ConfVars.HIVE_CONVERT_JOIN, false);
      conf.setBoolVar(ConfVars.HIVE_VECTORIZATION_ENABLED, false);
      HiveConf.setVar(conf, ConfVars.REWRITE_POLICY, RewritePolicy.PARTITION.name());
      conf.set(IcebergCompactionService.PARTITION_PATH, new Path(ci.partName).toString());

      PartitionSpec spec;
      String partitionPredicate;
      try {
        spec = IcebergTableUtil.getPartitionSpec(icebergTable, ci.partName);
        partitionPredicate = buildPartitionPredicate(ci, spec);
      } catch (MetaException e) {
        throw new HiveException(e);
      }

      compactionQuery = String.format("INSERT OVERWRITE TABLE %1$s SELECT * FROM %1$s WHERE %2$s IN " +
          "(SELECT FILE_PATH FROM %1$s.FILES WHERE %3$s AND SPEC_ID = %4$d) %5$s %6$s",
      compactTableName, VirtualColumn.FILE_PATH.getName(), partitionPredicate, spec.specId(),
      fileSizePredicate == null ? "" : "AND " + fileSizePredicate, orderBy);
    }
    return compactionQuery;
  }

  private String buildPartitionPredicate(CompactionInfo ci, PartitionSpec spec) throws MetaException {
    Map<String, String> partSpecMap = Warehouse.makeSpecFromName(ci.partName);
    Map<String, PartitionField> partitionFieldMap = spec.fields().stream()
        .collect(Collectors.toMap(PartitionField::name, Function.identity()));

    Types.StructType partitionType = spec.partitionType();
    return  partitionType.fields().stream().map(field -> {
      String value = partSpecMap.get(field.name());
      String literal = "NULL";

      if (value != null && !value.equals("null")) {
        String type = HiveSchemaUtil.convertToTypeString(field.type());
        PartitionField partitionField = partitionFieldMap.get(field.name());
        TransformSpec transformSpec = TransformSpec.fromString(partitionField.transform().toString(), field.name());
        literal = TypeInfoUtils.convertStringToLiteralForSQL(
            HiveIcebergFilterFactory.convertPartitionLiteral(value, transformSpec).toString(),
            ((PrimitiveTypeInfo) TypeInfoUtils.getTypeInfoFromTypeString(type)).getPrimitiveCategory());
      }

      return String.format("`partition`.%s %s %s", HiveUtils.unparseIdentifier(field.name()),
          Objects.equals(literal, "NULL") ? "IS" : "=", literal);
    }).collect(Collectors.joining(" AND "));
  }
}
