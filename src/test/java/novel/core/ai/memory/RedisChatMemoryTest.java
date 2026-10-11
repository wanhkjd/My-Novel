package novel.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryTest {

    private static final String CONVERSATION = "aiChatMemory::1001::conv1";
    private static final String KEY = "Cache::Novel::" + CONVERSATION;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisChatMemory memory;

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void getWithNonPositiveLimitReturnsEmptyWithoutAccessingRedis(int lastN) {
        assertThat(memory.get(CONVERSATION, lastN)).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void clearDeletesOnlyTheConversationKey() {
        memory.clear(CONVERSATION);

        verify(redisTemplate).delete(KEY);
        verifyNoMoreInteractions(redisTemplate);
    }
}
