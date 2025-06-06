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
package org.apache.hadoop.hive.conf;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hive.common.util.HiveTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


/**
 * TestHiveConf
 *
 * Test cases for HiveConf. Loads configuration files located
 * in common/src/test/resources.
 */
public class TestHiveConf {
  @Test
  public void testHiveSitePath() throws Exception {
    String expectedPath = HiveTestUtils.getFileFromClasspath("hive-site.xml");
    String hiveSiteLocation = HiveConf.getHiveSiteLocation().getPath();
    Assert.assertEquals(expectedPath, hiveSiteLocation);
  }

  private void checkHadoopConf(String name, String expectedHadoopVal) throws Exception {
    Assert.assertEquals(expectedHadoopVal, new JobConf(HiveConf.class).get(name));
  }

  private void checkConfVar(ConfVars var, String expectedConfVarVal) throws Exception {
    Assert.assertEquals(expectedConfVarVal, var.getDefaultValue());
  }

  private void checkHiveConf(String name, String expectedHiveVal) throws Exception {
    Assert.assertEquals(expectedHiveVal, new HiveConf().get(name));
  }

  @Test
  public void testConfProperties() throws Exception {
    // Make sure null-valued ConfVar properties do not override the Hadoop Configuration
    // NOTE: Comment out the following test case for now until a better way to test is found,
    // as this test case cannot be reliably tested. The reason for this is that Hive does
    // overwrite fs.default.name in HiveConf if the property is set in system properties.
    // checkHadoopConf(ConfVars.HADOOPFS.varname, "core-site.xml");
    // checkConfVar(ConfVars.HADOOPFS, null);
    // checkHiveConf(ConfVars.HADOOPFS.varname, "core-site.xml");

    // Make sure non-null-valued ConfVar properties *do* override the Hadoop Configuration
    checkHadoopConf(ConfVars.HADOOP_NUM_REDUCERS.varname, "1");
    checkConfVar(ConfVars.HADOOP_NUM_REDUCERS, "-1");
    checkHiveConf(ConfVars.HADOOP_NUM_REDUCERS.varname, "-1");

    // Non-null ConfVar only defined in ConfVars
    checkHadoopConf(ConfVars.HIVE_SKEWJOIN_KEY.varname, null);
    checkConfVar(ConfVars.HIVE_SKEWJOIN_KEY, "100000");
    checkHiveConf(ConfVars.HIVE_SKEWJOIN_KEY.varname, "100000");

    // ConfVar overridden in in hive-site.xml
    checkHadoopConf(ConfVars.HIVE_TEST_MODE_DUMMY_STAT_AGGR.varname, null);
    checkConfVar(ConfVars.HIVE_TEST_MODE_DUMMY_STAT_AGGR, "");
    checkHiveConf(ConfVars.HIVE_TEST_MODE_DUMMY_STAT_AGGR.varname, "value2");

    //Property defined for hive masking algorithm
    checkConfVar(ConfVars.HIVE_MASKING_ALGO, "sha256");
    checkHiveConf(ConfVars.HIVE_MASKING_ALGO.varname, "sha256");

    // Property defined in hive-site.xml only
    checkHadoopConf("test.property1", null);
    checkHiveConf("test.property1", "value1");

    // Test HiveConf property variable substitution in hive-site.xml
    checkHiveConf("test.var.hiveconf.property", ConfVars.DEFAULT_PARTITION_NAME.getDefaultValue());
  }

  @Test
  public void testColumnNameMapping() throws Exception {
    for (int i = 0 ; i < 20 ; i++ ){
      Assert.assertTrue(i == HiveConf.getPositionFromInternalName(HiveConf.getColumnInternalName(i)));
    }
  }

  @Test
  public void testUnitFor() throws Exception {
    Assert.assertEquals(TimeUnit.SECONDS, HiveConf.unitFor("L", TimeUnit.SECONDS));
    Assert.assertEquals(TimeUnit.MICROSECONDS, HiveConf.unitFor("", TimeUnit.MICROSECONDS));
    Assert.assertEquals(TimeUnit.DAYS, HiveConf.unitFor("d", null));
    Assert.assertEquals(TimeUnit.DAYS, HiveConf.unitFor("days", null));
    Assert.assertEquals(TimeUnit.HOURS, HiveConf.unitFor("h", null));
    Assert.assertEquals(TimeUnit.HOURS, HiveConf.unitFor("hours", null));
    Assert.assertEquals(TimeUnit.MINUTES, HiveConf.unitFor("m", null));
    Assert.assertEquals(TimeUnit.MINUTES, HiveConf.unitFor("minutes", null));
    Assert.assertEquals(TimeUnit.SECONDS, HiveConf.unitFor("s", null));
    Assert.assertEquals(TimeUnit.SECONDS, HiveConf.unitFor("seconds", null));
    Assert.assertEquals(TimeUnit.MILLISECONDS, HiveConf.unitFor("ms", null));
    Assert.assertEquals(TimeUnit.MILLISECONDS, HiveConf.unitFor("msecs", null));
    Assert.assertEquals(TimeUnit.MICROSECONDS, HiveConf.unitFor("us", null));
    Assert.assertEquals(TimeUnit.MICROSECONDS, HiveConf.unitFor("usecs", null));
    Assert.assertEquals(TimeUnit.NANOSECONDS, HiveConf.unitFor("ns", null));
    Assert.assertEquals(TimeUnit.NANOSECONDS, HiveConf.unitFor("nsecs", null));
  }

  @Test
  public void testToSizeBytes() throws Exception {
    Assert.assertEquals(1L, HiveConf.toSizeBytes("1b"));
    Assert.assertEquals(1L, HiveConf.toSizeBytes("1bytes"));
    Assert.assertEquals(1024L, HiveConf.toSizeBytes("1kb"));
    Assert.assertEquals(1048576L, HiveConf.toSizeBytes("1mb"));
    Assert.assertEquals(1073741824L, HiveConf.toSizeBytes("1gb"));
    Assert.assertEquals(1099511627776L, HiveConf.toSizeBytes("1tb"));
    Assert.assertEquals(1125899906842624L, HiveConf.toSizeBytes("1pb"));
  }

  @Test
  public void testHiddenConfig() throws Exception {
    HiveConf conf = new HiveConf();

    // check that a change to the hidden list should fail
    try {
      final String name = HiveConf.ConfVars.HIVE_CONF_HIDDEN_LIST.varname;
      conf.verifyAndSet(name, "");
      conf.verifyAndSet(name + "postfix", "");
      Assert.fail("Setting config property " + name + " should fail");
    } catch (IllegalArgumentException e) {
      // the verifyAndSet in this case is expected to fail with the IllegalArgumentException
    }

    ArrayList<String> hiddenList = Lists.newArrayList(
        HiveConf.ConfVars.METASTORE_PWD.varname,
        HiveConf.ConfVars.HIVE_SERVER2_SSL_KEYSTORE_PASSWORD.varname,
        HiveConf.ConfVars.HIVE_SERVER2_WEBUI_SSL_KEYSTORE_PASSWORD.varname,
        "fs.s3.awsSecretAccessKey",
        "fs.s3n.awsSecretAccessKey",
        "dfs.adls.oauth2.credential",
        "fs.adl.oauth2.credential",
        "fs.azure.account.oauth2.client.secret"
    );

    for (String hiddenConfig : hiddenList) {
      // check configs are hidden
      Assert.assertTrue("config " + hiddenConfig + " should be hidden",
          conf.isHiddenConfig(hiddenConfig));
      // check stripHiddenConfigurations removes the property
      Configuration conf2 = new Configuration(conf);
      conf2.set(hiddenConfig, "password");
      conf.stripHiddenConfigurations(conf2);
      // check that a property that begins the same is also hidden
      Assert.assertTrue(conf.isHiddenConfig(
          hiddenConfig + "postfix"));
      // Check the stripped property is the empty string
      Assert.assertEquals("", conf2.get(hiddenConfig));
    }
  }

  @Test
  public void testLockedConfig() throws Exception {
    HiveConf conf = new HiveConf();
    // Set the default value of the config
    conf.setVar(ConfVars.HIVE_EXECUTION_ENGINE, "mr");
    String defaultVal = conf.get(ConfVars.HIVE_EXECUTION_ENGINE.varname);
    // Update the lockedSet variable
    conf.addToLockedSet(ConfVars.HIVE_EXECUTION_ENGINE.varname);
    // Update the value of sample/test config
    conf.verifyAndSet(ConfVars.HIVE_EXECUTION_ENGINE.varname, "tez");
    String modifiedVal = conf.get(ConfVars.HIVE_EXECUTION_ENGINE.varname);
    // Check if the value is changed.
    Assert.assertEquals(defaultVal, modifiedVal);
  }

  @Test
  public void testEncodingDecoding() throws UnsupportedEncodingException {
    HiveConf conf = new HiveConf();
    String query = "select blah, '\u0001' from random_table";
    conf.setQueryString(query);
    Assert.assertEquals(URLEncoder.encode(query, "UTF-8"), conf.get(ConfVars.HIVE_QUERY_STRING.varname));
    Assert.assertEquals(query, conf.getQueryString());
  }

  @Test
  public void testAdditionalConfigFiles() throws Exception{
    URL url = ClassLoader.getSystemResource("hive-site.xml");
    File fileHiveSite = new File(url.getPath());

    String parFolder = fileHiveSite.getParent();
    //back up hive-site.xml
    String bakHiveSiteFileName = parFolder + "/hive-site-bak.xml";
    File fileBakHiveSite = new File(bakHiveSiteFileName);
    FileUtils.copyFile(fileHiveSite, fileBakHiveSite);

    String content = FileUtils.readFileToString(fileHiveSite);
    content = content.substring(0, content.lastIndexOf("</configuration>"));

    String testHiveSiteString = content + "<property>\n" +
            " <name>HIVE_SERVER2_PLAIN_LDAP_DOMAIN</name>\n" +
            " <value>a.com</value>\n" +
            "</property>\n" +
            "\n" +
            " <property>\n" +
            "   <name>hive.additional.config.files</name>\n" +
            "   <value>ldap-site.xml,other.xml</value>\n" +
            "   <description>additional config dir for Hive to load</description>\n" +
            " </property>\n" +
            "\n" +
            "</configuration>";

    FileUtils.writeStringToFile(fileHiveSite, testHiveSiteString);

    String testLdapString = """
            <?xml version="1.0"?>
            <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
            <configuration>
              <property>
              <name>hive.server2.authentication.ldap.Domain</name>
              <value>b.com</value>
            </property>
            
            </configuration>\
            """;


    String newFileName = parFolder+"/ldap-site.xml";
    File f2 = new File(newFileName);
    FileUtils.writeStringToFile(f2, testLdapString);

    HiveConf conf = new HiveConf();
    String val = conf.getVar(ConfVars.HIVE_SERVER2_PLAIN_LDAP_DOMAIN);
    Assert.assertEquals("b.com", val);
    //restore and clean up
    FileUtils.copyFile(fileBakHiveSite, fileHiveSite);
    f2.delete();
    fileBakHiveSite.delete();
  }
}
