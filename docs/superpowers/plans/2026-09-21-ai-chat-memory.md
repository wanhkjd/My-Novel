# AI 智能书童对话记忆存储 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为前台"智能书童"AI 助手增加基于 Redis 的多轮对话记忆，使模型能记住上下文，并支持多会话、新对话与历史回显。

**Architecture:** 实现一个 `RedisChatMemory`（Spring AI `ChatMemory` 的 Redis 版），用 Redis List 按会话存 `role+content`；在 `BookCompanionServiceImpl` 用 `MessageChatMemoryAdvisor` 挂载它，每轮请求带上由后端 `UserHolder` 强制拼接的会话 key（保证用户隔离）；Controller 增加清空/历史接口；前端在 localStorage 维护 conversationId 并支持"新对话"与刷新回显。

**Tech Stack:** Java 17 / Spring Boot 3 / Spring AI 1.0.0-M1 / Redis(StringRedisTemplate) / JUnit5 + Mockito + AssertJ；前端 Vue3 + Element Plus + axios。

**Spec:** `docs/superpowers/specs/2026-09-20-ai-chat-memory-design.md`

## Global Constraints

以下为项目级约束，每个 Task 的要求都隐含包含本节：

- Spring AI 版本固定 `1.0.0-M1`，只用已核实存在的 API：
  - `org.springframework.ai.chat.memory.ChatMemory`：`void add(String, List<Message>)` / `List<Message> get(String, int)` / `void clear(String)`
  - `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor(ChatMemory)`（单参构造器）
  - `org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY` / `.CHAT_MEMORY_RETRIEVE_SIZE_KEY`（`String` 常量）
  - `org.springframework.ai.chat.messages.UserMessage(String)` / `AssistantMessage(String)`；`Message.getContent()` / `Message.getMessageType()`；`MessageType.USER` / `MessageType.ASSISTANT`
  - `ChatClient.Builder.defaultAdvisors(RequestResponseAdvisor...)`；`prompt().advisors(a -> a.param(k,v))`
- 复用现有 Redis：注入 `org.springframework.data.redis.core.StringRedisTemplate`；key 前缀用 `novel.core.constant.CacheConsts.REDIS_CACHE_PREFIX`（值 `"Cache::Novel::"`）。
- **用户隔离（安全）**：Redis 业务 key 必须由后端用 `novel.core.auth.UserHolder.getUserId()` 拼接，格式 `aiChatMemory::{userId}::{conversationId}`；前端传入的 conversationId 只作为末段，无法跨用户读取。
- 记忆窗口 `MEMORY_WINDOW = 20` 条；TTL `Duration.ofDays(3)`。
- 后端测试栈：JUnit5 (`org.junit.jupiter`) + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ (`assertThat`)。
- 前端**无单元测试框架**（package.json 仅 `serve`/`build`）：前端任务用 `npm run build` 编译校验 + 明确的手动验证清单代替自动化测试。
- 新增 Java 文件注释风格跟随 AI 模块现有约定（`@author AI` / `@date 2026/09/21`）。
- Git 提交信息结尾附：`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

### Task 1: RedisChatMemory —— Redis 版对话记忆存储

**Files:**
- Create: `src/main/java/novel/core/ai/memory/RedisChatMemory.java`
- Test: `src/test/java/novel/core/ai/memory/RedisChatMemoryTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`（构造注入）；`CacheConsts.REDIS_CACHE_PREFIX`
- Produces: `@Component` 类 `RedisChatMemory implements ChatMemory`，实现 `add(String,List<Message>)` / `get(String,int)` / `clear(String)`。Redis key = `REDIS_CACHE_PREFIX + conversationId`（传入的 conversationId 已是含 userId 的业务 key，本类只加缓存前缀，不再拼业务段）。

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/novel/core/ai/memory/RedisChatMemoryTest.java`：

```java
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=RedisChatMemoryTest test`
Expected: 编译失败 / `RedisChatMemory` 不存在。

- [ ] **Step 3: 写最小实现**

创建 `src/main/java/novel/core/ai/memory/RedisChatMemory.java`：

```java
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=RedisChatMemoryTest test`
Expected: PASS（5 个测试全绿）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/novel/core/ai/memory/RedisChatMemory.java src/test/java/novel/core/ai/memory/RedisChatMemoryTest.java
git commit -m "feat(ai): 新增 Redis 版对话记忆存储 RedisChatMemory

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 后端接线 —— DTO / Service 接口 / 实现 / Controller

**Files:**
- Create: `src/main/java/novel/core/ai/dto/ChatHistoryMsgDto.java`
- Modify: `src/main/java/novel/service/BookCompanionService.java`
- Modify: `src/main/java/novel/service/impl/BookCompanionServiceImpl.java`
- Modify: `src/main/java/novel/controller/front/AiBookCompanionController.java`
- Test: `src/test/java/novel/service/impl/BookCompanionServiceImplMemoryTest.java`

**Interfaces:**
- Consumes: Task 1 的 `RedisChatMemory`（作为 `ChatMemory` bean 注入）；`UserHolder.getUserId()`；`RestResp.ok()` / `RestResp.ok(T)`
- Produces:
  - `ChatHistoryMsgDto`（Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`，字段 `String role` / `String content`）
  - Service 接口新签名：`Flux<String> chatStream(String message, String conversationId)`、`RestResp<String> chat(String message, String conversationId)`、`RestResp<Void> clearMemory(String conversationId)`、`RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId)`
  - `BookCompanionServiceImpl.buildMemoryKey(String conversationId)`（包级可见，返回 `aiChatMemory::{userId}::{conversationId或default}`），常量 `MEMORY_WINDOW = 20`

- [ ] **Step 1: 建 DTO**

创建 `src/main/java/novel/core/ai/dto/ChatHistoryMsgDto.java`：

```java
package novel.core.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对话历史消息 DTO（供前端刷新回显）
 *
 * @author AI
 * @date 2026/09/21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryMsgDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;
}
```

- [ ] **Step 2: 写失败测试**

创建 `src/test/java/novel/service/impl/BookCompanionServiceImplMemoryTest.java`。用 `@InjectMocks` 构造 impl（不触发 `@PostConstruct`，`chatClient` 保持 null；`clearMemory`/`getHistory` 不依赖 chatClient）。`UserHolder` 是 `ThreadLocal` 工具类，测试内直接 `setUserId` / 用 `@AfterEach clear`：

```java
package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import novel.core.ai.config.AiProperties;
import novel.core.ai.dto.ChatHistoryMsgDto;
import novel.core.ai.tools.BookCompanionTools;
import novel.core.ai.vector.BookVectorSyncService;
import novel.core.ai.vector.EsVectorStoreService;
import novel.core.auth.UserHolder;
import novel.core.common.resp.RestResp;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class BookCompanionServiceImplMemoryTest {

    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private AiProperties aiProperties;
    @Mock private BookCompanionTools bookCompanionTools;
    @Mock private EsVectorStoreService esVectorStoreService;
    @Mock private BookVectorSyncService bookVectorSyncService;
    @Mock private UserBookshelfMapper userBookshelfMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private ChatMemory chatMemory;

    @InjectMocks private BookCompanionServiceImpl service;

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void buildMemoryKey_namespacesByUserId() {
        UserHolder.setUserId(1001L);
        assertThat(service.buildMemoryKey("conv1")).isEqualTo("aiChatMemory::1001::conv1");
    }

    @Test
    void buildMemoryKey_fallsBackToDefault_whenBlank() {
        UserHolder.setUserId(1001L);
        assertThat(service.buildMemoryKey("  ")).isEqualTo("aiChatMemory::1001::default");
        assertThat(service.buildMemoryKey(null)).isEqualTo("aiChatMemory::1001::default");
    }

    @Test
    void clearMemory_clearsUserNamespacedKey() {
        UserHolder.setUserId(1001L);
        RestResp<Void> resp = service.clearMemory("conv1");
        assertThat(resp.getCode()).isEqualTo("00000");
        verify(chatMemory).clear("aiChatMemory::1001::conv1");
    }

    @Test
    void clearMemory_doesNothing_whenNotLoggedIn() {
        service.clearMemory("conv1");
        verify(chatMemory, never()).clear(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getHistory_mapsMessagesToDto() {
        UserHolder.setUserId(1001L);
        List<Message> stored = List.of(new UserMessage("你好"), new AssistantMessage("少侠好"));
        when(chatMemory.get("aiChatMemory::1001::conv1", 20)).thenReturn(stored);

        RestResp<List<ChatHistoryMsgDto>> resp = service.getHistory("conv1");

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData().get(0).getRole()).isEqualTo("user");
        assertThat(resp.getData().get(0).getContent()).isEqualTo("你好");
        assertThat(resp.getData().get(1).getRole()).isEqualTo("assistant");
        assertThat(resp.getData().get(1).getContent()).isEqualTo("少侠好");
    }

    @Test
    void getHistory_returnsEmpty_whenNotLoggedIn() {
        RestResp<List<ChatHistoryMsgDto>> resp = service.getHistory("conv1");
        assertThat(resp.getData()).isEmpty();
    }
}
```

> 注：`RestResp.getCode()` 成功值为 `"00000"`（见 `ErrorCodeEnum.OK`）。若字段名不同，以实际 `RestResp` 为准调整断言，不改变行为语义。

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -Dtest=BookCompanionServiceImplMemoryTest test`
Expected: 编译失败（新方法/DTO 尚不存在）。

- [ ] **Step 4: 改 Service 接口**

修改 `src/main/java/novel/service/BookCompanionService.java`：给 `chatStream` / `chat` 加 `conversationId` 参数，新增 `clearMemory` / `getHistory`，并 `import novel.core.ai.dto.ChatHistoryMsgDto;`：

```java
    /** 智能书童对话 (打字机流式输出 SSE) */
    Flux<String> chatStream(String message, String conversationId);

    /** 智能书童对话 (常规阻塞式交互) */
    RestResp<String> chat(String message, String conversationId);

    /** 清空指定会话的对话记忆（用于"新对话"） */
    RestResp<Void> clearMemory(String conversationId);

    /** 查询指定会话的历史消息（用于刷新回显） */
    RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId);
```

（保留原有 `recommendSimilarBooks` / `syncAllBookVectors` 不变。）

- [ ] **Step 5: 改 Service 实现**

修改 `src/main/java/novel/service/impl/BookCompanionServiceImpl.java`：

1. 新增导入：
```java
import novel.core.ai.dto.ChatHistoryMsgDto;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
```

2. 新增字段 + 常量（放在现有 `private final ... bookInfoMapper;` 之后）：
```java
    private final ChatMemory chatMemory;

    /** 记忆窗口条数 */
    static final int MEMORY_WINDOW = 20;
    /** 记忆业务 key 前缀 */
    private static final String MEMORY_KEY_PREFIX = "aiChatMemory::";
```

3. `init()` 挂 advisor（在 `.defaultFunctions(...)` 之后、`.build()` 之前加一行）：
```java
        this.chatClient = chatClientBuilder
                .defaultSystem(aiProperties.getSystemPrompt())
                .defaultFunctions("searchBooksFunction", "getBookRankFunction", "addToBookshelfFunction", "recommendBooksFunction")
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
```

4. `chatStream` 方法签名改为 `chatStream(String message, String conversationId)`，其中调用大模型的那句改为带 advisors：
```java
                String fullResponse = chatClient.prompt()
                        .user(message)
                        .advisors(a -> a
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, buildMemoryKey(conversationId))
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW))
                        .call()
                        .content();
```
（其余流式分块与异常兜底逻辑保持不变。）

5. `chat` 方法签名改为 `chat(String message, String conversationId)`，调用改为：
```java
        String response = chatClient.prompt()
                .user(message)
                .advisors(a -> a
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, buildMemoryKey(conversationId))
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW))
                .call()
                .content();
        return RestResp.ok(response);
```

6. 新增三个方法（`buildMemoryKey` 包级可见以便测试）：
```java
    @Override
    public RestResp<Void> clearMemory(String conversationId) {
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            chatMemory.clear(buildMemoryKey(conversationId));
        }
        return RestResp.ok();
    }

    @Override
    public RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return RestResp.ok(Collections.emptyList());
        }
        List<Message> messages = chatMemory.get(buildMemoryKey(conversationId), MEMORY_WINDOW);
        List<ChatHistoryMsgDto> dtos = messages.stream()
                .map(m -> ChatHistoryMsgDto.builder()
                        .role(m.getMessageType() == MessageType.USER ? "user" : "assistant")
                        .content(m.getContent())
                        .build())
                .toList();
        return RestResp.ok(dtos);
    }

    /**
     * 由登录用户 ID 与会话 ID 拼接记忆业务 key，userId 由后端强制注入以隔离用户。
     */
    String buildMemoryKey(String conversationId) {
        Long userId = UserHolder.getUserId();
        String convId = (conversationId == null || conversationId.isBlank()) ? "default" : conversationId;
        return MEMORY_KEY_PREFIX + userId + "::" + convId;
    }
```
（`Collections` 已在现有 import 中；`List` 亦已导入。）

- [ ] **Step 6: 改 Controller**

修改 `src/main/java/novel/controller/front/AiBookCompanionController.java`：

1. 新增导入：
```java
import novel.core.ai.dto.ChatHistoryMsgDto;
```

2. `chatStream` / `chat` 加可选 `conversationId` 参数并透传：
```java
    @Operation(summary = "智能书童流式对话接口 (SSE)")
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(
            @Parameter(description = "读者发送的自然语言消息") @RequestParam("message") String message,
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.chatStream(message, conversationId);
    }

    @Operation(summary = "智能书童常规对话接口")
    @GetMapping("/chat")
    public RestResp<String> chat(
            @Parameter(description = "读者发送的自然语言消息") @RequestParam("message") String message,
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.chat(message, conversationId);
    }
```

3. 新增两个接口：
```java
    @Operation(summary = "清空指定会话的对话记忆")
    @DeleteMapping("/chat/memory")
    public RestResp<Void> clearMemory(
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.clearMemory(conversationId);
    }

    @Operation(summary = "查询指定会话的历史消息")
    @GetMapping("/chat/history")
    public RestResp<List<ChatHistoryMsgDto>> getHistory(
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.getHistory(conversationId);
    }
```

> 无需改 `WebConfig`：`/chat/memory`、`/chat/history` 均在 `/api/front/ai/**` 下，已被 `AuthInterceptor` 拦截（要求登录）并被 `TokenParseInterceptor` 解析出 `UserHolder`，符合用户隔离前提。

- [ ] **Step 7: 运行测试确认通过 + 全量编译**

Run: `mvn -q -Dtest=BookCompanionServiceImplMemoryTest test`
Expected: PASS（6 个测试全绿）。
Run: `mvn -q -DskipTests compile`
Expected: 编译成功（确认 Controller/接口/实现签名一致）。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/novel/core/ai/dto/ChatHistoryMsgDto.java src/main/java/novel/service/BookCompanionService.java src/main/java/novel/service/impl/BookCompanionServiceImpl.java src/main/java/novel/controller/front/AiBookCompanionController.java src/test/java/novel/service/impl/BookCompanionServiceImplMemoryTest.java
git commit -m "feat(ai): 智能书童接入对话记忆(会话隔离/清空/历史回显)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 前端 API 层 —— conversationId 管理与新接口

**Files:**
- Modify: `front/novel-front-web/src/api/ai.js`

**Interfaces:**
- Consumes: Task 2 的后端接口 `GET /front/ai/companion/chat`（加 `conversationId`）、`GET /chat/stream`（加 `conversationId`）、`DELETE /chat/memory`、`GET /chat/history`
- Produces: 导出 `getConversationId()` / `newConversation()` / `clearMemory(conversationId)` / `getChatHistory(conversationId)`；`chatWithCompanion(message, conversationId)`；`streamChatWithCompanion(message, conversationId, onChunk, onDone, onError)`（**新增第 2 位参数 conversationId**，调用方需同步更新——见 Task 4）

> 前端无单测框架，本任务用 `npm run build` 编译校验 + 手动验证。

- [ ] **Step 1: 改写 `src/api/ai.js`**

在文件顶部现有 import 后新增会话 ID 工具与新接口，并修改两个对话函数：

```js
const CONV_ID_KEY = 'ai_companion_conversation_id'

function genConversationId() {
  if (typeof window !== 'undefined' && window.crypto && window.crypto.randomUUID) {
    return window.crypto.randomUUID()
  }
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

/** 获取当前会话ID，无则生成并持久化 */
export function getConversationId() {
  let id = localStorage.getItem(CONV_ID_KEY)
  if (!id) {
    id = genConversationId()
    localStorage.setItem(CONV_ID_KEY, id)
  }
  return id
}

/** 开启新会话：生成并持久化新的会话ID，返回之 */
export function newConversation() {
  const id = genConversationId()
  localStorage.setItem(CONV_ID_KEY, id)
  return id
}

/** 清空指定会话的服务端对话记忆 */
export function clearMemory(conversationId) {
  return request.delete('/front/ai/companion/chat/memory', {
    params: { conversationId }
  })
}

/** 查询指定会话的历史消息 */
export function getChatHistory(conversationId) {
  return request.get('/front/ai/companion/chat/history', {
    params: { conversationId }
  })
}
```

修改 `chatWithCompanion` 带上 conversationId：
```js
export function chatWithCompanion(message, conversationId) {
  return request.get('/front/ai/companion/chat', {
    params: { message, conversationId }
  })
}
```

修改 `streamChatWithCompanion` 签名（新增第 2 位 `conversationId`）并拼进 URL：
```js
export async function streamChatWithCompanion(message, conversationId, onChunk, onDone, onError) {
  const token = getToken()
  const baseUrl = process.env.VUE_APP_BASE_API_URL || '/api'
  const url = `${baseUrl}/front/ai/companion/chat/stream`
    + `?message=${encodeURIComponent(message)}`
    + `&conversationId=${encodeURIComponent(conversationId || '')}`
  // ...（以下 fetch/reader/SSE 解析逻辑保持不变）
```

> `request`（axios 实例）响应拦截器返回整个 `RestResp`（`{code,message,data,ok}`），业务数据在 `.data` 字段——Task 4 消费 history 时取 `resp.data`。

- [ ] **Step 2: 编译校验**

Run: `cd front/novel-front-web && npm run build`
Expected: 构建成功，无语法/引用错误。（此步会因 Task 4 尚未更新调用方而**仍能通过**，因为 ai.js 自身无编译期强约束；真正的调用方联动在 Task 4 验证。）

- [ ] **Step 3: 提交**

```bash
git add front/novel-front-web/src/api/ai.js
git commit -m "feat(ai-web): api 层增加会话ID管理与记忆清空/历史接口

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 前端组件 —— 会话贯通、新对话按钮、刷新回显

**Files:**
- Modify: `front/novel-front-web/src/components/common/AiCompanion.vue`

**Interfaces:**
- Consumes: Task 3 的 `getConversationId` / `newConversation` / `clearMemory` / `getChatHistory` / `streamChatWithCompanion(message, conversationId, ...)`

> 前端无单测框架，本任务用 `npm run build` 编译校验 + 明确手动验证清单。

- [ ] **Step 1: 更新 import 与 state**

修改 `<script>` 中 import：
```js
import { streamChatWithCompanion, addToBookshelf,
         getConversationId, newConversation, clearMemory, getChatHistory } from '@/api/ai'
```

`reactive` state 中新增字段（与 `nickName` 同级）：
```js
      conversationId: getConversationId(),
```

- [ ] **Step 2: 发送时带上 conversationId**

`sendMessage` 中调用改为（新增第 2 位参数）：
```js
        await streamChatWithCompanion(
          text,
          state.conversationId,
          (chunk) => { assistantMsg.content += chunk; scrollToBottom() },
          () => { state.loading = false; scrollToBottom() },
          (error) => {
            state.loading = false
            assistantMsg.content += '\n\n*(哎呀，小墨似乎遇到了点小问题，请稍后再试)*'
            ElMessage.error(error.message || '请求失败')
            scrollToBottom()
          }
        )
```

- [ ] **Step 3: "清空对话"改为真正的新对话（清服务端记忆 + 换会话ID + 重置本地）**

`clearMessages` 改为 async：
```js
    const clearMessages = async () => {
      if (state.loading) return
      try {
        await clearMemory(state.conversationId)
      } catch (e) {
        // 忽略清空失败，仍继续开启新会话
      }
      state.conversationId = newConversation()
      state.messages = [
        {
          role: 'assistant',
          content: '对话已清空，已开启新的一段对话。少侠，今天想了解什么题材的小说？随时问我吧！'
        }
      ]
    }
```

- [ ] **Step 4: mounted 时回显历史**

在现有 `onMounted` 中，注册事件监听之后追加历史加载（登录态下）：
```js
    const loadHistory = async () => {
      const token = getToken()
      if (!token) return
      try {
        const resp = await getChatHistory(state.conversationId)
        const history = resp && resp.data ? resp.data : []
        if (history.length > 0) {
          state.messages = history.map(m => ({ role: m.role, content: m.content }))
          scrollToBottom()
        }
      } catch (e) {
        // 回显失败不影响使用，保留欢迎语
      }
    }

    onMounted(() => {
      window.addEventListener('open-ai-companion', openChat)
      loadHistory()
    })
```
（删除原来单独的 `onMounted(...)`，合并为上面这一个。）

- [ ] **Step 5: 编译校验**

Run: `cd front/novel-front-web && npm run build`
Expected: 构建成功，无未定义引用（确认 `streamChatWithCompanion` 新参数顺序、新导入函数均正确）。

- [ ] **Step 6: 手动验证清单**

后端已启动、ES/Redis 可用、以登录用户访问前台：
1. 打开 AI 书童，发"我想找修仙爽文" → 再发"刚才那类再多推荐两本"，第二轮回复能延续上一轮语境（记忆生效）。
2. 刷新页面重开书童 → 历史对话被回显（getHistory 生效）。
3. 点"清空对话" → 列表回到欢迎语；再问"刚才聊了什么" → 模型不再记得（新会话ID + 服务端已清）。
4. 用另一账号登录 → 看不到上一个账号的历史（用户隔离生效）。
5. Redis 中可见 `Cache::Novel::aiChatMemory::{userId}::{conversationId}` 键，且 TTL 约 3 天。

- [ ] **Step 7: 提交**

```bash
git add front/novel-front-web/src/components/common/AiCompanion.vue
git commit -m "feat(ai-web): 书童贯通会话ID,支持新对话与刷新历史回显

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage（对照 spec 各节）：**
- §3 RedisChatMemory → Task 1 ✅
- §4.2 Bean 装配 / advisor → Task 2 Step 5.3 ✅
- §4.3 用户隔离（userId 拼 key）→ Task 2 `buildMemoryKey` + 测试 ✅
- §4.4 每轮调用带会话 id → Task 2 Step 5.4/5.5 ✅
- §5 API 变更（chat/stream 加参、DELETE、GET history）→ Task 2 Step 6 ✅
- §6 前端 ai.js → Task 3 ✅
- §7 前端组件（新对话按钮、回显）→ Task 4 ✅
- §8 测试策略 → Task 1（5 测）+ Task 2（6 测）覆盖 add/get/clear/窗口/TTL/隔离/映射 ✅
- §9 M1 API 核对 → 已用 javap 全部确认，Global Constraints 固化 ✅

**2. Placeholder scan：** 无 TBD/TODO/"类似上文"；所有步骤含真实代码。✅

**3. Type consistency：**
- `buildMemoryKey` 全程一致（Task 2 定义与测试、调用一致）✅
- `streamChatWithCompanion(message, conversationId, onChunk, onDone, onError)` 在 Task 3 定义、Task 4 调用参数顺序一致 ✅
- `ChatHistoryMsgDto{role,content}` 定义（Task 2）与前端消费 `m.role/m.content`（Task 4）一致 ✅
- `MEMORY_WINDOW = 20` 与 RedisChatMemory `MAX_MESSAGES = 20`、测试中的 `-20,-1` / `get(...,20)` 一致 ✅

**说明性备注（非阻塞）：** `RestResp` 的成功码断言按实际字段名核对；若 `getCode()`/`"00000"` 与项目定义不符，按实际调整断言，不改行为。
