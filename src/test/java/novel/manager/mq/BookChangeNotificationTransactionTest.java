package novel.manager.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;
import novel.core.constant.AmqpConsts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Protects the existing after-commit contract without connecting to a broker or application DB. */
class BookChangeNotificationTransactionTest {

    private final AmqpTemplate broker = mock(AmqpTemplate.class);
    private final AmqpMsgManager messages = new AmqpMsgManager(broker);
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:book-notification-" + UUID.randomUUID(), "sa", "");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        ReflectionTestUtils.setField(messages, "amqpEnabled", true);
    }

    @Test
    void sendsOnlyAfterSuccessfulCommit() {
        transaction.executeWithoutResult(status -> {
            messages.sendBookChangeMsg(20L);
            verifyNoInteractions(broker);
        });
        verify(broker).convertAndSend(AmqpConsts.BookChangeMq.EXCHANGE_NAME, null, 20L);
        verifyNoMoreInteractions(broker);
    }

    @Test
    void rollbackDiscardsNotification() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            messages.sendBookChangeMsg(20L);
            verifyNoInteractions(broker);
            throw new IllegalStateException("fixture failure");
        })).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(broker);
    }

    @Test
    void rollbackOnlyAlsoDiscardsNotification() {
        transaction.executeWithoutResult(status -> {
            messages.sendBookChangeMsg(20L);
            status.setRollbackOnly();
        });
        verifyNoInteractions(broker);
    }

    @Test
    void disabledNotificationsDoNotContactBroker() {
        ReflectionTestUtils.setField(messages, "amqpEnabled", false);
        transaction.executeWithoutResult(status -> messages.sendBookChangeMsg(20L));
        verifyNoInteractions(broker);
    }
}
