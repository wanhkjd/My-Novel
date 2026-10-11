# 评论发布与编辑共用内容校验

## 问题

发布 DTO 原有非空、10–512 长度校验；编辑入口仅接收一个未约束的 String，直接更新内容并把审核状态设为 0。缺失 content 时，MyBatis-Plus 的非空字段策略会跳过内容更新，却仍重置审核状态；本轮已在修复前源码的隔离 H2 副本中复现。

这不是创建日期问题，也不是越权漏洞：原 SQL 已同时限制评论 ID 和用户 ID，本轮保留该条件。

## 改动

- CommentContentRules 是唯一内容规则：非 null、非空白，长度 10–512（含边界）。不 trim、不自动截断。
- ValidCommentContent 是 Jakarta Bean Validation 的薄适配器，发布/编辑 DTO 都委托到同一规则。
- 编辑入口使用 @Valid @ModelAttribute UserCommentUpdateReqDto，仍接收原表单字段 content。
- saveComment 和 updateComment 在数据库读写前再次执行同一规则，直接调用服务也不能绕过校验。
- 无效内容返回现有业务错误码 A0400，不修改内容、审核状态或时间字段。
- 有效编辑仍只更新属于当前用户的指定评论，审核状态仍置为待审核（0）。不存在/非本人评论保持原来的返回约定，不扩大本轮范围。

### 空白和长度语义

长度沿用原 Hibernate @Length 的 Java UTF-16 String.length 计数：普通汉字一般计 1，Emoji 等补充字符计 2。因此 5 个 📚 为长度 10，256 个为 512。

“空白”统一使用 Java String.isBlank，纯空格、制表/换行和纯全角空格均拒绝。相较某些旧 @NotBlank 实现的 trim 判断，这也会拒绝以前可能放行的 Unicode 全空白内容。非空白内容的前后空格仍原样保留，并计入长度；本轮没有引入“至少 10 个非空白字”的新规则。NBSP（不换行空格）不属于 Java isBlank 认定的空白，本轮也不额外扩大该定义。

## 协议与兼容性

- 发布：POST /api/front/user/comment，JSON 包含 bookId、commentContent；bookId 仍必填，userId 仍由登录身份覆盖。
- 编辑：PUT /api/front/user/comment/{id}，application/x-www-form-urlencoded 的 content 字段，兼容现有 putForm 和查询参数方式。
- 没有把编辑改为 JSON 接口。缺少 content、使用错误字段、发送未支持的 JSON 正文会因缺少有效 content 被拒绝，而非静默重置审核状态。
- 用户身份/审核状态不从编辑表单取值；额外 userId、auditStatus 等字段不能改变服务调用参数。
- HTTP 错误保持现有 RestResp 约定：参数校验返回 HTTP 200 + code A0400。此轮不统一修改全项目 HTTP 状态码或全局异常处理器。
- 不修改表结构、不清理历史评论、不需要数据迁移。

## 验证

- CommentContentValidationMvcTest：真实 MVC 参数绑定、Jakarta 校验和项目异常处理器；使用 MockMvc 的 FormContentFilter 解析真实 URL 编码 PUT 正文，不只用预设参数模拟。
- 覆盖缺失/null/空串/空白、1/9/10/512/513、Emoji UTF-16 边界、特殊字符与前后空格、原 form/query 协议、创建 bookId 必填与身份覆盖。
- CommentWriteValidationTest：独立 H2 + 真实 MyBatis-Plus BookCommentMapper + 实际服务调用。验证无效更新不改变数据库、有效编辑审核重置及原归属条件、直接发布/编辑规则一致。
- 修复前源码的隔离 H2 对照验证：2 项通过，确认 null 编辑保留原内容却将已通过审核重置为待审，1 字符编辑也被实际写入。对照测试只在临时副本中保留，不作为修复后的行为要求。
- 新增专项回归：39 项 MVC + 46 项 H2，共 85 项，在提交工作区和包含原项目未提交改动的集成副本中均全部通过。
- 2026-10-11 完整安全回归：提交工作区 300 项，包含原项目未提交改动的集成副本 323 项，均 0 failures / 0 errors / 0 skipped；两边都包含上述 85 项。
- Maven 测试筛选为 `-Dtest="*,!EsRealSyncTest" test`，主动排除会写入真实 ES 的 EsRealSyncTest；上述 0 skipped 指所选集合，没有把该外部测试算成已通过。
- 完整回归包含 21 项在随机端口启动/关闭自有 Redis 进程的隔离测试，不使用正在运行的业务 Redis。

所有构建在独立工作区或原项目的临时集成副本中进行。没有调用运行中的后端，没有写业务 MySQL/Redis/ES，也没有创建真实用户评论。数据库回归不代表真实 MySQL、ShardingSphere 或负载测试。

原项目已有用户未提交的阅读历史等代码；只同步本轮精确修改，不把这些改动混入远程分支。同步后需要重新编译并重启后端才能生效。
