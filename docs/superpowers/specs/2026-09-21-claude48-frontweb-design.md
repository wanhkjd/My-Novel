# Claude4-8frontWeb 前端设计文档（第 1 期：地基 + 读者门户 + AI 书童）

- 日期：2026-09-21
- 目标目录：`front/Claude4-8frontWeb`
- 技术栈：Vue 3 + Vite + Element Plus + Pinia + Vue Router + axios
- 后端：Spring Boot（`http://localhost:8888`，无 context-path），接口前缀 `/api`
- 原则：**一切以后端接口契约与请求参数要求为准**；参考 `novel-front-web` 但不照抄。

---

## 1. 背景与范围

后端是一个 Java 求职作品级小说系统，含前台门户、会员中心、AI 智能书童、作家专区、平台管理后台五大子系统。本前端为全新现代设计的对照实现（与并列目录 `GeminiFrontWeb` 对照展示）。

整体分 3 期，每期独立可跑：

- **第 1 期（本文档）**：工程地基 + 完整 API 层（9 模块一次写全）+ 读者门户全部页面 + AI 悬浮书童。
- **第 2 期**：作家专区（注册/状态守卫、作品与章节管理）。
- **第 3 期**：管理后台（登录、菜单、dashboard、8 类资源 CRUD）。

API 层在第 1 期全部写完，2/3 期只新增页面 / store / 路由。

---

## 2. 全局接口契约（已从后端源码核实）

### 2.1 响应包装 `RestResp<T>`
```json
{ "code": "00000", "message": "一切ok", "data": <T> }
```
- 成功码：`"00000"`（字符串）。
- token 过期码：`"A0230"` → 前端清 token 并跳登录。
- axios 响应拦截器：`code !== "00000"` 时统一 `ElMessage.error(message)` 并 reject；成功时 `return res.data`（调用方拿到 `{code,message,data}`，用 `.data` 取业务数据）。

### 2.2 分页包装 `PageRespDto<T>`
```
{ pageNum:long, pageSize:long, total:long, list:T[], pages:long }
```

### 2.3 分页请求 `PageReqDto`（query）
- `pageNum`(默认1,≥1)、`pageSize`(默认10,夹[1,100])、`sort`、`order`、`fetchAll`(隐藏)。

### 2.4 鉴权
- 请求头：`Authorization`，值为**裸 JWT，无 `Bearer ` 前缀**。
- 读者 token 与管理员 token 是不同 JWT。
- **决策（已采纳）：token 分槽隔离**。
  - `novel_reader_token`（读者/作家共用，作家本质是注册成作家的读者）
  - `novel_admin_token`（管理员）
  - 请求拦截器按 URL 判定：路径含 `/admin` 用 admin 槽，否则用 reader 槽。
  - 好处：可同时保持读者与管理员登录态，互不顶替。

### 2.5 基址与代理
- 开发：环境变量前缀从参考项目的 `VUE_APP_` 改为 Vite 的 `VITE_`，即 `VITE_BASE_API_URL='/api'`；`vite.config.js` 的 server.proxy 把 `/api`、`/image` 代理到 `http://localhost:8888`。
- 生产：`VITE_BASE_API_URL` 指向后端域名 + `/api`；图片基址 `VITE_BASE_IMG_URL`。
- 图片 URL：后端返回的封面 `picUrl` 形如 `/xxx.jpg`，静态资源走 `/image/**`；封面地址前端按 `VITE_BASE_IMG_URL + picUrl` 拼接（开发环境 `VITE_BASE_IMG_URL=''`，靠 proxy）。

---

## 3. 完整 API 层（第 1 期一次写全 9 个模块）

> 路径均相对 baseURL `/api`。字段名严格沿用后端 Java 命名。

### 3.1 `api/home.js`
| 函数 | 方法 | 路径 | 返回 data |
|---|---|---|---|
| `listHomeBooks()` | GET | `/front/home/books` | `HomeBookRespDto[]`（type/bookId/picUrl/bookName/authorName/bookDesc） |
| `listHomeFriendLinks()` | GET | `/front/home/friend_Link/list` | `{linkName,linkUrl}[]` |

### 3.2 `api/news.js`
| 函数 | 方法 | 路径 | 返回 |
|---|---|---|---|
| `listLatestNews()` | GET | `/front/news/latest_list` | `NewsInfoRespDto[]` |
| `getNewsById(id)` | GET | `/front/news/{id}` | `NewsInfoRespDto`（id/categoryId/categoryName/sourceName/title/updateTime/content） |

### 3.3 `api/book.js`
| 函数 | 方法 | 路径 | 参数/返回 |
|---|---|---|---|
| `listCategory(workDirection)` | GET | `/front/book/category/list` | query `workDirection`(必填) → `{id,name}[]` |
| `getBookById(id)` | GET | `/front/book/{id}` | `BookInfoRespDto` |
| `addVisitCount(bookId)` | POST | `/front/book/visit` | query `bookId` |
| `getLastChapterAbout(bookId)` | GET | `/front/book/last_chapter/about` | `{chapterInfo,chapterTotal,contentSummary}` |
| `listRecBooks(bookId)` | GET | `/front/book/rec_list` | `BookInfoRespDto[]` |
| `listChapters(bookId)` | GET | `/front/book/chapter/list` | `BookChapterRespDto[]` |
| `getBookContent(chapterId)` | GET | `/front/book/content/{chapterId}` | `{bookInfo,chapterInfo,bookContent}` |
| `getPreChapterId(chapterId)` | GET | `/front/book/pre_chapter_id/{chapterId}` | `Long` |
| `getNextChapterId(chapterId)` | GET | `/front/book/next_chapter_id/{chapterId}` | `Long` |
| `listVisitRankBooks()` | GET | `/front/book/visit_rank` | `BookRankRespDto[]` |
| `listNewestRankBooks()` | GET | `/front/book/newest_rank` | `BookRankRespDto[]` |
| `listUpdateRankBooks()` | GET | `/front/book/update_rank` | `BookRankRespDto[]` |
| `listNewestComments(bookId)` | GET | `/front/book/comment/newest_list` | `BookCommentRespDto`（commentTotal + comments[]） |

- `BookInfoRespDto` 字段：id/categoryId/categoryName/picUrl/bookName/authorId/authorName/bookDesc/bookStatus(0连载1完结)/visitCount/wordCount/commentCount/firstChapterId/lastChapterId/lastChapterName/updateTime。
- `BookChapterRespDto`：id/bookId/chapterNum/chapterName/chapterWordCount/chapterUpdateTime/isVip(1收费0免费)。
- `BookContentAboutRespDto`：bookInfo/chapterInfo/bookContent(String)。
- `BookCommentRespDto.comments[]`：id/commentContent/commentUser/commentUserId/commentUserPhoto/commentTime。

### 3.4 `api/search.js`
| 函数 | 方法 | 路径 | 参数 |
|---|---|---|---|
| `searchBooks(condition)` | GET | `/front/search/books` | query `BookSearchReqDto` → `PageRespDto<BookInfoRespDto>` |

`BookSearchReqDto`（继承 PageReqDto）：keyword、workDirection、categoryId、isVip(1收0免)、bookStatus(0连1完)、wordCountMin、wordCountMax、updateTimeMin(yyyy-MM-dd)。

### 3.5 `api/user.js`（除 register/login 外均需 token）
| 函数 | 方法 | 路径 | 参数 |
|---|---|---|---|
| `register(dto)` | POST | `/front/user/register` | body `{username,password,velCode,sessionId}` |
| `login(dto)` | POST | `/front/user/login` | body `{username,password}` → `{uid,nickName,token}` |
| `getUserInfo()` | GET | `/front/user` | → `{nickName,userPhoto,userSex}` |
| `updateUserInfo(dto)` | PUT | `/front/user` | body `{nickName?,userPhoto?,userSex?}` |
| `submitFeedback(content)` | POST | `/front/user/feedback` | body 纯字符串（5–512 字） |
| `listFeedbacks(page)` | GET | `/front/user/feedbacks` | query PageReqDto → `PageRespDto<{id,content,createTime}>` |
| `deleteFeedback(id)` | DELETE | `/front/user/feedback/{id}` | |
| `comment(dto)` | POST | `/front/user/comment` | body `{bookId,commentContent}`(内容10–512) |
| `updateComment(id,content)` | PUT | `/front/user/comment/{id}` | query/form `content` |
| `deleteComment(id)` | DELETE | `/front/user/comment/{id}` | |
| `getBookshelfStatus(bookId)` | GET | `/front/user/bookshelf_status` | query `bookId` → `Integer`(0/1) |
| `addBookshelf(bookId)` | POST | `/front/user/bookshelf/{bookId}` | |
| `listBookshelf(page)` | GET | `/front/user/bookshelf` | query PageReqDto → `PageRespDto<BookInfoRespDto>` |
| `removeBookshelf(bookId)` | DELETE | `/front/user/bookshelf/{bookId}` | |
| `listComments(page)` | GET | `/front/user/comments` | query PageReqDto → `PageRespDto<UserCommentRespDto>` |

注册校验（后端强制）：username 手机号 `^1[3-9]\d{9}$`；velCode `^\d{4}$`；sessionId 长度 32；先调验证码接口拿 sessionId + base64 图。

### 3.6 `api/resource.js`
| 函数 | 方法 | 路径 | 返回 |
|---|---|---|---|
| `getImgVerifyCode()` | GET | `/front/resource/img_verify_code` | `{sessionId, img(base64)}` |
| `uploadImage(file)` | POST | `/front/resource/image` | multipart `file` → `String`(图片路径) |

### 3.7 `api/ai.js`（AI 书童；token 可选，登录后才能加书架/个性推荐）
| 函数 | 方法 | 路径 | 说明 |
|---|---|---|---|
| `chatStream(message,conversationId,{onChunk,onDone,onError})` | GET(SSE) | `/front/ai/companion/chat/stream` | 原生 fetch + ReadableStream 解析 SSE（axios 不适合流式） |
| `chat(message,conversationId)` | GET | `/front/ai/companion/chat` | 阻塞式 → `String` |
| `clearMemory(conversationId)` | DELETE | `/front/ai/companion/chat/memory` | 新对话时清记忆 |
| `getChatHistory(conversationId)` | GET | `/front/ai/companion/chat/history` | → `{role,content}[]` 刷新回显 |
| `recommendSimilar(count=4)` | GET | `/front/ai/companion/recommend` | → `SimilarBookRecommendDto[]` |

- 会话 ID：`crypto.randomUUID()`，持久化在 `localStorage('ai_companion_conversation_id')`；「新对话」重置。
- SSE 事件按 `\n\n` 分块，取 `data:` 行拼接。
- `SimilarBookRecommendDto`：bookId/bookName/categoryName/authorName/score/picUrl/bookDesc/recommendReason/similarity。

### 3.8 `api/author.js`（第 2 期用，第 1 期先建好文件）
10 个接口，全需 token，前缀 `/author`：status、register、books(page)、book(发布)、book/chapters/{bookId}、book/chapter/{bookId}(发布)、book/chapter/{chapterId}(GET/PUT/DELETE)、info。

### 3.9 `api/admin.js`（第 3 期用，第 1 期先建好文件）
login + info + menus + dashboard + 8 类资源 page/CRUD/status/audit，前缀 `/admin`，除 login 外全需 admin token。

---

## 4. 设计系统（全新现代 · 纸墨文学感）

`src/styles/tokens.css`（CSS 变量）：
- **主色**：`--brand:#b5352b`（朱砂红），`--brand-soft:#f6ece9`；成功/警告/危险沿用 Element Plus。
- **中性**：纸白 `--bg:#faf7f2`、卡片白 `--surface:#ffffff`、墨黑字 `--text:#2b2b2b`、次级 `--text-2:#6b6b6b`、分隔 `--border:#eae4db`。
- **字体**：标题衬线 `--font-serif:"Noto Serif SC",serif`；正文无衬线 `--font-sans:"Noto Sans SC",system-ui,sans-serif`。
- **圆角/阴影/间距**：`--radius:10px`；`--shadow-sm/md`；4px 基准间距梯度。
- Element Plus 主题：通过覆盖 `--el-color-primary` 系列对齐朱砂红。

**阅读页主题**（BookContent 专用，body dataset 切换）：day（纸白）/ sepia（护眼米黄）/ night（深灰墨）；字号 `--reader-fs`、行距 `--reader-lh` 可调，持久化到 localStorage。

**微交互**：卡片 hover `translateY(-4px)+shadow`；路由切换 fade；书童流式打字光标；骨架屏/loading。

---

## 5. 目录结构

```
Claude4-8frontWeb/
├── index.html
├── package.json           # vue3 vite element-plus pinia vue-router axios
├── vite.config.js         # @别名 + devServer proxy(/api,/image→8888) + 端口
├── .env.development / .env.production
├── src/
│   ├── main.js            # createApp + Pinia + Router + ElementPlus + 全局样式
│   ├── App.vue            # <router-view> + 全局 AiCompanion 挂载
│   ├── api/               # home news book search user resource ai author admin
│   ├── utils/
│   │   ├── request.js     # axios 实例 + 拦截器 + putForm 辅助
│   │   ├── auth.js        # 双槽 token / nickName / uid 读写
│   │   └── format.js      # 字数(万)、时间、状态文案
│   ├── stores/
│   │   ├── user.js        # 读者登录态 / userInfo / 登出
│   │   └── category.js    # 分类缓存（男频/女频）
│   ├── styles/            # tokens.css / global.css / reader.css
│   ├── layouts/
│   │   └── PortalLayout.vue   # 顶部导航 + 搜索 + 登录态 + Footer
│   ├── components/
│   │   ├── common/        # AppNavbar / AppFooter / BackTop / BookCard / Pagination / Empty
│   │   ├── home/          # HomeRecommend / RankPanel / FriendLinks / LatestNews
│   │   └── ai/            # AiCompanion.vue（悬浮书童抽屉）
│   ├── router/index.js    # 路由 + 守卫（第 1 期读者组；预留 author/admin 组）
│   └── views/
│       └── portal/        # Home Search BookClass BookRank BookDetail ChapterList
│                          # BookReader News Login Register
│                          # user/{Bookshelf,MyComments,Feedback,Profile}
```

---

## 6. 路由与守卫（第 1 期）

`createWebHistory`（生产配合 Nginx；也可 hash，二选一，spec 用 history）：

| path | 组件 | 需登录 |
|---|---|---|
| `/` → `/home` | Home | 否 |
| `/search` | Search | 否 |
| `/category` | BookClass | 否 |
| `/rank` | BookRank | 否 |
| `/book/:id` | BookDetail | 否 |
| `/book/:id/catalog` | ChapterList | 否 |
| `/read/:chapterId` | BookReader | 否 |
| `/news/:id` | News | 否 |
| `/login` `/register` | Login/Register | 否 |
| `/user/bookshelf` 等 | 个人中心 | **是** |

守卫：`meta.requiresAuth` 且无 reader token → 跳 `/login?redirect=`。`afterEach` 滚动置顶。

---

## 7. AI 书童数据流

- `App.vue` 全局挂 `AiCompanion.vue`（悬浮按钮 + Element Plus Drawer）。
- 打开时若有 conversationId → 调 `getChatHistory` 回显。
- 发送：追加 user 气泡 → 起 SSE → assistant 气泡随 `onChunk` 增量追加（打字机光标）→ `onDone` 收尾。
- 「新对话」：`clearMemory(old)` → 生成新 conversationId → 清空气泡。
- 登录态下额外展示「个性化推荐」（`recommendSimilar`）；未登录提示登录。
- token 从 reader 槽取，随 SSE fetch 头带上。

---

## 8. 错误处理

- 统一在响应拦截器 `ElMessage.error`；业务层用 try/catch 兜 UI 态。
- `A0230`（token 过期）：清对应槽 token + 跳登录（按路径区分 reader/admin）。
- 网络异常：统一「网络异常，请稍后重试」。
- 空态：列表用 `Empty` 组件；封面加载失败回退默认图。

---

## 9. 测试策略

- 组件/工具：Vitest + @vue/test-utils。
  - `utils/format.js`（字数万位、时间、状态文案）纯函数——TDD 先行。
  - `utils/auth.js` 双槽读写、`request` 拦截器对 RestResp 的拆包与 A0230 分支——用 mock 验证。
  - `api/*` 关键路径拼接（如 `/front/book/{id}`、search query）——快照/断言。
- 构建校验：`vite build` 必须通过；`vite dev` 起本地可访问。
- 因后端需 MySQL/Redis/ES 才能真实联调，单测以 mock 为主；联调在本地后端就绪时手动验证首页/详情/阅读/搜索/AI 五条主链路。

---

## 10. 非目标（本期不做，留待 2/3 期）

- 作家专区页面（API 已建文件，页面第 2 期）。
- 管理后台页面（API 已建文件，页面第 3 期）。
- 支付/充值、第三方登录（后端无对应前台接口）。

---

## 11. 验收标准（第 1 期）

1. `npm run dev` 起站，`npm run build` 通过。
2. 首页展示后端推荐位（轮播/分组）、三榜、新闻、友链，字段来自 `/front/home` `/front/book/*_rank` `/front/news`。
3. 搜索/分类可按 `BookSearchReqDto` 条件分页查询。
4. 小说详情→目录→阅读页链路通，阅读页三主题+字号可调，上/下一章可翻。
5. 登录/注册（含图形验证码 sessionId 流程）可用，token 存 reader 槽。
6. 个人中心书架/评论/反馈/资料 CRUD 通（需登录守卫）。
7. AI 书童 SSE 流式对话、历史回显、清空记忆、相似推荐可用。
8. 所有请求命中后端真实路径与参数名（与本文档接口表一致）。
