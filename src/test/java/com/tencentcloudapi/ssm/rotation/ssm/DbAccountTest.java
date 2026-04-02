package com.tencentcloudapi.ssm.rotation.ssm;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DbAccount 单元测试
 */
public class DbAccountTest {

    @Test
    public void testConstructor() {
        DbAccount account = new DbAccount("user1", "pass1");
        assertEquals("user1", account.getUserName());
        assertEquals("pass1", account.getPassword());
    }

    @Test
    public void testDefaultConstructor() {
        DbAccount account = new DbAccount();
        assertNull(account.getUserName());
        assertNull(account.getPassword());
    }

    @Test
    public void testAllArgsConstructor() {
        DbAccount account = new DbAccount("user2", "pass2");
        assertEquals("user2", account.getUserName());
        assertEquals("pass2", account.getPassword());
    }

    @Test
    public void testEquals_same() {
        DbAccount a = new DbAccount("user", "pass");
        DbAccount b = new DbAccount("user", "pass");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEquals_differentUser() {
        DbAccount a = new DbAccount("user1", "pass");
        DbAccount b = new DbAccount("user2", "pass");
        assertNotEquals(a, b);
    }

    @Test
    public void testEquals_differentPassword() {
        DbAccount a = new DbAccount("user", "pass1");
        DbAccount b = new DbAccount("user", "pass2");
        assertNotEquals(a, b);
    }

    @Test
    public void testEquals_null() {
        DbAccount a = new DbAccount("user", "pass");
        assertNotEquals(a, null);
    }

    @Test
    public void testEquals_self() {
        DbAccount a = new DbAccount("user", "pass");
        assertEquals(a, a);
    }

    @Test
    public void testToString_masksPassword() {
        DbAccount account = new DbAccount("admin", "supersecret");
        String str = account.toString();
        assertTrue(str.contains("admin"));
        assertTrue(str.contains("****"));
        assertFalse(str.contains("supersecret"));
    }
}
