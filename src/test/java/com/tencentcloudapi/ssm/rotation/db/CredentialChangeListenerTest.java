package com.tencentcloudapi.ssm.rotation.db;

import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * CredentialChangeListener 单元测试
 */
public class CredentialChangeListenerTest {

    @Test
    public void testFunctionalInterface_lambda() {
        List<String> changes = new ArrayList<>();

        CredentialChangeListener listener = (oldAccount, newAccount) -> {
            changes.add(oldAccount.getUserName() + " -> " + newAccount.getUserName());
        };

        DbAccount oldAccount = new DbAccount("user1", "pass1");
        DbAccount newAccount = new DbAccount("user2", "pass2");
        listener.onCredentialChanged(oldAccount, newAccount);

        assertEquals(1, changes.size());
        assertEquals("user1 -> user2", changes.get(0));
    }

    @Test
    public void testMultipleListeners() {
        AtomicInteger callCount = new AtomicInteger(0);

        CredentialChangeListener listener1 = (old, newAcc) -> callCount.incrementAndGet();
        CredentialChangeListener listener2 = (old, newAcc) -> callCount.incrementAndGet();
        CredentialChangeListener listener3 = (old, newAcc) -> callCount.incrementAndGet();

        DbAccount oldAccount = new DbAccount("user1", "pass1");
        DbAccount newAccount = new DbAccount("user2", "pass2");

        listener1.onCredentialChanged(oldAccount, newAccount);
        listener2.onCredentialChanged(oldAccount, newAccount);
        listener3.onCredentialChanged(oldAccount, newAccount);

        assertEquals(3, callCount.get());
    }

    @Test
    public void testListenerReceivesCorrectAccounts() {
        final DbAccount[] received = new DbAccount[2];

        CredentialChangeListener listener = (oldAccount, newAccount) -> {
            received[0] = oldAccount;
            received[1] = newAccount;
        };

        DbAccount oldAccount = new DbAccount("admin", "oldpass");
        DbAccount newAccount = new DbAccount("admin_v2", "newpass");
        listener.onCredentialChanged(oldAccount, newAccount);

        assertEquals("admin", received[0].getUserName());
        assertEquals("oldpass", received[0].getPassword());
        assertEquals("admin_v2", received[1].getUserName());
        assertEquals("newpass", received[1].getPassword());
    }

    @Test
    public void testListenerWithSameUsername_passwordRotation() {
        final boolean[] called = {false};

        CredentialChangeListener listener = (oldAccount, newAccount) -> {
            // 同一用户名，密码轮转
            assertEquals(oldAccount.getUserName(), newAccount.getUserName());
            assertNotEquals(oldAccount.getPassword(), newAccount.getPassword());
            called[0] = true;
        };

        DbAccount oldAccount = new DbAccount("admin", "oldpass");
        DbAccount newAccount = new DbAccount("admin", "newpass");
        listener.onCredentialChanged(oldAccount, newAccount);

        assertTrue(called[0]);
    }
}
