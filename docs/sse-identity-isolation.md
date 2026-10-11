# SSE 请求身份隔离

## 问题

Spring MVC 在 SSE 请求进入异步处理后，会先把原 servlet 线程归还线程池，并调用 AsyncHandlerInterceptor.afterConcurrentHandlingStarted，而不是在原线程调用普通的 afterCompletion。旧身份拦截器只在 afterCompletion 清理 ThreadLocal；无 Token 的正文读取又不覆盖旧值，因此匿名请求可能继承上一位读者身份，影响试读限制和阅读历史归属。

认证失败也有相似缺口：作家认证等流程可能先设置 userId，随后拒绝访问。preHandle 返回 false 或抛异常时，Spring 不会对该拦截器调用 afterCompletion，必须当场清理。

## 本次修改

只修改 AuthInterceptor 和 TokenParseInterceptor 的生命周期处理：

1. 两者在 preHandle 入口清空旧 userId、authorId，再按本次请求重新认证或解析。
2. AuthInterceptor 在认证未成功时通过 finally 清理，包括业务拒绝、依赖异常及写失败响应时的异常。
3. 两者实现 AsyncHandlerInterceptor，在 afterConcurrentHandlingStarted 清理即将归还线程池的原线程。
4. 保留 afterCompletion 清理，覆盖同步完成、处理异常以及异步重新派发结束。

未修改路由、Token 规则、读者封禁规则、上传权限或 SSE 响应协议。没有引入全局 Reactor ThreadLocal 传播，也没有在响应流的 doFinally 中清理不属于该回调线程的请求身份。

## 异步业务约束

UserHolder 只代表当前 servlet 线程的请求身份，不是跨线程上下文。

- 异步操作应在经过认证的同步入口捕获 userId，随后通过参数、闭包等方式显式传递。
- 不要在异步订阅、模型回调、工具执行线程重新从 UserHolder 获取请求发起者。
- 不要为了“传递”身份把请求 userId 写进共享工作线程的 ThreadLocal。

用户当前工作区的流式实现已在进入异步链前捕获 userId，并用 forUser(userId) 绑定工具。此次保留这些既有改动，没有把整套流式实现混入本提交。本地集成测试继续覆盖真实流式实现的身份捕获，以及工具在工作线程持有另一身份时仍操作捕获的读者。

## 回归测试（2026-10-11）

新增两组共 **19 项**测试：

- IdentityInterceptorLifecycleTest：11 项，分别验证两个拦截器的异步回调、正常/异常完成、认证前清空、部分认证失败清理，以及空/非法/读者/管理员 Token 的可选解析。
- SseIdentityIsolationTest：8 项，使用生产 WebConfig 映射、真实认证策略和控制器，验证 SSE 移交后立即清空、同线程匿名正文读取、两位读者交错请求及逆序完成、超时、断连、脏线程及同步异常。

异步测试使用受控 Flux、单独线程发出流信号，以及取消信号闩锁，不以 sleep 推测异步完成。Spring MVC 6.1 断连测试包含错误通知后的下一次发送，以触发对上游的取消；不声称任意静默上游在断连后都会立即取消。

- **修复前**：19 项运行，13 个 failure、3 个 error、3 项通过。3 个 error 均为真实 MVC 控制器内的身份断言被 ServletException 包装，说明旧身份确实到达正文业务入口。
- **修复后，提交分支**：82 项通过，0 失败、0 错误、0 跳过。
- **修复后，当前工作区的隔离集成副本**：105 项通过，0 失败、0 错误、0 跳过。额外用例来自既有本地改动，不包含在本提交中。

测试选择：

~~~shell
mvn "-Dtest=*,!EsRealSyncTest" test
~~~

实际验证使用 JDK 21、Maven 离线依赖及本机临时 settings。排除会写真实 Elasticsearch 的 EsRealSyncTest。数据库、模型、Redis 与正文存储依赖使用 mock，身份和 JWT 密钥均为测试夹具；没有对线上/本地真实账号执行串读、历史写入或模型调用。

## 生效方式及范围

源码更新后重新构建并重启后端；前端不需要变更。本次构建仅在独立工作树和临时副本执行，不改写当前运行中的 target/classes。

本轮仅修复 servlet 身份生命周期。作者状态封禁、公开评论隐私、章节并发更新、清空历史与在途请求竞态、异步执行器容量等需要分别评估，不属于本次修复。
