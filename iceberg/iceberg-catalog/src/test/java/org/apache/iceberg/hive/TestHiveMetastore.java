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

package org.apache.iceberg.hive;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HMSHandler;
import org.apache.hadoop.hive.metastore.HMSHandlerProxyFactory;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.TSetIpAddressProcessor;
import org.apache.hadoop.hive.metastore.api.GetTableRequest;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.utils.TestTxnDbUtil;
import org.apache.iceberg.ClientPool;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.hadoop.Util;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;

import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHiveMetastore {

  private static final String DEFAULT_DATABASE_NAME = "default";
  private static final int DEFAULT_POOL_SIZE = 5;

  // create the metastore handlers based on whether we're working with Hive2 or Hive3 dependencies
  // we need to do this because there is a breaking API change between Hive2 and Hive3
  private static final DynConstructors.Ctor<HMSHandler> HMS_HANDLER_CTOR = DynConstructors.builder()
          .impl(HMSHandler.class, String.class, Configuration.class)
          .impl(HMSHandler.class, String.class, HiveConf.class)
          .build();

  private static final DynMethods.StaticMethod GET_BASE_HMS_HANDLER = DynMethods.builder("getProxy")
          .impl(HMSHandlerProxyFactory.class, Configuration.class, IHMSHandler.class, boolean.class)
          .impl(HMSHandlerProxyFactory.class, HiveConf.class, IHMSHandler.class, boolean.class)
          .buildStatic();

  // Hive3 introduces background metastore tasks (MetastoreTaskThread) for performing various cleanup duties. These
  // threads are scheduled and executed in a static thread pool (org.apache.hadoop.hive.metastore.ThreadPool).
  // This thread pool is shut down normally as part of the JVM shutdown hook, but since we're creating and tearing down
  // multiple metastore instances within the same JVM, we have to call this cleanup method manually, otherwise
  // threads from our previous test suite will be stuck in the pool with stale config, and keep on being scheduled.
  // This can lead to issues, e.g. accidental Persistence Manager closure by ScheduledQueryExecutionsMaintTask.
  private static final DynMethods.StaticMethod METASTORE_THREADS_SHUTDOWN = DynMethods.builder("shutdown")
          .impl("org.apache.hadoop.hive.metastore.ThreadPool")
          .orNoop()
          .buildStatic();

  // It's tricky to clear all static fields in an HMS instance in order to switch derby root dir.
  // Therefore, we reuse the same derby root between tests and remove it after JVM exits.
  private static final File HIVE_LOCAL_DIR;
  private static final File HIVE_WAREHOUSE_DIR;
  private static final File HIVE_EXTERNAL_WAREHOUSE_DIR;
  private static final String DERBY_PATH;

  static {
    try {
      HIVE_LOCAL_DIR = createTempDirectory("hive", asFileAttribute(fromString("rwxrwxrwx"))).toFile();
      DERBY_PATH = new File(HIVE_LOCAL_DIR, "metastore_db").getPath();
      HIVE_WAREHOUSE_DIR = new File(HIVE_LOCAL_DIR, "managed");
      HIVE_EXTERNAL_WAREHOUSE_DIR = new File(HIVE_LOCAL_DIR, "external");
      File derbyLogFile = new File(HIVE_LOCAL_DIR, "derby.log");
      System.setProperty("derby.stream.error.file", derbyLogFile.getAbsolutePath());
      setupMetastoreDB("jdbc:derby:" + DERBY_PATH + ";create=true");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        Path localDirPath = new Path(HIVE_LOCAL_DIR.getAbsolutePath());
        FileSystem fs = Util.getFs(localDirPath, new Configuration());
        String errMsg = "Failed to delete " + localDirPath;
        try {
          assertThat(fs.delete(localDirPath, true)).as(errMsg).isTrue();
        } catch (IOException e) {
          throw new RuntimeException(errMsg, e);
        }
      }));
    } catch (Exception e) {
      throw new RuntimeException("Failed to setup local dir for hive metastore", e);
    }
  }

  private HiveConf hiveConf;
  private ExecutorService executorService;
  private TServer server;
  private HMSHandler baseHandler;
  private HiveClientPool clientPool;

  /**
   * Starts a TestHiveMetastore with the default connection pool size (5) and the default HiveConf.
   */
  public void start() {
    start(new HiveConf(new Configuration(), TestHiveMetastore.class), DEFAULT_POOL_SIZE);
  }

  /**
   * Starts a TestHiveMetastore with the default connection pool size (5) with the provided HiveConf.
   * @param conf The hive configuration to use
   */
  public void start(HiveConf conf) {
    start(conf, DEFAULT_POOL_SIZE);
  }

  /**
   * Starts a TestHiveMetastore with a provided connection pool size and HiveConf.
   * @param conf The hive configuration to use
   * @param poolSize The number of threads in the executor pool
   */
  public void start(HiveConf conf, int poolSize) {
    start(conf, poolSize, false);
  }

  /**
   * Starts a TestHiveMetastore with a provided connection pool size and HiveConf.
   *
   * @param conf The hive configuration to use
   * @param poolSize The number of threads in the executor pool
   * @param directSql Used to turn on directSql
   */
  public void start(HiveConf conf, int poolSize, boolean directSql) {
    try {
      TServerSocket socket = new TServerSocket(0);
      int port = socket.getServerSocket().getLocalPort();
      initConf(conf, port, directSql);

      this.hiveConf = conf;
      this.server = newThriftServer(socket, poolSize, hiveConf);
      this.executorService = Executors.newSingleThreadExecutor();
      this.executorService.submit(() -> server.serve());

      // in Hive3, setting this as a system prop ensures that it will be picked up whenever a new HiveConf is created
      System.setProperty(HiveConf.ConfVars.METASTORE_URIS.varname, hiveConf.getVar(HiveConf.ConfVars.METASTORE_URIS));

      this.clientPool = new HiveClientPool(1, hiveConf);
    } catch (Exception e) {
      throw new RuntimeException("Cannot start TestHiveMetastore", e);
    }
  }

  public void stop() throws Exception {
    reset();
    if (clientPool != null) {
      clientPool.close();
    }
    if (server != null) {
      server.stop();
    }
    if (executorService != null) {
      executorService.shutdown();
    }
    if (baseHandler != null) {
      baseHandler.shutdown();
    }
    METASTORE_THREADS_SHUTDOWN.invoke();
  }

  public HiveConf hiveConf() {
    return hiveConf;
  }

  public String getDatabasePath(String dbName) {
    File dbDir = new File(HIVE_LOCAL_DIR, dbName + ".db");
    return dbDir.getPath();
  }

  public void reset() throws Exception {
    if (clientPool != null) {
      for (String dbName : clientPool.run(client -> client.getAllDatabases())) {
        for (String tblName : clientPool.run(client -> client.getAllTables(dbName))) {
          clientPool.run(client -> {
            client.dropTable(dbName, tblName, true, true, true);
            return null;
          });
        }

        if (!DEFAULT_DATABASE_NAME.equals(dbName)) {
          // Drop cascade, functions dropped by cascade
          clientPool.run(client -> {
            client.dropDatabase(dbName, true, true, true);
            return null;
          });
        }
      }
    }

    Path warehouseRoot = new Path(HIVE_LOCAL_DIR.getAbsolutePath());
    FileSystem fs = Util.getFs(warehouseRoot, hiveConf);
    for (FileStatus fileStatus : fs.listStatus(warehouseRoot)) {
      if (!fileStatus.getPath().getName().equals("derby.log") &&
          !fileStatus.getPath().getName().equals("metastore_db")) {
        fs.delete(fileStatus.getPath(), true);
      }
    }
  }

  public Table getTable(String dbName, String tableName) throws TException, InterruptedException {
    return clientPool.run(client -> client.getTable(new GetTableRequest(dbName, tableName)));
  }

  public Table getTable(TableIdentifier identifier) throws TException, InterruptedException {
    return getTable(identifier.namespace().toString(), identifier.name());
  }

  public <R> R run(ClientPool.Action<R, IMetaStoreClient, TException> action) throws InterruptedException, TException {
    return clientPool.run(action, false);
  }

  private TServer newThriftServer(TServerSocket socket, int poolSize, HiveConf conf) throws Exception {
    HiveConf serverConf = new HiveConf(conf);
    serverConf.set(HiveConf.ConfVars.METASTORE_CONNECT_URL_KEY.varname, "jdbc:derby:" + DERBY_PATH + ";create=true");
    baseHandler = HMS_HANDLER_CTOR.newInstance("new db based metaserver", serverConf);
    IHMSHandler handler = GET_BASE_HMS_HANDLER.invoke(serverConf, baseHandler, false);

    TThreadPoolServer.Args args = new TThreadPoolServer.Args(socket)
        .processor(new TSetIpAddressProcessor<>(handler))
        .transportFactory(new TTransportFactory())
        .protocolFactory(new TBinaryProtocol.Factory())
        .minWorkerThreads(poolSize)
        .maxWorkerThreads(poolSize);

    return new TThreadPoolServer(args);
  }

  private void initConf(HiveConf conf, int port, boolean directSql) {
    conf.set(HiveConf.ConfVars.METASTORE_URIS.varname, "thrift://localhost:" + port);
    conf.set(HiveConf.ConfVars.METASTORE_WAREHOUSE.varname, "file:" + HIVE_WAREHOUSE_DIR.getAbsolutePath());
    conf.set(HiveConf.ConfVars.HIVE_METASTORE_WAREHOUSE_EXTERNAL.varname,
        "file:" + HIVE_EXTERNAL_WAREHOUSE_DIR.getAbsolutePath());
    conf.set(HiveConf.ConfVars.METASTORE_TRY_DIRECT_SQL.varname, String.valueOf(directSql));
    conf.set(HiveConf.ConfVars.METASTORE_DISALLOW_INCOMPATIBLE_COL_TYPE_CHANGES.varname, "false");
    conf.set("iceberg.hive.client-pool-size", "2");
    // set to false so that TxnManager#checkLock does not throw exception when using UNSET data type operation
    // in the requested lock component
    conf.setBoolVar(HiveConf.ConfVars.HIVE_IN_TEST, false);
  }

  private static void setupMetastoreDB(String dbURL) throws Exception {
    HiveConf conf = new HiveConf();
    MetastoreConf.setVar(conf, MetastoreConf.ConfVars.CONNECT_URL_KEY,
        "jdbc:derby:" + DERBY_PATH + ";create=true");
    TestTxnDbUtil.prepDb(conf);
  }
}
