# AI 智能书童 - 对话记忆存储 设计文档

- 日期：2026-09-20
- 范围：为前台"智能书童"AI 助手增加多轮对话记忆能力
- 涉及模块：后端 `core/ai`、`service`、`controller/front`；前端 `novel-front-web` 的 `api/ai.js`、`AiCompanion.vue`

## 1. 背景与现状

当前 `BookCompanionServiceImpl.chat` / `chatStream` 每轮均为无状态调用：

```java
chatClient.prompt().user(message).call().content();
```

不携带任何会话标识，也不注入历史，模型每一轮都"失忆"，无法进行多轮上下文对话（例如"刚才那本书的作者还写过什么"）。

技术前提：
- 项目使用 Spring AI `1.0.0-M1`，自带 `org.springframework.ai.chat.memory.ChatMemory` 抽象与记忆 Advisor，无需手写记忆注入逻辑。
- 项目已集成 Redis（`StringRedisTemplate`、缓存前缀 `Cache::Novel::`）。
- AI 全部接口已被 `AuthInterceptor` 强制登录拦截（`/api/front/ai/**`，仅排除 `/companion/sync-vectors`），`UserHolder.getUserId()` 在会话内可靠可用。
- `chatStream` 实为**阻塞式 `.call()` 后按 Unicode 码点手动分块**，并非真正的反应式 `.stream()`，因此记忆逻辑在 `chat` 与 `chatStream` 之间完全一致。

## 2. 目标

1. 按"会话"粒度持久化多轮对话到 Redis，模型可记住上下文。
2. 支持多会话与"新对话/清空当前会话"。
3. 刷新页面后可回显历史对话（可选能力，本设计纳入）。
4. 严格的用户隔离：任何一方无法通过伪造 conversationId 读取他人对话。

### 非目标（YAGNI）

- 不做跨会话的"会话列表管理 / 重命名 / 归档"面板，前端仅维护"当前活跃会话"。
- 不持久化 tool-call（函数调用）的中间元数据，仅存 `role + content` 文本。
- 不引入向量化长期记忆 / 摘要压缩记忆，仅做定长滑动窗口。

## 3. 整体架构

```
前端 AiCompanion.vue
  ├─ localStorage 保存 conversationId（首次生成 UUID）
  ├─ 发消息：chat/stream?message=..&conversationId=..
  ├─ "新对话"：换新 UUID + DELETE 清空 + 清本地消息
  └─ mount：GET history 回显
        │
        ▼
AiBookCompanionController
  ├─ GET  /companion/chat/stream   (+conversationId)
  ├─ GET  /companion/chat          (+conversationId)
  ├─ DELETE /companion/chat/memory (conversationId)
  └─ GET  /companion/chat/history  (conversationId)
        │
        ▼
BookCompanionServiceImpl
  ├─ 由 UserHolder.getUserId() + conversationId 派生 memoryKey
  ├─ chatClient.prompt().advisors(param 会话id, param 窗口).call()
  ├─ clearMemory(conversationId)
  └─ getHistory(conversationId)
        │
        ▼
MessageChatMemoryAdvisor（Spring AI 框架）
        │ add/get
        ▼
RedisChatMemory implements ChatMemory
        │
        ▼
Redis List: Cache::Novel::aiChatMemory::{userId}::{conversationId}
```

## 4. 组件设计

### 4.1 RedisChatMemory（新增 `core/ai/memory/RedisChatMemory.java`）

实现 Spring AI `ChatMemory` 接口，复用 `StringRedisTemplate`。

- **Key 规则与职责划分**（避免歧义，明确到位）：
  - Service 层负责拼**业务 key**：`aiChatMemory::{userId}::{clientConversationId}`（userId 由后端强制注入，见 4.3）。这个业务 key 作为框架的 conversationId 参数向下传递。
  - `RedisChatMemory` 层负责在业务 key 前再拼 **Redis 缓存前缀** `Cache::Novel::`，得到最终 Redis key：`Cache::Novel::aiChatMemory::{userId}::{clientConversationId}`。
  - 即：`RedisChatMemory` 只认它收到的 conversationId 字符串，不关心 userId 从哪来；隔离性由上层保证。
- **数据结构**：Redis List，每个元素是一条消息的 JSON：`{"role":"user"|"assistant","content":"..."}`
- **`add(conversationId, messages)`**：将消息序列化后 `RPUSH`；随后 `LTRIM(key, -MAX_MESSAGES, -1)` 保留最近 N 条；`expire(key, TTL)` 刷新过期时间。
- **`get(conversationId, lastN)`**：`LRANGE(key, -lastN, -1)`，反序列化为 `List<Message>`（`user` → `UserMessage`，`assistant` → `AssistantMessage`）。
- **`clear(conversationId)`**：`delete(key)`。
- **常量**：`MAX_MESSAGES = 20`（约 10 轮），`TTL = Duration.ofDays(3)`。
- **健壮性**：单条反序列化失败时跳过该条并记 warn，不影响整体对话。

### 4.2 Bean 装配

`RedisChatMemory` 标注 `@Component`（构造注入 `StringRedisTemplate`）。`BookCompanionServiceImpl` 注入该 `ChatMemory`，在 `@PostConstruct` 构建 ChatClient 时挂上 advisor：

```java
this.chatClient = chatClientBuilder
    .defaultSystem(aiProperties.getSystemPrompt())
    .defaultFunctions("searchBooksFunction","getBookRankFunction",
                      "addToBookshelfFunction","recommendBooksFunction")
    .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
    .build();
```

选择 `MessageChatMemoryAdvisor`（把历史作为独立 message 列表注入）而非 `PromptChatMemoryAdvisor`（把历史拼进 system prompt 文本）——前者与"函数调用 + 人设 system prompt"共存更自然。

> 记忆与函数调用的关系：advisor 记录的是本轮"用户输入"与"最终助手回复"，中间的 tool 调用发生在一次 `.call()` 内部，不进入记忆，符合预期。

### 4.3 会话隔离与安全（关键约束）

- conversationId 由前端生成并传入，但**后端在派生 memoryKey 时强制用 `UserHolder.getUserId()` 作为命名空间前缀**。
- 即：`memoryKey = "aiChatMemory::" + userId + "::" + clientConversationId`，再交给 advisor / RedisChatMemory。
- 因此即使前端传入他人的 conversationId，落点仍在本人 userId 空间内，无法读到他人历史。
- 若 `getUserId()` 为空（理论上被拦截器挡住不会发生），做防御：回退到不带记忆的原逻辑，避免 NPE。

### 4.4 每轮调用

```java
String key = buildMemoryKey(conversationId);      // 含 userId
chatClient.prompt().user(message)
    .advisors(a -> a
        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, key)
        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))
    .call().content();
```

## 5. API 变更

| 方法 | 路径 | 变更 | 说明 |
|------|------|------|------|
| GET | `/companion/chat/stream` | 加 `conversationId`（可选） | 空则回退 `"default"` |
| GET | `/companion/chat` | 加 `conversationId`（可选） | 同上 |
| DELETE | `/companion/chat/memory` | 新增 | 清空指定会话，供"新对话" |
| GET | `/companion/chat/history` | 新增 | 返回该会话历史消息（含 role、content），供刷新回显 |

Service 接口 `BookCompanionService` 相应调整：
- `chatStream(String message, String conversationId)`
- `chat(String message, String conversationId)`
- `RestResp<Void> clearMemory(String conversationId)`（新增）
- `RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId)`（新增）

新增 DTO `core/ai/dto/ChatHistoryMsgDto`：`{ role, content }`。

## 6. 前端变更（novel-front-web）

### 6.1 `src/api/ai.js`
- 维护 conversationId：`getConversationId()` 从 localStorage 读，无则生成 UUID 并存。
- `chatStream` / `chat` 调用附带 `conversationId`。
- 新增 `clearMemory(conversationId)`（DELETE）、`getHistory(conversationId)`（GET）。
- 新增 `newConversation()`：生成并存新 UUID，返回之。

### 6.2 `src/components/common/AiCompanion.vue`
- 顶部加"新对话"按钮：调用 `newConversation()` + `clearMemory(旧id)` + 清空本地消息列表。
- 发送消息时带上当前 conversationId。
- 组件 mounted 时调用 `getHistory()` 回显既有对话（登录态下）。

## 7. 错误处理

- Redis 读写异常：记 error 日志，`get` 返回空列表（等价"无记忆"，不阻断对话），`add` 失败不影响本轮回复返回。
- 反序列化异常：跳过坏数据。
- 大模型调用异常：沿用现有 `chatStream` 的兜底文案逻辑。

## 8. 测试策略

对 `RedisChatMemory` 编写单元测试（可用嵌入式 Redis 或 mock `StringRedisTemplate`）：
1. `add` 后 `get` 能取回同样内容、顺序正确。
2. 超过 `MAX_MESSAGES` 时窗口裁剪，只保留最近 N 条。
3. `clear` 后 `get` 为空。
4. 写入后 key 设置了 TTL（约 3 天）。
5. 不同 userId 派生的 key 互不干扰（隔离性）。

多轮语义正确性依赖真实大模型，不纳入自动化单测；实现后手动验证"上一轮提到的书，本轮能被引用"。

## 9. 待实现时核对的事项（已知不确定项）

Spring AI `1.0.0-M1` 为里程碑版本，以下符号在实现时需对着实际 jar 确认，不硬编码：
- `MessageChatMemoryAdvisor` 的包路径与构造器签名。
- 会话 id 与检索条数的参数常量名（预期为 `AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY` / `CHAT_MEMORY_RETRIEVE_SIZE_KEY`，需核实）。
- `ChatMemory` 接口方法签名是否与本文档一致（`add/get/clear`）。

若 M1 的 advisor 与文档假设不符，退路：不挂 advisor，改为在 service 内手动 `chatMemory.get(...)` 拼进 `prompt().messages(...)`、调用后手动 `chatMemory.add(...)`，接口与存储层设计不变。

## 10. 安全备注（关联发现，非本次范围）

`application-dev.yml` 存在明文 DashScope API Key 默认值，建议后续轮换并从工作区移除。与本设计无耦合，单独处理。
