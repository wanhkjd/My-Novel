package novel.core.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.constant.CacheConsts;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户会话记忆：读取历史时取得代次，回答完成后仅向仍有效的代次原子提交整轮消息。
 * 清空或过期都会废弃旧快照；代次使用随机值，不会因 key 被删除而重新从零计数。
 * 一个 Redis key 同时保存代次和历史，使读取、提交、清空在多实例间也有明确先后顺序。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemory {

    public static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofDays(3);
    private static final DefaultRedisScript<List> BEGIN = listScript("lua/ai-memory-begin.lua");
    private static final DefaultRedisScript<List> READ = listScript("lua/ai-memory-read.lua");
    private static final DefaultRedisScript<Long> APPEND = new DefaultRedisScript<>();
    static {
        APPEND.setLocation(new ClassPathResource("lua/ai-memory-append.lua"));
        APPEND.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 原子读取同一代次及其历史。必须在调用模型之前取得，不能在回答完成后补取。 */
    public Snapshot begin(String conversationId) {
        List<?> result = redisTemplate.execute(BEGIN, List.of(buildKey(conversationId)),
                UUID.randomUUID().toString(), Long.toString(TTL.toSeconds()), Integer.toString(MAX_MESSAGES));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Unable to read conversation snapshot");
        }
        try {
            List<String> raw = objectMapper.readValue((String) result.get(1), new TypeReference<>() {});
            return new Snapshot(conversationId, (String) result.get(0), deserialize(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid conversation snapshot", e);
        }
    }

    /**
     * 原子提交问题和完整回答、裁剪窗口并续期。false 表示会话已清空或过期，旧结果被丢弃。
     * 不提供不带快照的 add 方法，防止调用方绕过代次检查；序列化失败时整轮不写入。
     */
    public boolean appendTurn(Snapshot snapshot, String question, String answer) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
        try {
            String user = objectMapper.writeValueAsString(new MemoryEntry("user", question));
            String assistant = objectMapper.writeValueAsString(new MemoryEntry("assistant", answer));
            Long result = redisTemplate.execute(APPEND, List.of(buildKey(snapshot.conversationId())),
                    snapshot.generation(), user, assistant, Integer.toString(MAX_MESSAGES),
                    Long.toString(TTL.toSeconds()));
            if (result == null) {
                throw new IllegalStateException("Unable to commit conversation turn");
            }
            return result == 1L;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize conversation turn", e);
        }
    }

    /** 读取历史不会创建或续期空会话，也兼容升级前的 Redis List。 */
    public List<Message> get(String conversationId, int lastN) {
        if (lastN <= 0) {
            return List.of();
        }
        List<?> raw = redisTemplate.execute(READ, List.of(buildKey(conversationId)),
                Integer.toString(Math.min(lastN, MAX_MESSAGES)));
        if (raw == null) {
            throw new IllegalStateException("Unable to read conversation history");
        }
        return deserialize(raw);
    }

    /** 删除的是整个代次。后续 begin 会生成全新代次，旧 append 无法重建被删除的 key。 */
    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId) {
        return CacheConsts.REDIS_CACHE_PREFIX + Objects.requireNonNull(conversationId, "conversationId");
    }

    private static DefaultRedisScript<List> listScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(List.class);
        return script;
    }

    private List<Message> deserialize(List<?> raw) {
        List<Message> result = new ArrayList<>();
        for (Object value : raw) {
            try {
                MemoryEntry entry = objectMapper.readValue((String) value, MemoryEntry.class);
                if ("user".equals(entry.role())) {
                    result.add(new UserMessage(entry.content()));
                } else if ("assistant".equals(entry.role())) {
                    result.add(new AssistantMessage(entry.content()));
                }
            } catch (Exception ex) {
                log.warn("[对话记忆] 跳过格式损坏的历史消息");
            }
        }
        return result;
    }

    public record Snapshot(String conversationId, String generation, List<Message> messages) {
        public Snapshot {
            Objects.requireNonNull(conversationId, "conversationId");
            Objects.requireNonNull(generation, "generation");
            messages = List.copyOf(messages);
        }
    }

    public record MemoryEntry(String role, String content) {}
}
