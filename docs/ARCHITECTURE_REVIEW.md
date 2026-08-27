# novel 单体架构审查与落地路线

> 审查时间：2026-08-26。本文记录本次包名迁移、接口补强、架构结论和可选技术启用步骤。

## 1. 本次变更

1. 后端基础包从 `io.github.xxyopen.novel` 迁移到 `novel`，Java 源码目录为 `src/main/java/novel`。
2. 同步更新了 Java import、MyBatis Mapper namespace、Logback logger 和 Maven `groupId`。
3. 使用 Git 建立版本历史：先提交原始快照，再提交包结构迁移。
4. 补充会员侧缺失闭环：
   - 书架列表：`GET /api/front/user/bookshelf`
   - 加入书架：`POST /api/front/user/bookshelf/{bookId}`
   - 移出书架：`DELETE /api/front/user/bookshelf/{bookId}`
   - 反馈列表：`GET /api/front/user/feedbacks`
5. 补充作家作品归属校验：作家后台的章节列表、查看、修改、删除只能操作本人作品。
6. 用户评论分页响应补充评论 ID 与审核状态；反馈提交增加长度校验。
7. 补充安全与稳定性修复：
   - 公开最新书评只展示审核通过的记录，编辑后的书评重新进入待审核状态。
   - 分页请求限制单页最大 100 条，并规范化页码和页大小。
   - 作家发布作品时校验分类方向、分类 ID 和分类名的一致性。
   - 加入书架改为事务方法，并直接查询当前用户书架判断重复。

## 2. 架构现状

### 分层与职责

```text
controller.front/author/admin
    HTTP 协议适配、参数绑定、登录态注入、Swagger 描述
service / service.impl
    业务规则、事务边界、业务异常
manager.cache / manager.dao / manager.message / manager.mq
    缓存编排、DAO 组合、邮件/站内信、MQ 封装
dao.mapper + resources/mapper
    MyBatis-Plus CRUD 与自定义 SQL
core.config / core.interceptor / core.auth / core.aspect
    CORS、缓存、OpenAPI、认证授权、分布式锁、排序白名单、限流
dto.req / dto.resp
    HTTP 入口和出口模型
```

### 认证链路

1. `WebConfig` 按 order 注册：
   - `FlowLimitInterceptor`
   - `FileInterceptor`
   - `AuthInterceptor`
   - `TokenParseInterceptor`
2. `AuthInterceptor` 只拦截 `/api/front/user/**`、`/api/author/**`、`/api/admin/**`。
3. 登录、注册、管理员登录被显式放行。
4. 三套认证策略通过 Bean 名称选择：
   - `frontAuthStrategy`：前台 JWT + 用户缓存。
   - `authorAuthStrategy`：前台 SSO + 作家身份检查。
   - `adminAuthStrategy`：管理员 JWT + 管理员状态检查。
5. Spring Security 目前只保护 Actuator，使用 HTTP Basic 和 `ENDPOINT_ADMIN` 角色；业务 API 由自定义拦截器负责。

这个设计的优点是轻量、可读；缺点是权限语义分散在拦截器和方法内部。后续如果菜单/按钮级权限变复杂，应把“认证”和“授权”拆开，并引入方法级注解或统一 AuthorizationFilter。

### 缓存设计

- Caffeine：首页推荐、新闻、排行榜、分类、书籍信息、章节等高频读数据。
- Redis：访问榜、友链、章节内容、用户信息、作者信息等需要跨实例共享的数据。
- 更新入口大多调用对应 CacheManager 的 evict 方法，例如新增/修改/删除章节会清掉章节、内容、小说信息缓存。

需要注意：本地缓存多实例部署时不会自动广播失效。当前单体阶段可接受；扩到多实例前应引入 Redis Pub/Sub 或 MQ 的缓存刷新事件。

### 可选技术开关

| 技术 | 当前状态 | 开关 | 已有代码入口 |
|---|---:|---|---|
| Elasticsearch | 关闭 | `spring.elasticsearch.enabled` | `EsSearchServiceImpl`、`BookToEsTask` |
| RabbitMQ | 关闭 | `spring.amqp.enabled` | `AmqpConfig`、`RabbitQueueListener` |
| XXL-JOB | 关闭 | `xxl.job.enabled` | `XxlJobConfig`、`saveToEsJobHandler` |
| ShardingSphere-JDBC | 关闭 | `spring.shardingsphere.enabled` | `ShardingSphereConfiguration` |
| Flyway | 关闭 | `spring.flyway.enabled` | 已引入依赖 |
| Spring Boot Admin Client | 关闭 | `spring.boot.admin.client.enabled` | client starter |
| Springdoc | dev 打开 | `springdoc.api-docs.enabled` | OpenAPI 配置 |

## 3. 接口审查结论

### 前台门户

已有能力：

- 首页推荐、友情链接、最新新闻、新闻详情。
- 小说分类、详情、点击量、推荐、章节列表、正文、上一章、下一章。
- 访问榜、新书榜、更新榜、最新评论。
- 数据库搜索；开启 ES 后自动切换到 ES 搜索实现。
- 图形验证码、图片上传。
- 注册、登录、用户信息、资料修改、反馈提交/删除/列表、评论发布/修改/删除/我的评论、书架状态/加入/移除/列表。

本次补强后，书架和反馈已经形成基本闭环。

### 作家端

已有能力：

- 作者注册、作者状态、作者信息。
- 创建作品、作品分页、章节创建/分页/查看/修改/删除。

本次修复了横向越权问题：非本人作品的章节管理操作现在会被拒绝。

同时，作品发布接口会拒绝伪造的分类信息，公开书评入口不会再暴露未通过审核的内容。

### 平台管理端

已有能力：

- 登录、管理员信息、动态菜单、Dashboard 统计。
- 用户/作家/小说/分类/评论分页与状态维护。
- 首页推荐、友情链接、新闻内容管理。

主要不足是大量返回 `Map<String, Object>`。它能快速支撑前端，但牺牲了编译期契约。求职项目里建议至少把核心资源改成明确 DTO，并在 Swagger 中形成稳定 schema。

## 4. 需要优先处理的风险

按优先级排列：

1. **敏感配置入库**：数据库、Redis、ES 等真实口令出现在配置文件中。应立即轮换，并改为环境变量或本地未提交 profile。
2. **付费阅读未闭环**：章节有 `isVip` 字段，支付表和消费流水表也存在，但缺少购买、扣费、订单、幂等和正文鉴权完整链路。
3. **密码哈希弱**：注册/登录仍使用 MD5 且 salt 固定为 `"0"`。新账号应改 BCrypt/Argon2，并设计旧密码登录后的渐进升级。
4. **管理员只有认证、缺细粒度授权**：任何有效管理员 token 都能访问所有管理接口；角色、菜单、角色菜单表还未参与 API 判权。
5. **公开评论列表没有过滤审核状态**：待审核或不通过评论可能进入前台展示，应只展示审核通过数据。
6. **章节字数并发一致性**：新增/修改/删除章节基于缓存中的旧总字数计算，高并发下可能漂移，应改为数据库原子累计或串行化同一作品操作。
7. **分页上限未强制**：`PageReqDto.pageSize` 应限制最大值，避免大页拖垮 MySQL。
8. **上传路径平台差异**：当前 dev 上传路径是 `/data/novel/uploads`，Windows 本地开发需要确认存在或改为环境变量。

## 5. 测试策略

已新增单元测试：

- `UserServiceImplTest`
  - 重复加入书架返回 `A2003`。
  - 书架列表保持书架顺序并正确映射书籍字段。
- `BookServiceImplOwnershipTest`
  - 非本人作者不能查看、修改、删除章节。
  - 非本人作者不能列出他人作品章节。

后续建议分层补齐：

1. Service 单测：Mock Mapper/Cache/MQ，覆盖业务分支和越权。
2. Web 切片测试：`@WebMvcTest` + Mock Service，验证路由、参数校验、HTTP 状态。
3. 数据层测试：Testcontainers 启动 MySQL，执行真实 Mapper/XML。
4. 冒烟集成测试：Testcontainers 启动 MySQL/Redis，`MockMvc` 走注册、登录、加书架、评论流程。
5. 合同测试：为前后端共同依赖的分页结构、错误码、时间格式写固定断言。

## 6. 未启动技术实现步骤

### 6.1 Elasticsearch 搜索

目标：把搜索从 `DbSearchServiceImpl` 切到 `EsSearchServiceImpl`。

1. 安装 Elasticsearch 8.x 和 IK 分词器，确保集群地址可访问。
2. 准备索引：
   ```http
   PUT /book
   ```
   映射可直接参考 `doc/es/book.http`，文本字段使用 `ik_smart`/`ik_max_word`。
3. 修改配置：
   ```yaml
   spring:
     elasticsearch:
       enabled: true
       uris:
         - http://127.0.0.1:9200
       username: elastic
       password: ${ELASTICSEARCH_PASSWORD}
   ```
4. 先手动执行一次全量导入。可以临时给 `BookToEsTask.saveToEs()` 加一个受管理员保护的触发接口，或在本地用测试类调用一次。
5. 验证索引数量与 MySQL 中 `word_count > 0` 的小说数一致。
6. 调用 `GET /api/front/search/books?keyword=...`，确认走 ES 结果且分页正确。
7. 如需增量同步，同时开启 RabbitMQ，见下一节。
8. 生产不要开启 `ssl.verification-mode: none`；应导入 CA 或使用可信证书。

### 6.2 RabbitMQ 增量同步

现有代码定义了 Fanout Exchange、队列和监听器；开启后章节变化会驱动 ES 单篇更新。

1. 启动 RabbitMQ，创建专用 vhost 和低权限账号。
2. 配置：
   ```yaml
   spring:
     amqp:
       enabled: true
     rabbitmq:
       addresses: amqp://novel:${RABBITMQ_PASSWORD}@127.0.0.1:5672/novel
       template:
         retry:
           enabled: true
           max-attempts: 3
   ```
3. 同时保持 `spring.elasticsearch.enabled=true`，否则监听器不会注册。
4. 发布或修改一章，观察日志中 `novel.book.change.queue.es.update` 消费。
5. 用 Kibana 或 `_search` 查询该 bookId，确认字段已更新。
6. 生产开启 publisher confirm、consumer retry 和死信队列，避免同步失败丢失消息。

### 6.3 XXL-JOB 定时任务

用途：调度 ES 全量重建任务 `saveToEsJobHandler`。

1. 部署 XXL-JOB Admin 并初始化 `doc/sql/xxl-job.sql`。
2. 在 Admin 创建执行器：
   - AppName：`xxl-job-executor-novel`
   - 注册方式：自动注册。
3. 配置后端：
   ```yaml
   xxl:
     job:
       enabled: true
       admin:
         addresses: http://127.0.0.1:8080/xxl-job-admin
       executor:
         appname: xxl-job-executor-novel
         logpath: logs/xxl-job/jobhandler
       accessToken: ${XXL_JOB_ACCESS_TOKEN}
   ```
4. 新建任务，JobHandler 填 `saveToEsJobHandler`。
5. 低峰期每月或每周执行一次；全量重建建议写入临时索引后再 alias 切换。
6. 查看执行日志和后端 `logs/xxl-job/jobhandler`。

### 6.4 Flyway 数据库迁移

当前 SQL 在 `doc/sql` 中手工维护。建议以后 schema 变更全部走 Flyway。

1. 创建 `src/main/resources/db/migration`。
2. 如果库已经是旧结构，先设置：
   ```yaml
   spring:
     flyway:
       enabled: true
       baseline-on-migrate: true
       baseline-version: 0
   ```
3. 第一个增量脚本命名为 `V1__init_project_baseline.sql`，内容可以为空或只做约束补齐。
4. 之后每个变更独立编号，例如 `V2__add_user_bookshelf_index.sql`。
5. 禁止修改已执行过的 checksum；变更错了就新增反向迁移。
6. CI 中用 Testcontainers 从空库跑全部迁移，再运行 DAO 测试。

### 6.5 ShardingSphere-JDBC 分表

项目已有 `book_content` 按 `chapter_id % 10` 的示例规则。

1. 先备份 `book_content`。
2. 创建物理表：
   ```sql
   CREATE TABLE book_content0 LIKE book_content;
   -- 重复到 book_content9
   ```
3. 将 `application.yml` 中 `spring.shardingsphere.enabled` 改为 `true`。
4. 核对 `shardingsphere-jdbc.yml` 的 JDBC 地址、账号和数据源名。
5. 编写双写/迁移程序：
   - 新写请求写旧表并由 ShardingSphere 路由，或引入双写开关。
   - 按 id/chapter_id 分批迁移旧数据。
   - 对比每批行数和抽样字段。
6. 迁移完成后切换读流量，观察 SQL 日志是否命中 `book_contentN`。
7. 最后下线旧单表写入，保留回滚窗口。
8. 注意元数据仓库当前使用 H2 文件，生产建议换成 JDBC/ZooKeeper etcd 等持久模式。

### 6.6 Spring Boot Admin 监控

1. 单独部署 Spring Boot Admin Server，不要和业务应用混部。
2. 给 Server 配置登录账号。
3. 业务端配置：
   ```yaml
   spring:
     boot:
       admin:
         client:
           enabled: true
           url: http://127.0.0.1:8081
           username: ${SBA_USERNAME}
           password: ${SBA_PASSWORD}
   ```
4. 生产不要 `management.endpoints.web.exposure.include: "*"`；至少收敛为 health、info、metrics、prometheus、logfile。
5. 接入 Prometheus/Grafana，重点看 JVM、Hikari、Tomcat、Redis、接口 P95/P99。

## 7. 下一步重构顺序

1. 外置所有密钥，轮换已泄露口令。
2. 为加入书架补充数据库唯一约束，作为并发提交的最终防线。
3. 把管理员接口从 Map 改成 typed DTO，并接入角色/权限校验。
4. 设计付费章节领域模块：订单、支付回调、消费流水、购买关系、正文鉴权、幂等。
5. 引入 Testcontainers 集成测试和 GitHub Actions/Jenkins 流水线。
6. 为核心接口补 OpenAPI 注解和响应 DTO，导出前后端契约。
7. 多实例部署前解决本地缓存失效广播和定时任务重复执行。
