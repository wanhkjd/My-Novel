package novel.core.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.constant.CacheConsts;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Redis List 的对话记忆存储（Spring AI ChatMemory 实现）。
 * 每条消息以 {"role","content"} JSON 存储，按窗口裁剪并设置 TTL。
 * 注意：传入的 conversationId 已是含 userId 的业务 key，本类只负责加 Redis 缓存前缀。
 *
 * @author AI
 * @date 2026/09/21
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    /** 记忆窗口：最多保留最近 N 条消息 */
    public static final int MAX_MESSAGES = 20;
    /** 记忆过期时间 */
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = buildKey(conversationId);
        ListOperations<String, String> ops = redisTemplate.opsForList();
        for (Message m : messages) {
            String json = serializeSafe(m);
            if (json != null) {
                ops.rightPush(key, json);
            }
        }
        ops.trim(key, -MAX_MESSAGES, -1);
        redisTemplate.expire(key, TTL);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = buildKey(conversationId);
        List<String> raw = redisTemplate.opsForList().range(key, -lastN, -1);
        List<Message> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String s : raw) {
            Message m = deserializeSafe(s);
            if (m != null) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId) {
        return CacheConsts.REDIS_CACHE_PREFIX + conversationId;
    }

    private String serializeSafe(Message m) {
        try {
            String role = m.getMessageType() == MessageType.USER ? "user" : "assistant";
            return objectMapper.writeValueAsString(new MemoryEntry(role, m.getContent()));
        } catch (Exception e) {
            log.warn("[对话记忆] 序列化消息失败，已跳过: {}", e.getMessage());
            return null;
        }
    }

    private Message deserializeSafe(String json) {
        try {
            MemoryEntry e = objectMapper.readValue(json, MemoryEntry.class);
            return "user".equals(e.role())
                ? new UserMessage(e.content())
                : new AssistantMessage(e.content());
        } catch (Exception ex) {
            log.warn("[对话记忆] 反序列化消息失败，已跳过: {}", ex.getMessage());
            return null;
        }
    }

    /** Redis 中单条消息的存储结构 */
    public record MemoryEntry(String role, String content) {
    }
}
