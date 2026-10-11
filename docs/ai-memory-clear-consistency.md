# AI 会话清空与回答提交一致性

## 问题与范围

旧实现清空只删除 Redis key，而模型调用完成后仍可无条件追加历史。因而“旧请求开始 → 另一标签页清空成功 → 旧请求完成”会让已清空的历史重新出现。每用户并发配额不能解决这一点，因为清空操作不占用生成配额。

本轮只修复会话记忆的读取/提交/清空顺序，未调整前端协议、提示词、工具权限、流式配额、模型配置或推荐检索。

## 设计

沿用原 key：`Cache::Novel::aiChatMemory::{userId}::default`。客户端 conversationId 继续不参与用户命名空间。

- `RedisChatMemory.begin(id)` 原子取得 `Snapshot(id, generation, messages)`；在调用模型**之前**执行。
- `appendTurn(snapshot, question, answer)` 用 Lua 在同一个 key 上比较 generation，仅当仍匹配时同时写入问题和完整回答、保留最近 20 条、续期三天。
- `clear(id)` 删除整个 key。下一次 begin 使用全新 UUID，不从零重置计数，因此旧快照不能在清空或 TTL 过期后再次有效。
- `get(id, lastN)` 只读，不创建/续期空会话；非正条数直接返回空。
- 不再实现不支持条件提交的 Spring AI `ChatMemory.add`；去掉自动写入的 `MessageChatMemoryAdvisor`，由调用方显式携带快照。

Redis Hash 在一个 key 内存储 `generation` 和 `messages`。messages 是序列化消息字符串的 JSON 数组，容纳现有格式。三个 Lua 脚本都只访问一个 key，没有跨槽事务或 JVM 本地锁依赖。多字段初始化使用 HMSET，兼容本机 Redis 3.2。

### 顺序保证

| 先后顺序 | 结果 |
| --- | --- |
| 旧请求 begin → clear → 旧请求 append | 拒绝旧提交，不重建 key |
| 旧请求 begin → clear → 新请求 begin/append → 旧请求 append | 新历史及其 TTL 不变 |
| append → clear | 已提交历史被清除 |
| begin → TTL 过期/外部 DEL → begin → 旧 append | 全新 generation，旧提交仍拒绝 |
| 多个有效 append 并发 | 每轮问题/回答保持相邻；原子裁剪，不发生拆开的半轮写入 |

存储错误会抛出异常，不能被当作“过期的旧请求”静默忽略。只有代次已失效时 appendTurn 返回 false。

### 普通与流式调用

已提交分支的普通调用和分块输出都在调用模型前读取快照、成功后提交整轮。用户本地已有的真实流式实现也已按同样协议适配：快照包含在一次订阅内部，模型完整结束后才执行条件提交，同步入口仍复用其流式路径。保留已有显式 userId 捕获、限流、工具限制、超时、metrics 和取消资源释放。

失败、空结果、生成期间取消不会写入孤立问题或部分回答。**清空不会强制终止模型计算或撤回已传到客户端的 token**，只保证旧结果不能写回历史。若完整回答的 Redis 提交已发出，恰好同时发生的网络断开/取消不具有分布式回滚能力；但 clear 与该提交仍按 Redis 的实际执行顺序线性化。

## 旧数据与部署

1. 本轮没有连接或修改正在使用的业务 Redis，也不要求清库。
2. 历史接口可直接读取原 List。该会话首次 begin 时，在同一 Lua 脚本内转换为 Hash，保留最近 20 条及原剩余正 TTL；无 TTL 的旧记录补三天 TTL。损坏的单条 JSON 继续跳过，不记录消息正文。
3. **所有读取/写入这份会话记忆的后端必须一起升级，不可混跑旧版本。** 旧代码仍使用 List 命令，会在迁移后的 Hash 上产生 WRONGTYPE；旧进程在清空之后仍可能重建未受保护的 List。
4. 单实例部署：停止旧后端及其在途请求 → 重新编译或打包 → 启动新后端。多实例部署应先停止旧流量并排空/终止旧进程，再整体切换，不能普通滚动混跑。
5. 回退时先停止新版本，备份相关数据，按 Hash 的 messages 内容转换回原 List 并保留 TTL，或在取得用户同意后清除该部分历史；不能只回退 jar。此轮**未执行**任何数据回退/删除脚本。
6. 原工作区包含用户未提交的真实流式、配额等修改。本轮仅把针对这些修改的最小适配同步回原文件；Git 分支基于此前已提交代码修复普通/分块调用，没有把用户其他未提交功能混入提交。

## 验证

- `RedisChatMemoryIntegrationTest`：21 个真实 Redis 测试实例。自行启动随机 loopback 端口的临时进程，禁用持久化、验证自有进程启动，再连接；结束时销毁自有进程。不连接现有 6379，不使用业务 Redis 配置。
- `RedisChatMemoryTest`：4 个轻量入口测试。
- `BookCompanionClearRaceTest`：10 个调用链测试，使用真实 ChatClient 或本地真实流式编排、可控模型替身与内存记忆替身。覆盖同步/流式在途清空、新会话保留、先提交后清空、取消/错误/空响应、提示词历史及用户隔离。此层不替代上面的真实 Redis 原子性验证。
- 原有 5 个会话接口测试和本地 6 个流式限流/超时/取消测试保留并适配。
- 完整测试结果：已提交基线的独立工作区 **215 项通过**；包含用户现有改动的集成副本 **238 项通过**，均为 0 失败/错误/跳过。所有全量命令都排除会写入真实 ES 的 `EsRealSyncTest`。

执行示例（Java 21，Maven）：

```powershell
mvn '-Dtest=*,!EsRealSyncTest' test
# Redis 不在 PATH 时，可显式指定：
mvn '-Dredis.test.server=D:/javaweb/Redis/redis-server.exe' '-Dtest=RedisChatMemoryIntegrationTest' test
```

没有可执行的 redis-server 时，真实 Redis 测试会明确跳过，不应把跳过解释为验证成功。此机执行时要求零跳过。尚未测试生产 Redis 故障转移、持久化丢失、Redis Cluster 实际路由或压力极限。保证只覆盖遵守该协议且访问同一 Redis 主节点的写入方，不提供模型副作用回滚或历史消息去重。
