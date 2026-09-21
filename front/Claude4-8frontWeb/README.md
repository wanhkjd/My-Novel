# Claude4-8 小说前台 Web

对接后端 Spring Boot 小说系统的读者前台，基于 **Vue 3 + Vite 5 + Element Plus + Pinia**。
包含书城首页、分类/排行/搜索、书籍详情与沉浸式阅读、新闻、会员中心，以及 **AI 智能书童**（SSE 流式对话 + 相似书推荐）。

> 设计原则：**以后端接口与请求参数为准**。前端契约（路径、参数、鉴权、返回结构）全部对齐后端源码，任何与参考前端不一致处均以后端实现为准。

## 技术栈

- Vue 3.4（Composition API `<script setup>`）
- Vite 5、Vue Router 4、Pinia 2
- Element Plus 2.x + `@element-plus/icons-vue`
- axios 1.x（业务接口）、原生 `fetch` + `ReadableStream`（AI SSE 流）
- Vitest + @vue/test-utils（jsdom）单元测试

## 快速开始

```bash
npm install
npm run dev      # 开发服务器，默认 http://localhost:2048
npm run build    # 生产构建 -> dist/
npm run preview  # 预览构建产物
npm test         # 运行单元测试（vitest run）
```

开发前请先启动后端（默认 `http://localhost:8888`）。`vite.config.js` 已把 `/api`、`/image` 代理到该地址，`/api` 开启 `ws`（供 SSE 使用）。

## 环境变量

| 变量 | 开发 | 生产 | 说明 |
| --- | --- | --- | --- |
| `VITE_BASE_API_URL` | `/api` | `https://book.mywsy.cc.cd/api` | 接口基地址；开发走 Vite 代理 |
| `VITE_BASE_IMG_URL` | 空（走代理） | `https://book.mywsy.cc.cd` | 图片/封面资源前缀 |

## 目录结构

```
src/
├── api/          # 按后端控制器划分的接口层（home/news/book/search/resource/user/ai/author/admin）
├── components/   # 通用组件、首页区块、AI 书童
│   └── ai/AiCompanion.vue   # 全局悬浮书童（FAB + 抽屉对话）
├── layouts/      # PortalLayout（前台外壳：导航 + 页脚）
├── views/portal/ # 页面（首页/分类/排行/搜索/详情/阅读/新闻/登录注册 + user 会员中心）
├── stores/       # Pinia：user（登录态/资料）、category（分类缓存）
├── router/       # 路由 + requiresAuth 守卫；/read/:chapterId 为沉浸式独立布局
├── styles/       # tokens.css（设计变量）、global.css、reader.css（阅读器主题）
├── utils/        # request（axios 封装）、auth（双槽 token）、format（字数/时间等）
└── main.js       # 应用装配；注册 request 的未授权跳转处理器
```

## 后端契约要点

**统一响应** `RestResp<T>`：`{ code, message, data }`。成功 `code === "00000"`；token 失效 `code === "A0230"`。
分页 `PageRespDto`：`{ pageNum, pageSize, total, list, pages }`。

**鉴权**：请求头 `Authorization` 携带 **裸 JWT（无 `Bearer ` 前缀）**。
双槽隔离——`novel_reader_token`（读者/作家）与 `novel_admin_token`（管理端）；拦截器按 URL 是否含 `/admin` 选取对应 token。

**响应拦截器** 返回完整 `response.data`（即 RestResp），调用方取 `.data` 得业务数据。命中 `A0230` 时按 URL 清除对应 token 槽并触发跳转登录。

### AI 智能书童（重要契约差异）

后端 `WebConfig` 的 `authInterceptor` 拦截 **整个** AI 前缀（`/front/ai/companion/**`），仅放行 `sync-vectors`。
因此 **书童的对话 / 历史 / 推荐接口全部要求读者登录**。

前端据此处理：

- 未登录时抽屉内展示"登录后使用书童"引导，**不发起**历史/推荐/对话请求。
- 登录后 `watch(visible)` 才拉取历史与推荐。
- SSE 鉴权失败时后端返回 **HTTP 200 + JSON(RestResp)**（而非 `text/event-stream`）。`streamChat` 据 `content-type` 甄别：命中 `application/json` 则解析 body，`A0230` 提示重新登录，否则抛出 `message`。

## 实现说明与偏差

- **request ↔ router 解耦**：`request.js` 不直接 `import router`，而是通过 `setUnauthorizedHandler(fn)` 由 `main.js` 注入跳转逻辑，避免循环依赖并让纯函数可脱离 router 单测。
- **`submitFeedback`**：后端 `@RequestBody String content`，前端以原始字符串 + `application/json` 提交（长度 5–512）。
- **`updateComment`**：以表单方式提交 `content`（`putForm`）。
- **`Profile` 头像**：后端对 `userPhoto` 有正则校验，空值时不提交该字段。
- **AI 书童鉴权**：见上，前端行为以后端 `WebConfig` 实拦截规则为准（覆盖了初版"AI 可匿名"的设想）。

## 测试

`tests/` 下覆盖 format 工具、auth 双槽 token、request 拦截器（成功/错误/A0230）、book API 契约、user store。

```bash
npm test
```
