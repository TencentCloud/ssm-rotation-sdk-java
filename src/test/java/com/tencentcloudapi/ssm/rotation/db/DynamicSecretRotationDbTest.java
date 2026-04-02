package com.tencentcloudapi.ssm.rotation.db;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DbConfig;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import com.tencentcloudapi.ssm.rotation.ssm.SsmRequester;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * DynamicSecretRotationDb 单元测试
 */
public class DynamicSecretRotationDbTest {

    @Test
    public void testRetryWithRefreshedCredential_noExtraStaleConnectionAttempt() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        DbAccount newAccount = new DbAccount("user_new", "pwd_new");
        Connection newConnection = mock(Connection.class);

        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);
        AtomicInteger oldCredentialAttempts = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount, newAccount));
             MockedStatic<DriverManager> driverManagerMocked = mockStatic(DriverManager.class)) {

            driverManagerMocked.when(() -> DriverManager.getConnection(any(String.class), any(Properties.class)))
                    .thenAnswer(invocation -> {
                        totalAttempts.incrementAndGet();
                        Properties props = invocation.getArgument(1);
                        String user = props.getProperty("user");
                        if ("user_old".equals(user)) {
                            oldCredentialAttempts.incrementAndGet();
                            throw new SQLException("Access denied for user", "28000", 1045);
                        }
                        if ("user_new".equals(user)) {
                            return newConnection;
                        }
                        throw new SQLException("Unexpected test user: " + user);
                    });

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                Connection actualConnection = rotationDb.getConnection();
                assertSame(newConnection, actualConnection);
                assertEquals("Stale credential should only be attempted once", 1, oldCredentialAttempts.get());
                assertEquals("Expected one failed attempt + one successful retry", 2, totalAttempts.get());
                assertEquals("user_new", rotationDb.getCurrentUser());
                assertTrue(requesterMocked.constructed().size() >= 1);
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testRetryWithRefreshedCredential_credentialUnchanged_shouldThrowOriginalException() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount, oldAccount));
             MockedStatic<DriverManager> driverManagerMocked = mockStatic(DriverManager.class)) {

            driverManagerMocked.when(() -> DriverManager.getConnection(any(String.class), any(Properties.class)))
                    .thenAnswer(invocation -> {
                        totalAttempts.incrementAndGet();
                        throw new SQLException("Access denied for user", "28000", 1045);
                    });

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                rotationDb.getConnection();
                fail("Expected SQLException");
            } catch (SQLException e) {
                assertEquals(1045, e.getErrorCode());
                assertEquals(1, totalAttempts.get());
                verify(requesterMocked.constructed().get(0), times(2))
                        .getCurrentAccount("test-secret");
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testGetConnection_nonAuthenticationError_shouldNotRefreshCredential() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount));
             MockedStatic<DriverManager> driverManagerMocked = mockStatic(DriverManager.class)) {

            driverManagerMocked.when(() -> DriverManager.getConnection(any(String.class), any(Properties.class)))
                    .thenAnswer(invocation -> {
                        throw new SQLException("connection timeout", "08S01", 2000);
                    });

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                rotationDb.getConnection();
                fail("Expected SQLException");
            } catch (SQLException e) {
                assertEquals(2000, e.getErrorCode());
                verify(requesterMocked.constructed().get(0), times(1))
                        .getCurrentAccount("test-secret");
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testGetConnection_refreshFails_shouldWrapException() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount)
                        .thenThrow(new SsmRotationException(SsmRotationException.ERROR_SSM, "ssm unavailable")));
             MockedStatic<DriverManager> driverManagerMocked = mockStatic(DriverManager.class)) {

            driverManagerMocked.when(() -> DriverManager.getConnection(any(String.class), any(Properties.class)))
                    .thenAnswer(invocation -> {
                        throw new SQLException("Access denied for user", "28000", 1045);
                    });

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                rotationDb.getConnection();
                fail("Expected SQLException");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("credential refresh also failed"));
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testGetCurrentAccount_returnsSnapshotCopy() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                DbAccount snapshot = rotationDb.getCurrentAccount();
                assertNotNull(snapshot);
                assertEquals("user_old", snapshot.getUserName());
                assertEquals("pwd_old", snapshot.getPassword());
                assertNotSame(oldAccount, snapshot);
                assertEquals(1, requesterMocked.constructed().size());
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testAddAndRemoveCredentialChangeListener() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                CredentialChangeListener listener = (before, after) -> { };
                rotationDb.addCredentialChangeListener(listener);
                assertTrue(rotationDb.removeCredentialChangeListener(listener));
                assertFalse(rotationDb.removeCredentialChangeListener(listener));

                try {
                    rotationDb.addCredentialChangeListener(null);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException expected) {
                    // expected
                }
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testClose_shouldNotShutdownExternalExecutors() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            rotationDb.close();

            verify(executors.watchScheduler, never()).shutdown();
            verify(executors.listenerExecutor, never()).shutdown();
            assertEquals(1, requesterMocked.constructed().size());
        }
    }

    @Test
    public void testGetConnection_whenClosed_shouldThrow() throws Exception {
        DbAccount oldAccount = new DbAccount("user_old", "pwd_old");
        MockExecutors executors = mockExecutors();
        DynamicSecretRotationConfig config = validConfig(executors.watchScheduler, executors.listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(oldAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            rotationDb.close();
            try {
                rotationDb.getConnection();
                fail("Expected SQLException");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("closed"));
            }
            assertEquals(1, requesterMocked.constructed().size());
        }
    }

    @Test
    public void testWatch_shouldBackoffAndRecoverToBaseInterval() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DbAccount rotatedAccount = new DbAccount("user_new", "pwd_new");
        ScheduledExecutorService watchScheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> normalFuture = (ScheduledFuture<Object>) mock(ScheduledFuture.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> backoffFuture = (ScheduledFuture<Object>) mock(ScheduledFuture.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> recoveredFuture = (ScheduledFuture<Object>) mock(ScheduledFuture.class);
        AtomicInteger scheduleCount = new AtomicInteger(0);
        when(watchScheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    int count = scheduleCount.incrementAndGet();
                    if (count == 1) {
                        return normalFuture;
                    }
                    if (count == 2) {
                        return backoffFuture;
                    }
                    return recoveredFuture;
                });
        when(watchScheduler.isShutdown()).thenReturn(false);

        ExecutorService listenerExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();
            return null;
        }).when(listenerExecutor).execute(any(Runnable.class));
        when(listenerExecutor.isShutdown()).thenReturn(false);

        DynamicSecretRotationConfig config = validConfig(watchScheduler, listenerExecutor);
        SsmRotationException watchFailure = new SsmRotationException(SsmRotationException.ERROR_SSM, "watch failed");

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(initialAccount)
                        .thenThrow(watchFailure, watchFailure, watchFailure, watchFailure, watchFailure, watchFailure)
                        .thenReturn(rotatedAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                for (int i = 0; i < 6; i++) {
                    invokeWatch(rotationDb);
                }
                invokeWatch(rotationDb);

                verify(watchScheduler, times(3))
                        .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
                verify(watchScheduler)
                        .scheduleWithFixedDelay(any(Runnable.class), eq(2000L), eq(2000L), eq(TimeUnit.MILLISECONDS));
                verify(watchScheduler)
                        .scheduleWithFixedDelay(any(Runnable.class), eq(1000L), eq(1000L), eq(TimeUnit.MILLISECONDS));

                verify(normalFuture).cancel(false);
                verify(backoffFuture).cancel(false);
                verifyNoMoreInteractions(recoveredFuture);

                assertEquals(1, requesterMocked.constructed().size());
            } finally {
                rotationDb.close();
            }
        }
    }

    @Test
    public void testRescheduleWatch_whenRejected_shouldKeepCurrentWatcher() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        ScheduledExecutorService watchScheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> normalFuture = (ScheduledFuture<Object>) mock(ScheduledFuture.class);
        AtomicInteger scheduleCount = new AtomicInteger(0);
        when(watchScheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    if (scheduleCount.incrementAndGet() == 1) {
                        return normalFuture;
                    }
                    throw new RejectedExecutionException("mock reject");
                });
        when(watchScheduler.isShutdown()).thenReturn(false);

        ExecutorService listenerExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();
            return null;
        }).when(listenerExecutor).execute(any(Runnable.class));
        when(listenerExecutor.isShutdown()).thenReturn(false);

        DynamicSecretRotationConfig config = validConfig(watchScheduler, listenerExecutor);

        try (MockedConstruction<SsmRequester> requesterMocked = mockConstruction(
                SsmRequester.class,
                (mock, context) -> when(mock.getCurrentAccount("test-secret"))
                        .thenReturn(initialAccount))) {

            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            try {
                boolean rescheduled = invokeRescheduleWatch(rotationDb, 2000L, 2000L, "unit-test");
                assertFalse(rescheduled);
                verify(normalFuture, never()).cancel(false);
                assertTrue(rotationDb.getHealthCheckResult().getLastError().contains("rejected"));
                assertEquals(1, requesterMocked.constructed().size());
            } finally {
                rotationDb.close();
            }
        }
    }

    private DynamicSecretRotationConfig validConfig(ScheduledExecutorService watchScheduler,
                                                    ExecutorService listenerExecutor) {
        DbConfig dbConfig = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("testdb")
                .build();

        SsmAccount ssmAccount = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");

        return DynamicSecretRotationConfig.builder()
                .dbConfig(dbConfig)
                .ssmServiceConfig(ssmAccount)
                .watchChangeIntervalMs(1000)
                .watchScheduler(watchScheduler)
                .credentialChangeExecutor(listenerExecutor)
                .build();
    }

    private MockExecutors mockExecutors() {
        ScheduledExecutorService watchScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> watchFuture = mock(ScheduledFuture.class);
        when(watchScheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> watchFuture);
        when(watchScheduler.isShutdown()).thenReturn(false);

        ExecutorService listenerExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();
            return null;
        }).when(listenerExecutor).execute(any(Runnable.class));
        when(listenerExecutor.isShutdown()).thenReturn(false);

        return new MockExecutors(watchScheduler, listenerExecutor);
    }

    private void invokeWatch(DynamicSecretRotationDb rotationDb) throws Exception {
        Method watchMethod = DynamicSecretRotationDb.class.getDeclaredMethod("watch");
        watchMethod.setAccessible(true);
        watchMethod.invoke(rotationDb);
    }

    private boolean invokeRescheduleWatch(DynamicSecretRotationDb rotationDb,
                                          long initialDelay,
                                          long interval,
                                          String reason) throws Exception {
        Method method = DynamicSecretRotationDb.class.getDeclaredMethod(
                "rescheduleWatch", long.class, long.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(rotationDb, initialDelay, interval, reason);
    }

    private static class MockExecutors {
        private final ScheduledExecutorService watchScheduler;
        private final ExecutorService listenerExecutor;

        private MockExecutors(ScheduledExecutorService watchScheduler, ExecutorService listenerExecutor) {
            this.watchScheduler = watchScheduler;
            this.listenerExecutor = listenerExecutor;
        }
    }
}
