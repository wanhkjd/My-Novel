package novel.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import novel.core.ai.memory.RedisChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @InjectMocks
    private RedisChatMemory memory;

    private static final String CONV = "aiChatMemory::1001::conv1";
    private static final String KEY = "Cache::Novel::aiChatMemory::1001::conv1";

    @Test
    void add_pushesEachMessage_thenTrimsAndSetsTtl() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        memory.add(CONV, List.of(new UserMessage("你好"), new AssistantMessage("少侠好")));

        verify(listOps).rightPush(eq(KEY), contains("你好"));
        verify(listOps).rightPush(eq(KEY), contains("少侠好"));
        verify(listOps).trim(KEY, -20, -1);
        verify(redisTemplate).expire(eq(KEY), any(Duration.class));
    }

    @Test
    void get_deserializesMessagesInOrderWithCorrectTypes() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, -20, -1)).thenReturn(List.of(
            "{\"role\":\"user\",\"content\":\"你好\"}",
            "{\"role\":\"assistant\",\"content\":\"少侠好\"}"));

        List<Message> msgs = memory.get(CONV, 20);

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(msgs.get(0).getContent()).isEqualTo("你好");
        assertThat(msgs.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(msgs.get(1).getContent()).isEqualTo("少侠好");
    }

    @Test
    void get_returnsEmptyList_whenRedisReturnsNull() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, -20, -1)).thenReturn(null);

        assertThat(memory.get(CONV, 20)).isEmpty();
    }

    @Test
    void get_skipsCorruptEntries() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, -20, -1)).thenReturn(List.of(
            "not-a-json", "{\"role\":\"user\",\"content\":\"有效\"}"));

        List<Message> msgs = memory.get(CONV, 20);

        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getContent()).isEqualTo("有效");
    }

    @Test
    void clear_deletesKey() {
        memory.clear(CONV);
        verify(redisTemplate).delete(KEY);
    }
}
