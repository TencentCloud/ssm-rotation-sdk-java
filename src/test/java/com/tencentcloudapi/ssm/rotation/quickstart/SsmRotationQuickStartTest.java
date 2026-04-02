package com.tencentcloudapi.ssm.rotation.quickstart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * QuickStart 单元测试
 */
public class SsmRotationQuickStartTest {

    @Test
    public void testSingleBuilder_shouldBuildOptionsWithDefaults() {
        SsmRotationDataSourceOptions options = SsmRotationQuickStart.single()
                .region("ap-guangzhou")
                .permanentCredential("id", "key")
                .secretName("test-secret")
                .db("127.0.0.1", 3306, "testdb")
                .buildOptions();

        assertEquals(SsmRotationPoolType.DRUID, options.getPoolType());
        assertEquals("ap-guangzhou", options.getRegion());
        assertEquals("id", options.getSecretId());
        assertEquals("key", options.getSecretKey());
        assertEquals("test-secret", options.getSecretName());
        assertEquals("127.0.0.1", options.getIpAddress());
        assertEquals(3306, options.getPort());
        assertEquals("testdb", options.getDbName());
        assertNotNull(options.getDruid());
        assertNotNull(options.getHikari());
        assertNotNull(options.getDbcp());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultiBuilder_shouldRejectBlankName() {
        SsmRotationQuickStart.multi()
                .add(" ", SsmRotationDataSourceOptions.builder().build());
    }
}
