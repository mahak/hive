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
package org.apache.hadoop.hive.llap.daemon.impl;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.llap.LlapDaemonInfo;
import org.apache.hadoop.hive.llap.daemon.rpc.LlapDaemonProtocolProtos;
import org.apache.hadoop.hive.llap.metrics.LlapMetricsSystem;
import org.apache.hadoop.hive.llap.metrics.MetricsUtils;
import org.apache.hadoop.hive.llap.registry.impl.LlapRegistryService;
import org.apache.hadoop.metrics2.MetricsSystem;
import org.apache.hive.common.util.ReflectionUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.InstanceField;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Integer.parseInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class TestLlapDaemon {

  private static final String[] METRICS_SOURCES = new String[]{
      "JvmMetrics",
      "LlapDaemonExecutorMetrics-" + MetricsUtils.getHostName(),
      "LlapDaemonJvmMetrics-" + MetricsUtils.getHostName(),
      MetricsUtils.METRICS_PROCESS_NAME
  };

  public static final String TEST_LOCAL_DIR = new File(System.getProperty("java.io.tmpdir") +
      File.separator + TestLlapDaemon.class.getCanonicalName()
      + "-" + System.currentTimeMillis()
  ).getPath().replaceAll("\\\\", "/");

  private Configuration hiveConf = new HiveConf();

  @Mock
  private LlapRegistryService mockRegistry;

  @Captor
  private ArgumentCaptor<Iterable<Map.Entry<String, String>>> captor;

  private LlapDaemon daemon;
  private String[] localDirs = new String[] {TEST_LOCAL_DIR};
  private int defaultWebPort = HiveConf.ConfVars.LLAP_DAEMON_WEB_PORT.defaultIntVal;

  @Before
  public void setUp() {
    initMocks(this);
    setupConf(hiveConf);
    LlapDaemonInfo.initialize("testDaemon", hiveConf);
  }

  private void setupConf(Configuration conf) {
    HiveConf.setVar(conf, HiveConf.ConfVars.LLAP_DAEMON_SERVICE_HOSTS, "localhost");
    HiveConf.setBoolVar(conf, HiveConf.ConfVars.HIVE_IN_TEST, true);
  }

  @After
  public void tearDown() {
    MetricsSystem ms = LlapMetricsSystem.instance();
    for (String mSource : METRICS_SOURCES) {
      ms.unregisterSource(mSource);
    }
    if (daemon != null) {
      daemon.shutdown();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEnforceProperNumberOfIOThreads() throws IOException {
    HiveConf.setIntVar(hiveConf, HiveConf.ConfVars.LLAP_IO_THREADPOOL_SIZE, 3);

    daemon = new LlapDaemon(hiveConf, 4, LlapDaemon.getTotalHeapSize(), true, false,
            -1, new String[1], 0, false, 0,0, 0, defaultWebPort, "TestLlapDaemon");
  }

   @Test
  public void testLocalDirCleaner() throws IOException, InterruptedException {
    HiveConf.setTimeVar(hiveConf, HiveConf.ConfVars.LLAP_LOCAL_DIR_CLEANER_CLEANUP_INTERVAL, 2, TimeUnit.SECONDS);
    HiveConf.setTimeVar(hiveConf, HiveConf.ConfVars.LLAP_LOCAL_DIR_CLEANER_FILE_MODIFY_TIME_THRESHOLD, 1,
        TimeUnit.SECONDS);

    createFile(localDirs[0] + "/hive/appcache/file1");
    createFile(localDirs[0] + "/hive/appcache/file2");
    createFile(localDirs[0] + "/file3");

    daemon = new LlapDaemon(hiveConf, 1, LlapDaemon.getTotalHeapSize(), false, false,
        -1, localDirs, 0, false, 0,0, 0, defaultWebPort, "TestLlapDaemon");
    daemon.init(hiveConf);

    assertFileExists(localDirs[0] + "/hive/appcache/file1", true);
    assertFileExists(localDirs[0] + "/hive/appcache/file2", true);
    assertFileExists(localDirs[0] + "/file3", true);

    daemon.start();
    Thread.sleep(5000);

    assertFileExists(localDirs[0] + "/hive/appcache/file1", false);
    assertFileExists(localDirs[0] + "/hive/appcache/file2", false);
    assertFileExists(localDirs[0] + "/file3", false);

    // folder is preserved
    assertFileExists(localDirs[0] + "/hive/appcache", true);
  }

  private void assertFileExists(String strPath, boolean exists) {
    assertEquals(strPath + " " + (exists ? "doesn't exist" : "exists"), exists, Files.exists(Paths.get(strPath)));
  }

  private void createFile(String strPath) throws IOException {
    Path path = Paths.get(strPath);
    Files.createDirectories(path.getParent());
    Files.createFile(path);
  }

  @Test
  public void testUpdateRegistration() throws IOException {
    // Given
    int enabledExecutors = 0;
    int enabledQueue = 2;

    daemon = new LlapDaemon(hiveConf, 1, LlapDaemon.getTotalHeapSize(), false, false,
        -1, new String[1], 0, false, 0,0, 0, defaultWebPort, "TestLlapDaemon");

    trySetMock(daemon, LlapRegistryService.class, mockRegistry);

    // When
    daemon.setCapacity(LlapDaemonProtocolProtos.SetCapacityRequestProto.newBuilder()
        .setQueueSize(enabledQueue)
        .setExecutorNum(enabledExecutors)
        .build());
    verify(mockRegistry).updateRegistration(captor.capture());

    // Then
    Map<String, String> attributes = StreamSupport.stream(captor.getValue().spliterator(), false)
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

    assertTrue(attributes.containsKey(LlapRegistryService.LLAP_DAEMON_NUM_ENABLED_EXECUTORS));
    assertTrue(attributes.containsKey(LlapRegistryService.LLAP_DAEMON_TASK_SCHEDULER_ENABLED_WAIT_QUEUE_SIZE));
    assertEquals(enabledQueue,
        parseInt(attributes.get(LlapRegistryService.LLAP_DAEMON_TASK_SCHEDULER_ENABLED_WAIT_QUEUE_SIZE)));
    assertEquals(enabledExecutors,
        parseInt(attributes.get(LlapRegistryService.LLAP_DAEMON_NUM_ENABLED_EXECUTORS)));

  }

  static <T> void trySetMock(Object o, Class<T> clazz, T mock) {
    List<InstanceField> instanceFields = ReflectionUtil.allDeclaredFieldsOf(o).stream()
        .filter(instanceField -> clazz.isAssignableFrom(instanceField.jdkField().getType())).toList();
    if (instanceFields.size() != 1) {
      throw new RuntimeException("Mocking is only supported, if only one field is assignable from the given class.");
    }
    InstanceField instanceField = instanceFields.get(0);
    instanceField.set(mock);
  }
}
