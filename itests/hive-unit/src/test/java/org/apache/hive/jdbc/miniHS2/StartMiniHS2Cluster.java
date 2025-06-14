/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hive.jdbc.miniHS2;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.hive.conf.Constants;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.ql.ServiceContext;
import org.apache.hive.jdbc.miniHS2.MiniHS2.MiniClusterType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class StartMiniHS2Cluster {
  private static MiniHS2 miniHS2 = null;

  /**
   * Not a unit test - this simply runs a MiniHS2 cluster, which can be used for manual testing.
   */
  @Test
  public void testRunCluster() throws Exception {
    if (!Boolean.parseBoolean(System.getProperty("miniHS2.run", "false"))) {
      return;
    }

    MiniClusterType clusterType = MiniClusterType.valueOf(System.getProperty("miniHS2.clusterType", "MR").toUpperCase());
    String confFilesProperty = System.getProperty("miniHS2.conf", "../../data/conf/hive-site.xml");
    boolean usePortsFromConf = Boolean.parseBoolean(System.getProperty("miniHS2.usePortsFromConf", "false"));
    boolean isMetastoreRemote = Boolean.getBoolean("miniHS2.isMetastoreRemote");
    boolean withHouseKeepingThreads = Boolean.getBoolean("miniHS2.withHouseKeepingThreads");
    boolean queryHistory = Boolean.getBoolean("miniHS2.queryHistory");

    // Load conf files
    String[] confFiles = confFilesProperty.split(",");
    int idx;
    for (idx = 0; idx < confFiles.length; ++idx) {
      String confFile = confFiles[idx];
      if (confFile.isEmpty()) {
        continue;
      }
      HiveConf.setHiveSiteLocation(new URL("file://"+ new File(confFile).toURI().getPath()));
      break;
    }
    HiveConf conf = new HiveConf();
    conf.setBoolVar(ConfVars.HIVE_SUPPORT_CONCURRENCY, false);
    conf.setBoolVar(HiveConf.ConfVars.HIVE_RPC_QUERY_PLAN, true);
    conf.set(Constants.CLUSTER_ID_HIVE_CONF_PROP, ServiceContext.findClusterId());

    for (; idx < confFiles.length; ++idx) {
      String confFile = confFiles[idx];
      if (confFile.isEmpty()) {
        continue;
      }
      conf.addResource(new URL("file://" + new File(confFile).toURI().getPath()));
    }

    miniHS2 = new MiniHS2.Builder().withConf(conf).withClusterType(clusterType).withPortsFromConf(usePortsFromConf)
        .withRemoteMetastore(isMetastoreRemote).withHouseKeepingThreads(withHouseKeepingThreads)
        .withQueryHistory(queryHistory).build();
    Map<String, String> confOverlay = new HashMap<String, String>();
    miniHS2.start(confOverlay);

    System.out.println("JDBC URL available at " + miniHS2.getJdbcURL());

    // MiniHS2 cluster is up .. let it run until someone kills the test
    while (true) {
      Thread.sleep(1000);
    }
  }
}
