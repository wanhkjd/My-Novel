package novel.core.ai.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/** Deterministic storage seam for orchestration tests; Lua behavior is tested against isolated Redis. */
public final class InMemoryConversationMemory extends RedisChatMemory {
    private final Map<String, Snapshot> conversations = new HashMap<>();

    public InMemoryConversationMemory() { super(mock(StringRedisTemplate.class)); }

    @Override
    public synchronized Snapshot begin(String id) {
        return conversations.computeIfAbsent(id, key -> new Snapshot(key, UUID.randomUUID().toString(), List.of()));
    }

    @Override
    public synchronized boolean appendTurn(Snapshot snapshot, String question, String answer) {
        Snapshot current = conversations.get(snapshot.conversationId());
        if (current == null || !current.generation().equals(snapshot.generation())) return false;
        List<Message> messages = new ArrayList<>(current.messages());
        messages.add(new UserMessage(question));
        messages.add(new AssistantMessage(answer));
        if (messages.size() > MAX_MESSAGES) messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
        conversations.put(snapshot.conversationId(), new Snapshot(snapshot.conversationId(), snapshot.generation(), messages));
        return true;
    }

    @Override
    public synchronized List<Message> get(String id, int lastN) {
        Snapshot current = conversations.get(id);
        if (current == null || lastN <= 0) return List.of();
        return current.messages().subList(Math.max(0, current.messages().size() - lastN), current.messages().size());
    }

    @Override
    public synchronized void clear(String id) { conversations.remove(id); }
}
