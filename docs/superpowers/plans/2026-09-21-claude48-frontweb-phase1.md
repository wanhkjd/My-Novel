# Claude4-8frontWeb 第 1 期实现计划（地基 + 读者门户 + AI 书童）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `front/Claude4-8frontWeb` 搭建一个 Vue 3 + Vite 前端，对接后端读者门户与 AI 书童全部接口，实现全新纸墨文学感 UI。

**Architecture:** 单 Vite SPA，路由级懒加载 + 三套布局预留。分层为 `utils`（请求/鉴权/格式化）→ `api`（与后端模块 1:1）→ `stores`（Pinia）→ `views/components`。可测试核心（utils/api/stores）走严格 TDD，Vue 组件走「构建通过 + 手动主链路验证」。

**Tech Stack:** Vue 3.4 (Composition API `<script setup>`), Vite 5, Element Plus 2.x, Pinia 2, Vue Router 4, axios 1.x, Vitest + @vue/test-utils。

**Spec:** `docs/superpowers/specs/2026-09-21-claude48-frontweb-design.md`（本计划依据该 spec，执行者需同时阅读两者）

## Global Constraints

- 目标目录：`C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb`（下称项目根）。所有相对路径均相对此目录。
- 后端基址：开发环境 `VITE_BASE_API_URL='/api'`，由 Vite proxy 转发到 `http://localhost:8888`。
- 统一响应：`{ code:string, message:string, data:T }`，成功码 **`"00000"`**，token 过期码 **`"A0230"`**。
- 分页体：`{ pageNum, pageSize, total, list, pages }`。
- 鉴权头：**`Authorization`**，值为**裸 JWT，无 `Bearer ` 前缀**。
- token 双槽：`novel_reader_token`（读者/作家）、`novel_admin_token`（管理员）；请求 URL 含 `/admin` 用 admin 槽，否则 reader 槽。
- 响应拦截器成功时 `return response.data`（即整个 `RestResp`），调用方用 `.data` 取业务数据。
- 字段名严格沿用后端 Java 命名，不得改写。
- 提交信息结尾附：`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`。
- git 操作与文件写入统一用绝对路径（工作目录可能漂移）。

---

## 文件结构总览

```
front/Claude4-8frontWeb/
├── index.html
├── package.json
├── vite.config.js
├── vitest.config.js
├── .env.development
├── .env.production
├── jsconfig.json
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── api/{home,news,book,search,resource,user,ai,author,admin}.js
│   ├── utils/{request,auth,format}.js
│   ├── stores/{user,category}.js
│   ├── styles/{tokens,global,reader}.css
│   ├── layouts/PortalLayout.vue
│   ├── components/
│   │   ├── common/{AppNavbar,AppFooter,BackTop,BookCard,Pagination,EmptyBlock}.vue
│   │   ├── home/{HomeRecommend,RankPanel,FriendLinks,LatestNews}.vue
│   │   └── ai/AiCompanion.vue
│   ├── router/index.js
│   └── views/portal/{Home,Search,BookClass,BookRank,BookDetail,ChapterList,BookReader,News,Login,Register}.vue
│       └── views/portal/user/{Bookshelf,MyComments,Feedback,Profile}.vue
└── tests/{format,auth,request,api-book,store-user}.spec.js
```

---

## Task 1: 脚手架与工程配置

**Files:**
- Create: `front/Claude4-8frontWeb/package.json`
- Create: `front/Claude4-8frontWeb/vite.config.js`
- Create: `front/Claude4-8frontWeb/vitest.config.js`
- Create: `front/Claude4-8frontWeb/index.html`
- Create: `front/Claude4-8frontWeb/jsconfig.json`
- Create: `front/Claude4-8frontWeb/.env.development`
- Create: `front/Claude4-8frontWeb/.env.production`
- Create: `front/Claude4-8frontWeb/.gitignore`
- Create: `front/Claude4-8frontWeb/src/main.js`
- Create: `front/Claude4-8frontWeb/src/App.vue`

**Interfaces:**
- Produces: 可运行的 `npm run dev` / `npm run build` / `npm run test` 环境；`@` 别名指向 `src`；proxy `/api`+`/image`→8888。

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "claude48-front-web",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "axios": "^1.7.2",
    "element-plus": "^2.7.6",
    "@element-plus/icons-vue": "^2.3.1",
    "pinia": "^2.1.7",
    "vue": "^3.4.31",
    "vue-router": "^4.4.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.5",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^24.1.0",
    "vite": "^5.3.3",
    "vitest": "^1.6.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.js**

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 2048,
    proxy: {
      '/api': { target: 'http://localhost:8888', changeOrigin: true, ws: true },
      '/image': { target: 'http://localhost:8888', changeOrigin: true }
    }
  }
})
```

- [ ] **Step 3: 创建 vitest.config.js**

```js
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  test: { environment: 'jsdom', globals: true }
})
```

- [ ] **Step 4: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>墨书 · 在线书城</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

- [ ] **Step 5: 创建 jsconfig.json、.gitignore、.env 文件**

`jsconfig.json`:
```json
{ "compilerOptions": { "baseUrl": ".", "paths": { "@/*": ["src/*"] } }, "exclude": ["node_modules", "dist"] }
```

`.gitignore`:
```
node_modules
dist
*.local
.DS_Store
```

`.env.development`:
```
VITE_BASE_API_URL=/api
VITE_BASE_IMG_URL=
```

`.env.production`:
```
VITE_BASE_API_URL=https://book.mywsy.cc.cd/api
VITE_BASE_IMG_URL=https://book.mywsy.cc.cd
```

- [ ] **Step 6: 创建 src/main.js（先最小可跑）**

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import '@/styles/tokens.css'
import '@/styles/global.css'

const app = createApp(App)
for (const [key, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, comp)
}
app.config.errorHandler = (err) => {
  if (err && (err.isApiError || err.name === 'ApiError')) return
  console.error(err)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 7: 创建 src/App.vue（骨架，AiCompanion 待 Task 21 挂载）**

```vue
<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup></script>

<style>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
```

- [ ] **Step 8: 安装依赖**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm install`
Expected: 依赖装好，无 error（warning 可忽略）。

> 注：Task 1 此时还不能 `npm run dev`（缺 router/styles，后续任务补齐）。本任务交付物 = 工程配置就绪 + 依赖安装成功。

- [ ] **Step 9: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb && git commit -m "chore(c48-web): 初始化 Vite 工程脚手架与配置

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 设计系统（tokens + 全局样式 + 阅读页主题）

**Files:**
- Create: `front/Claude4-8frontWeb/src/styles/tokens.css`
- Create: `front/Claude4-8frontWeb/src/styles/global.css`
- Create: `front/Claude4-8frontWeb/src/styles/reader.css`

**Interfaces:**
- Produces: 全局 CSS 变量（`--brand` 朱砂红等）、Element Plus 主色覆盖、通用工具类（`.container`、`.card`、`.text-ellipsis`）、阅读页三主题类。

- [ ] **Step 1: 创建 tokens.css**

```css
:root {
  --brand: #b5352b;
  --brand-hover: #c8443a;
  --brand-soft: #f6ece9;
  --bg: #faf7f2;
  --surface: #ffffff;
  --text: #2b2b2b;
  --text-2: #6b6b6b;
  --text-3: #9a9a9a;
  --border: #eae4db;
  --radius: 10px;
  --radius-sm: 6px;
  --shadow-sm: 0 2px 8px rgba(60, 40, 30, 0.06);
  --shadow-md: 0 8px 24px rgba(60, 40, 30, 0.10);
  --font-serif: "Noto Serif SC", "Songti SC", serif;
  --font-sans: "Noto Sans SC", system-ui, -apple-system, "Microsoft YaHei", sans-serif;
  --sp-1: 4px; --sp-2: 8px; --sp-3: 12px; --sp-4: 16px; --sp-5: 24px; --sp-6: 32px;
  /* Element Plus 主色对齐朱砂红 */
  --el-color-primary: #b5352b;
  --el-color-primary-light-3: #c8443a;
  --el-color-primary-light-5: #d66a62;
  --el-color-primary-light-7: #e59d98;
  --el-color-primary-light-9: #f6ece9;
  --el-color-primary-dark-2: #93291f;
}
```

- [ ] **Step 2: 创建 global.css**

```css
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body {
  background: var(--bg);
  color: var(--text);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.6;
}
a { color: inherit; text-decoration: none; }
h1, h2, h3, h4 { font-family: var(--font-serif); font-weight: 600; margin: 0; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 var(--sp-4); }
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.text-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.text-clamp-2 {
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.text-clamp-3 { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.brand-text { color: var(--brand); }
.hover-lift { transition: transform .2s ease, box-shadow .2s ease; }
.hover-lift:hover { transform: translateY(-4px); box-shadow: var(--shadow-md); }
.section-title {
  font-family: var(--font-serif); font-size: 20px; font-weight: 600;
  border-left: 4px solid var(--brand); padding-left: var(--sp-3); margin-bottom: var(--sp-4);
}
```

- [ ] **Step 3: 创建 reader.css**

```css
.reader-theme-day   { --reader-bg: #faf7f2; --reader-text: #2b2b2b; }
.reader-theme-sepia { --reader-bg: #f3e9d2; --reader-text: #4b3f2f; }
.reader-theme-night { --reader-bg: #1e1e1e; --reader-text: #b8b8b8; }
.reader-page {
  background: var(--reader-bg);
  color: var(--reader-text);
  min-height: 100vh;
  transition: background .3s ease, color .3s ease;
}
.reader-body {
  max-width: 720px; margin: 0 auto; padding: 40px 20px;
  font-size: var(--reader-fs, 18px);
  line-height: var(--reader-lh, 1.9);
  font-family: var(--font-serif);
}
.reader-body p { margin: 0 0 1em; text-indent: 2em; }
```

- [ ] **Step 4: 校验样式被 main.js 引用**

Run: `grep -n "tokens.css\|global.css" "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb/src/main.js"`
Expected: 两行都命中（Task 1 Step 6 已引入）。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/styles && git commit -m "feat(c48-web): 纸墨文学感设计系统 tokens 与全局样式

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: utils/format.js（纯函数，TDD）

**Files:**
- Create: `front/Claude4-8frontWeb/src/utils/format.js`
- Test: `front/Claude4-8frontWeb/tests/format.spec.js`

**Interfaces:**
- Produces:
  - `formatWordCount(n: number): string` — ≥10000 返回 `"X.X万字"`（保留1位，去尾0），否则 `"N字"`；非数字返回 `"0字"`。
  - `formatVisit(n: number): string` — ≥10000 返回 `"X.X万"`，否则原数字字符串；非数字返回 `"0"`。
  - `bookStatusText(s: number): string` — `1→"已完结"`，否则 `"连载中"`。
  - `formatTime(v: string): string` — 空值返回 `"-"`，否则原样返回（后端已格式化字符串）。

- [ ] **Step 1: 写失败测试**

```js
import { describe, it, expect } from 'vitest'
import { formatWordCount, formatVisit, bookStatusText, formatTime } from '@/utils/format'

describe('format', () => {
  it('formatWordCount', () => {
    expect(formatWordCount(500)).toBe('500字')
    expect(formatWordCount(12000)).toBe('1.2万字')
    expect(formatWordCount(20000)).toBe('2万字')
    expect(formatWordCount(null)).toBe('0字')
  })
  it('formatVisit', () => {
    expect(formatVisit(999)).toBe('999')
    expect(formatVisit(35000)).toBe('3.5万')
    expect(formatVisit(undefined)).toBe('0')
  })
  it('bookStatusText', () => {
    expect(bookStatusText(1)).toBe('已完结')
    expect(bookStatusText(0)).toBe('连载中')
  })
  it('formatTime', () => {
    expect(formatTime('')).toBe('-')
    expect(formatTime('2026-09-21 10:00')).toBe('2026-09-21 10:00')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/format.spec.js`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现 format.js**

```js
export function formatWordCount(n) {
  if (typeof n !== 'number' || isNaN(n)) return '0字'
  if (n >= 10000) return `${parseFloat((n / 10000).toFixed(1))}万字`
  return `${n}字`
}

export function formatVisit(n) {
  if (typeof n !== 'number' || isNaN(n)) return '0'
  if (n >= 10000) return `${parseFloat((n / 10000).toFixed(1))}万`
  return String(n)
}

export function bookStatusText(s) {
  return s === 1 ? '已完结' : '连载中'
}

export function formatTime(v) {
  return v ? String(v) : '-'
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/format.spec.js`
Expected: PASS（4 个用例）。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/utils/format.js front/Claude4-8frontWeb/tests/format.spec.js && git commit -m "feat(c48-web): 格式化工具函数(字数/点击/状态/时间)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: utils/auth.js（双槽 token，TDD）

**Files:**
- Create: `front/Claude4-8frontWeb/src/utils/auth.js`
- Test: `front/Claude4-8frontWeb/tests/auth.spec.js`

**Interfaces:**
- Produces：
  - `getReaderToken()/setReaderToken(t)/removeReaderToken()`
  - `getAdminToken()/setAdminToken(t)/removeAdminToken()`
  - `getNickName()/setNickName(n)/removeNickName()`
  - `getUid()/setUid(u)/removeUid()`
  - `tokenForUrl(url: string): string|null` — url 含 `/admin` 返回 admin 槽，否则 reader 槽（无则返回 `null`）。
- localStorage keys: `novel_reader_token`、`novel_admin_token`、`nickName`、`uid`。

- [ ] **Step 1: 写失败测试**

```js
import { describe, it, expect, beforeEach } from 'vitest'
import {
  getReaderToken, setReaderToken, removeReaderToken,
  getAdminToken, setAdminToken, tokenForUrl
} from '@/utils/auth'

describe('auth', () => {
  beforeEach(() => localStorage.clear())

  it('reader token roundtrip', () => {
    expect(getReaderToken()).toBeNull()
    setReaderToken('R1')
    expect(getReaderToken()).toBe('R1')
    removeReaderToken()
    expect(getReaderToken()).toBeNull()
  })

  it('tokenForUrl picks the right slot', () => {
    setReaderToken('R1')
    setAdminToken('A1')
    expect(tokenForUrl('/front/book/1')).toBe('R1')
    expect(tokenForUrl('/admin/dashboard')).toBe('A1')
  })

  it('tokenForUrl returns null when slot empty', () => {
    expect(tokenForUrl('/front/user')).toBeNull()
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/auth.spec.js`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现 auth.js**

```js
const READER_KEY = 'novel_reader_token'
const ADMIN_KEY = 'novel_admin_token'
const NICK_KEY = 'nickName'
const UID_KEY = 'uid'

const read = (k) => localStorage.getItem(k)
const write = (k, v) => localStorage.setItem(k, v)
const clear = (k) => localStorage.removeItem(k)

export const getReaderToken = () => read(READER_KEY)
export const setReaderToken = (t) => write(READER_KEY, t)
export const removeReaderToken = () => clear(READER_KEY)

export const getAdminToken = () => read(ADMIN_KEY)
export const setAdminToken = (t) => write(ADMIN_KEY, t)
export const removeAdminToken = () => clear(ADMIN_KEY)

export const getNickName = () => read(NICK_KEY)
export const setNickName = (n) => write(NICK_KEY, n)
export const removeNickName = () => clear(NICK_KEY)

export const getUid = () => read(UID_KEY)
export const setUid = (u) => write(UID_KEY, u)
export const removeUid = () => clear(UID_KEY)

export function tokenForUrl(url = '') {
  const isAdmin = url.includes('/admin')
  return isAdmin ? getAdminToken() : getReaderToken()
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/auth.spec.js`
Expected: PASS（3 个用例）。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/utils/auth.js front/Claude4-8frontWeb/tests/auth.spec.js && git commit -m "feat(c48-web): 双槽 token 鉴权工具(读者/管理员隔离)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: utils/request.js（axios 实例 + 拦截器，TDD）

**Files:**
- Create: `front/Claude4-8frontWeb/src/utils/request.js`
- Test: `front/Claude4-8frontWeb/tests/request.spec.js`

**Interfaces:**
- Consumes: `@/utils/auth` 的 `tokenForUrl`、`removeReaderToken`、`removeAdminToken`、`removeNickName`、`removeUid`。
- Produces:
  - default export `request` = axios 实例（baseURL=`import.meta.env.VITE_BASE_API_URL`，timeout 15000）。
  - 请求拦截器：把 `tokenForUrl(config.url)` 写入 `config.headers.Authorization`（有值才写）。
  - 响应拦截器：`code==="00000"` 返回 `response.data`；否则构造 `ApiError`（含 code/message/data，`isApiError=true`）并 `ElMessage.error`，`A0230` 时清 token 并跳登录；reject。
  - 具名导出 `putForm(url, content)` — 以 `application/x-www-form-urlencoded` 发送 `{content}`（评论更新用）。
  - 常量导出 `SUCCESS_CODE='00000'`、`TOKEN_EXPIRED_CODE='A0230'`。
- 说明：拦截逻辑抽成可单测的纯函数 `handleSuccess(response)` 与 `buildApiError(payload)` 一并导出，测试针对它们。

- [ ] **Step 1: 写失败测试**

```js
import { describe, it, expect } from 'vitest'
import { handleSuccess, buildApiError, SUCCESS_CODE } from '@/utils/request'

describe('request interceptors', () => {
  it('handleSuccess returns body on success code', () => {
    const body = { code: SUCCESS_CODE, message: 'ok', data: { id: 1 } }
    expect(handleSuccess({ data: body })).toEqual(body)
  })

  it('handleSuccess throws ApiError on business error', () => {
    const body = { code: 'B0001', message: '业务失败', data: null }
    expect(() => handleSuccess({ data: body })).toThrowError()
    try { handleSuccess({ data: body }) }
    catch (e) { expect(e.isApiError).toBe(true); expect(e.code).toBe('B0001') }
  })

  it('buildApiError carries fields', () => {
    const e = buildApiError({ code: 'X', message: 'm', data: 1 })
    expect(e.isApiError).toBe(true)
    expect(e.code).toBe('X')
    expect(e.message).toBe('m')
    expect(e.name).toBe('ApiError')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/request.spec.js`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现 request.js**

```js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import {
  tokenForUrl, removeReaderToken, removeAdminToken, removeNickName, removeUid
} from '@/utils/auth'

export const SUCCESS_CODE = '00000'
export const TOKEN_EXPIRED_CODE = 'A0230'

export function buildApiError(payload, raw) {
  const err = new Error((payload && payload.message) || '请求失败')
  err.name = 'ApiError'
  err.isApiError = true
  err.code = payload && payload.code
  err.data = payload && payload.data
  err.raw = raw || payload
  return err
}

// 纯逻辑：成功返回 body，业务失败抛 ApiError（不含副作用，供单测）
export function handleSuccess(response) {
  const body = response.data
  if (!body || typeof body !== 'object') {
    throw buildApiError({ message: '服务端异常' }, response)
  }
  if (String(body.code) !== SUCCESS_CODE) {
    throw buildApiError(body)
  }
  return body
}

const request = axios.create({
  baseURL: import.meta.env.VITE_BASE_API_URL,
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = tokenForUrl(config.url || '')
  if (token) config.headers.Authorization = token
  return config
}, (e) => Promise.reject(e))

request.interceptors.response.use(
  (response) => {
    try {
      return handleSuccess(response)
    } catch (err) {
      ElMessage.error(err.message)
      if (err.code === TOKEN_EXPIRED_CODE) {
        const isAdmin = (response.config.url || '').includes('/admin')
        if (isAdmin) { removeAdminToken() } else { removeReaderToken() }
        removeNickName(); removeUid()
        const path = isAdmin ? '/admin/login' : '/login'
        router.push(path).catch(() => {})
      }
      return Promise.reject(err)
    }
  },
  (error) => {
    const apiErr = buildApiError({ message: '网络异常，请稍后重试', code: 'NETWORK_ERROR' }, error)
    ElMessage.error(apiErr.message)
    return Promise.reject(apiErr)
  }
)

export function putForm(url, content) {
  return request.put(url, new URLSearchParams({ content }), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
}

export default request
```

- [ ] **Step 4: 运行确认通过**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/request.spec.js`
Expected: PASS（3 个用例）。

> 注：测试只覆盖 `handleSuccess`/`buildApiError` 纯逻辑，不触发 axios 实例创建的副作用（router/ElMessage 在测试环境未初始化，但纯函数不引用它们）。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/utils/request.js front/Claude4-8frontWeb/tests/request.spec.js && git commit -m "feat(c48-web): axios 请求封装(RestResp 拆包/A0230/双槽注入)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: API 层 — 读者查询模块（home/news/book/search/resource）

**Files:**
- Create: `front/Claude4-8frontWeb/src/api/home.js`
- Create: `front/Claude4-8frontWeb/src/api/news.js`
- Create: `front/Claude4-8frontWeb/src/api/book.js`
- Create: `front/Claude4-8frontWeb/src/api/search.js`
- Create: `front/Claude4-8frontWeb/src/api/resource.js`
- Test: `front/Claude4-8frontWeb/tests/api-book.spec.js`

**Interfaces:**
- Consumes: `@/utils/request` default export。
- Produces: 各 api 函数返回 `request` 的 Promise（resolve 值为 `RestResp`）。签名见下方代码。

- [ ] **Step 1: 写失败测试（校验路径与参数拼接，mock request）**

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'

const calls = []
vi.mock('@/utils/request', () => ({
  default: {
    get: (url, cfg) => { calls.push(['get', url, cfg]); return Promise.resolve({ data: null }) },
    post: (url, data, cfg) => { calls.push(['post', url, data, cfg]); return Promise.resolve({ data: null }) }
  }
}))

import * as bookApi from '@/api/book'

describe('book api paths', () => {
  beforeEach(() => { calls.length = 0 })

  it('getBookById hits /front/book/{id}', () => {
    bookApi.getBookById(42)
    expect(calls[0][0]).toBe('get')
    expect(calls[0][1]).toBe('/front/book/42')
  })
  it('getBookContent hits /front/book/content/{chapterId}', () => {
    bookApi.getBookContent(7)
    expect(calls[0][1]).toBe('/front/book/content/7')
  })
  it('listCategory passes workDirection as query', () => {
    bookApi.listCategory(0)
    expect(calls[0][1]).toBe('/front/book/category/list')
    expect(calls[0][2]).toEqual({ params: { workDirection: 0 } })
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/api-book.spec.js`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现 5 个 api 文件**

`src/api/home.js`:
```js
import request from '@/utils/request'
export const listHomeBooks = () => request.get('/front/home/books')
export const listHomeFriendLinks = () => request.get('/front/home/friend_Link/list')
```

`src/api/news.js`:
```js
import request from '@/utils/request'
export const listLatestNews = () => request.get('/front/news/latest_list')
export const getNewsById = (id) => request.get(`/front/news/${id}`)
```

`src/api/book.js`:
```js
import request from '@/utils/request'
export const listCategory = (workDirection) =>
  request.get('/front/book/category/list', { params: { workDirection } })
export const getBookById = (id) => request.get(`/front/book/${id}`)
export const addVisitCount = (bookId) =>
  request.post('/front/book/visit', null, { params: { bookId } })
export const getLastChapterAbout = (bookId) =>
  request.get('/front/book/last_chapter/about', { params: { bookId } })
export const listRecBooks = (bookId) =>
  request.get('/front/book/rec_list', { params: { bookId } })
export const listChapters = (bookId) =>
  request.get('/front/book/chapter/list', { params: { bookId } })
export const getBookContent = (chapterId) => request.get(`/front/book/content/${chapterId}`)
export const getPreChapterId = (chapterId) => request.get(`/front/book/pre_chapter_id/${chapterId}`)
export const getNextChapterId = (chapterId) => request.get(`/front/book/next_chapter_id/${chapterId}`)
export const listVisitRankBooks = () => request.get('/front/book/visit_rank')
export const listNewestRankBooks = () => request.get('/front/book/newest_rank')
export const listUpdateRankBooks = () => request.get('/front/book/update_rank')
export const listNewestComments = (bookId) =>
  request.get('/front/book/comment/newest_list', { params: { bookId } })
```

`src/api/search.js`:
```js
import request from '@/utils/request'
export const searchBooks = (condition) =>
  request.get('/front/search/books', { params: condition })
```

`src/api/resource.js`:
```js
import request from '@/utils/request'
export const getImgVerifyCode = () => request.get('/front/resource/img_verify_code')
export const uploadImage = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/front/resource/image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/api-book.spec.js`
Expected: PASS（3 个用例）。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/api/home.js front/Claude4-8frontWeb/src/api/news.js front/Claude4-8frontWeb/src/api/book.js front/Claude4-8frontWeb/src/api/search.js front/Claude4-8frontWeb/src/api/resource.js front/Claude4-8frontWeb/tests/api-book.spec.js && git commit -m "feat(c48-web): 读者查询 API 层(home/news/book/search/resource)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: API 层 — 会员/AI/作家/管理员模块

**Files:**
- Create: `front/Claude4-8frontWeb/src/api/user.js`
- Create: `front/Claude4-8frontWeb/src/api/ai.js`
- Create: `front/Claude4-8frontWeb/src/api/author.js`
- Create: `front/Claude4-8frontWeb/src/api/admin.js`

**Interfaces:**
- Consumes: `@/utils/request`（default + `putForm`）、`@/utils/auth`（`getReaderToken`，AI SSE 用）。
- Produces: 见下方各函数。`ai.js` 额外导出 `getConversationId()`、`newConversation()`、`streamChat(message, conversationId, {onChunk,onDone,onError})`。

- [ ] **Step 1: 实现 user.js**

```js
import request, { putForm } from '@/utils/request'
export const register = (dto) => request.post('/front/user/register', dto)
export const login = (dto) => request.post('/front/user/login', dto)
export const getUserInfo = () => request.get('/front/user')
export const updateUserInfo = (dto) => request.put('/front/user', dto)
export const submitFeedback = (content) =>
  request.post('/front/user/feedback', content, { headers: { 'Content-Type': 'application/json' } })
export const listFeedbacks = (params) => request.get('/front/user/feedbacks', { params })
export const deleteFeedback = (id) => request.delete(`/front/user/feedback/${id}`)
export const comment = (dto) => request.post('/front/user/comment', dto)
export const updateComment = (id, content) => putForm(`/front/user/comment/${id}`, content)
export const deleteComment = (id) => request.delete(`/front/user/comment/${id}`)
export const getBookshelfStatus = (bookId) =>
  request.get('/front/user/bookshelf_status', { params: { bookId } })
export const addBookshelf = (bookId) => request.post(`/front/user/bookshelf/${bookId}`)
export const listBookshelf = (params) => request.get('/front/user/bookshelf', { params })
export const removeBookshelf = (bookId) => request.delete(`/front/user/bookshelf/${bookId}`)
export const listComments = (params) => request.get('/front/user/comments', { params })
```

> 注：`submitFeedback` 后端用 `@RequestBody String content` 接收，且校验 5–512 字。前端直接把字符串作为 body 发送，Content-Type 保持 json。

- [ ] **Step 2: 实现 ai.js（含 SSE 流式）**

```js
import request from '@/utils/request'
import { getReaderToken } from '@/utils/auth'

const CONV_KEY = 'ai_companion_conversation_id'

function genId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}
export function getConversationId() {
  let id = localStorage.getItem(CONV_KEY)
  if (!id) { id = genId(); localStorage.setItem(CONV_KEY, id) }
  return id
}
export function newConversation() {
  const id = genId(); localStorage.setItem(CONV_KEY, id); return id
}

export const chat = (message, conversationId) =>
  request.get('/front/ai/companion/chat', { params: { message, conversationId } })
export const clearMemory = (conversationId) =>
  request.delete('/front/ai/companion/chat/memory', { params: { conversationId } })
export const getChatHistory = (conversationId) =>
  request.get('/front/ai/companion/chat/history', { params: { conversationId } })
export const recommendSimilar = (count = 4) =>
  request.get('/front/ai/companion/recommend', { params: { count } })

export async function streamChat(message, conversationId, { onChunk, onDone, onError } = {}) {
  const token = getReaderToken()
  const base = import.meta.env.VITE_BASE_API_URL || '/api'
  const url = `${base}/front/ai/companion/chat/stream`
    + `?message=${encodeURIComponent(message)}`
    + `&conversationId=${encodeURIComponent(conversationId || '')}`
  try {
    const resp = await fetch(url, {
      method: 'GET',
      headers: { Authorization: token || '', Accept: 'text/event-stream' }
    })
    if (!resp.ok) {
      if (resp.status === 401 || resp.status === 403) throw new Error('登录状态已失效，请重新登录')
      throw new Error(`请求失败 (${resp.status})`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) { if (buffer.trim()) consume(buffer, onChunk); onDone && onDone(); break }
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const p of parts) consume(p, onChunk)
    }
  } catch (e) { onError && onError(e) }
}
function consume(eventStr, onChunk) {
  if (!eventStr) return
  const dataLines = []
  for (const line of eventStr.split('\n')) {
    const t = line.trim()
    if (t.startsWith('data:')) dataLines.push(t.slice(5).replace(/^\s/, ''))
  }
  if (dataLines.length && onChunk) onChunk(dataLines.join('\n'))
}
```

- [ ] **Step 3: 实现 author.js（第 2 期页面用，本期建好）**

```js
import request from '@/utils/request'
export const getAuthorStatus = () => request.get('/author/status')
export const authorRegister = (dto) => request.post('/author/register', dto)
export const getAuthorInfo = () => request.get('/author/info')
export const listAuthorBooks = (params) => request.get('/author/books', { params })
export const publishBook = (dto) => request.post('/author/book', dto)
export const listAuthorChapters = (bookId, params) =>
  request.get(`/author/book/chapters/${bookId}`, { params })
export const publishChapter = (bookId, dto) => request.post(`/author/book/chapter/${bookId}`, dto)
export const getAuthorChapter = (chapterId) => request.get(`/author/book/chapter/${chapterId}`)
export const updateAuthorChapter = (chapterId, dto) =>
  request.put(`/author/book/chapter/${chapterId}`, dto)
export const deleteAuthorChapter = (chapterId) => request.delete(`/author/book/chapter/${chapterId}`)
```

- [ ] **Step 4: 实现 admin.js（第 3 期页面用，本期建好）**

```js
import request from '@/utils/request'
const P = '/admin'
export const adminLogin = (dto) => request.post(`${P}/login`, dto)
export const adminInfo = () => request.get(`${P}/info`)
export const adminMenus = () => request.get(`${P}/menus`)
export const adminDashboard = () => request.get(`${P}/dashboard`)
export const listUsers = (params) => request.get(`${P}/user/page`, { params })
export const getUser = (id) => request.get(`${P}/user/${id}`)
export const updateUserStatus = (id, status) => request.put(`${P}/user/${id}/status`, { status })
export const listAdminBooks = (params) => request.get(`${P}/book/page`, { params })
export const updateBookStatus = (id, status) => request.put(`${P}/book/${id}/status`, { status })
export const listAdminCategories = (params) => request.get(`${P}/book/category/page`, { params })
export const saveCategory = (dto) => request.post(`${P}/book/category`, dto)
export const updateCategory = (id, dto) => request.put(`${P}/book/category/${id}`, dto)
export const deleteCategory = (id) => request.delete(`${P}/book/category/${id}`)
export const listAdminComments = (params) => request.get(`${P}/book/comment/page`, { params })
export const auditComment = (id, auditStatus) =>
  request.put(`${P}/book/comment/${id}/audit`, { auditStatus })
export const deleteAdminComment = (id) => request.delete(`${P}/book/comment/${id}`)
export const listAdminAuthors = (params) => request.get(`${P}/author/page`, { params })
export const getAdminAuthor = (id) => request.get(`${P}/author/${id}`)
export const updateAuthorStatus = (id, status) => request.put(`${P}/author/${id}/status`, { status })
export const listHomeBooksAdmin = (params) => request.get(`${P}/home/book/page`, { params })
export const saveHomeBook = (dto) => request.post(`${P}/home/book`, dto)
export const updateHomeBook = (id, dto) => request.put(`${P}/home/book/${id}`, dto)
export const deleteHomeBook = (id) => request.delete(`${P}/home/book/${id}`)
export const listFriendLinksAdmin = (params) => request.get(`${P}/home/friend-link/page`, { params })
export const saveFriendLink = (dto) => request.post(`${P}/home/friend-link`, dto)
export const updateFriendLink = (id, dto) => request.put(`${P}/home/friend-link/${id}`, dto)
export const deleteFriendLink = (id) => request.delete(`${P}/home/friend-link/${id}`)
export const listAdminNews = (params) => request.get(`${P}/news/page`, { params })
export const saveNews = (dto) => request.post(`${P}/news`, dto)
export const updateNews = (id, dto) => request.put(`${P}/news/${id}`, dto)
export const deleteNews = (id) => request.delete(`${P}/news/${id}`)
```

- [ ] **Step 5: 校验文件齐全**

Run: `ls "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb/src/api"`
Expected: 列出 9 个文件：admin.js author.js ai.js book.js home.js news.js resource.js search.js user.js。

- [ ] **Step 6: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/api/user.js front/Claude4-8frontWeb/src/api/ai.js front/Claude4-8frontWeb/src/api/author.js front/Claude4-8frontWeb/src/api/admin.js && git commit -m "feat(c48-web): 会员/AI/作家/管理员 API 层(含 SSE 流式)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Pinia stores（user + category，TDD user store）

**Files:**
- Create: `front/Claude4-8frontWeb/src/stores/user.js`
- Create: `front/Claude4-8frontWeb/src/stores/category.js`
- Test: `front/Claude4-8frontWeb/tests/store-user.spec.js`

**Interfaces:**
- Consumes: `@/api/user`（login/getUserInfo）、`@/api/book`（listCategory）、`@/utils/auth`。
- Produces:
  - `useUserStore`：state `{ token, nickName, uid, userInfo }`；getter `isLoggedIn`；actions `loginAction(dto)`、`fetchUserInfo()`、`logout()`、`hydrate()`。
  - `useCategoryStore`：state `{ map: {0:[],1:[]} }`；action `load(workDirection)`（缓存命中则跳过请求）。

- [ ] **Step 1: 写失败测试（mock api）**

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/user', () => ({
  login: vi.fn(() => Promise.resolve({ data: { uid: 5, nickName: '墨客', token: 'T1' } })),
  getUserInfo: vi.fn(() => Promise.resolve({ data: { nickName: '墨客', userPhoto: '/a.png', userSex: 1 } }))
}))

import { useUserStore } from '@/stores/user'
import { getReaderToken } from '@/utils/auth'

describe('user store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('loginAction stores token/nick/uid and sets reader slot', async () => {
    const s = useUserStore()
    await s.loginAction({ username: '18888888888', password: '123456' })
    expect(s.token).toBe('T1')
    expect(s.nickName).toBe('墨客')
    expect(s.isLoggedIn).toBe(true)
    expect(getReaderToken()).toBe('T1')
  })

  it('logout clears everything', async () => {
    const s = useUserStore()
    await s.loginAction({ username: '18888888888', password: '123456' })
    s.logout()
    expect(s.token).toBeNull()
    expect(s.isLoggedIn).toBe(false)
    expect(getReaderToken()).toBeNull()
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/store-user.spec.js`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现 stores/user.js**

```js
import { defineStore } from 'pinia'
import { login, getUserInfo } from '@/api/user'
import {
  getReaderToken, setReaderToken, removeReaderToken,
  getNickName, setNickName, removeNickName,
  getUid, setUid, removeUid
} from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getReaderToken(),
    nickName: getNickName(),
    uid: getUid(),
    userInfo: null
  }),
  getters: { isLoggedIn: (s) => !!s.token },
  actions: {
    async loginAction(dto) {
      const { data } = await login(dto)
      this.token = data.token
      this.nickName = data.nickName
      this.uid = String(data.uid)
      setReaderToken(data.token); setNickName(data.nickName); setUid(String(data.uid))
      return data
    },
    async fetchUserInfo() {
      const { data } = await getUserInfo()
      this.userInfo = data
      if (data && data.nickName) { this.nickName = data.nickName; setNickName(data.nickName) }
      return data
    },
    logout() {
      this.token = null; this.nickName = null; this.uid = null; this.userInfo = null
      removeReaderToken(); removeNickName(); removeUid()
    },
    hydrate() {
      this.token = getReaderToken(); this.nickName = getNickName(); this.uid = getUid()
    }
  }
})
```

- [ ] **Step 4: 实现 stores/category.js**

```js
import { defineStore } from 'pinia'
import { listCategory } from '@/api/book'

export const useCategoryStore = defineStore('category', {
  state: () => ({ map: { 0: [], 1: [] } }),
  actions: {
    async load(workDirection = 0) {
      if (this.map[workDirection] && this.map[workDirection].length) return this.map[workDirection]
      const { data } = await listCategory(workDirection)
      this.map[workDirection] = data || []
      return this.map[workDirection]
    }
  }
})
```

- [ ] **Step 5: 运行确认通过**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run tests/store-user.spec.js`
Expected: PASS（2 个用例）。

- [ ] **Step 6: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/stores front/Claude4-8frontWeb/tests/store-user.spec.js && git commit -m "feat(c48-web): Pinia 用户与分类 store

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: 路由与守卫

**Files:**
- Create: `front/Claude4-8frontWeb/src/router/index.js`

**Interfaces:**
- Consumes: `@/utils/auth` 的 `getReaderToken`。
- Produces: default export `router`（`createWebHistory`）。读者门户路由 + `meta.requiresAuth` 守卫；作家/管理路由本期留空注释占位（第 2/3 期补）。
- 说明：所有 view 组件懒加载。本期先建占位 view（Task 12 起逐个实现），路由指向的文件必须存在——故本任务同时创建**最小占位 .vue**（仅 `<template><div/></template>`），后续任务替换内容。

- [ ] **Step 1: 创建全部占位 view 文件**

在 `src/views/portal/` 下创建以下文件，每个内容均为：
```vue
<template><div class="container" style="padding:24px">Coming soon</div></template>
<script setup></script>
```
文件清单：`Home.vue Search.vue BookClass.vue BookRank.vue BookDetail.vue ChapterList.vue BookReader.vue News.vue Login.vue Register.vue`
以及 `src/views/portal/user/` 下：`Bookshelf.vue MyComments.vue Feedback.vue Profile.vue`
布局占位 `src/layouts/PortalLayout.vue`：
```vue
<template><div><router-view /></div></template>
<script setup></script>
```

- [ ] **Step 2: 创建 router/index.js**

```js
import { createRouter, createWebHistory } from 'vue-router'
import { getReaderToken } from '@/utils/auth'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/PortalLayout.vue'),
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'home', component: () => import('@/views/portal/Home.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/portal/Search.vue') },
      { path: 'category', name: 'category', component: () => import('@/views/portal/BookClass.vue') },
      { path: 'rank', name: 'rank', component: () => import('@/views/portal/BookRank.vue') },
      { path: 'book/:id', name: 'bookDetail', component: () => import('@/views/portal/BookDetail.vue') },
      { path: 'book/:id/catalog', name: 'chapterList', component: () => import('@/views/portal/ChapterList.vue') },
      { path: 'news/:id', name: 'news', component: () => import('@/views/portal/News.vue') },
      { path: 'login', name: 'login', component: () => import('@/views/portal/Login.vue') },
      { path: 'register', name: 'register', component: () => import('@/views/portal/Register.vue') },
      { path: 'user/bookshelf', name: 'bookshelf', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Bookshelf.vue') },
      { path: 'user/comments', name: 'myComments', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/MyComments.vue') },
      { path: 'user/feedback', name: 'feedback', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Feedback.vue') },
      { path: 'user/profile', name: 'profile', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Profile.vue') }
    ]
  },
  // 阅读页独立布局（无门户导航），沉浸式阅读
  { path: '/read/:chapterId', name: 'reader', component: () => import('@/views/portal/BookReader.vue') }
  // 第 2 期：/author/*   第 3 期：/admin/*
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !getReaderToken()) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
```

- [ ] **Step 3: 首次可启动验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && timeout 25 npm run dev 2>&1 | head -15 || true`
Expected: Vite 打印 `Local: http://localhost:2048/`，无编译错误（占位页可访问）。

- [ ] **Step 4: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -15`
Expected: `built in ...`，dist 生成，无报错。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/router front/Claude4-8frontWeb/src/views front/Claude4-8frontWeb/src/layouts && git commit -m "feat(c48-web): 路由与登录守卫 + 页面占位

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: 通用组件（BookCard / Pagination / EmptyBlock / BackTop）

**Files:**
- Create: `front/Claude4-8frontWeb/src/components/common/BookCard.vue`
- Create: `front/Claude4-8frontWeb/src/components/common/Pagination.vue`
- Create: `front/Claude4-8frontWeb/src/components/common/EmptyBlock.vue`
- Create: `front/Claude4-8frontWeb/src/components/common/BackTop.vue`

**Interfaces:**
- Produces:
  - `BookCard` props: `book`(Object，含 id/picUrl/bookName/authorName/bookDesc/bookStatus?/wordCount?)、`horizontal`(Boolean 默认 false)。点击跳 `/book/:id`。
  - `Pagination` props: `total`(Number)、`pageNum`(Number)、`pageSize`(Number)；emit `update:pageNum`。
  - `EmptyBlock` props: `text`(String 默认 "暂无数据")。
  - `BackTop`: 无 props，滚动 > 300 显示，点击回顶。
- Consumes: `@/utils/format`（formatWordCount/bookStatusText）、`import.meta.env.VITE_BASE_IMG_URL` 拼封面。

- [ ] **Step 1: BookCard.vue**

```vue
<template>
  <div class="book-card hover-lift" :class="{ horizontal }" @click="goDetail">
    <div class="cover">
      <img :src="coverUrl" :alt="book.bookName" @error="onImgError" />
    </div>
    <div class="info">
      <h4 class="book-name text-ellipsis">{{ book.bookName }}</h4>
      <p class="author text-ellipsis">{{ book.authorName }}</p>
      <p v-if="horizontal" class="desc text-clamp-2">{{ book.bookDesc }}</p>
      <div v-if="horizontal" class="meta">
        <span class="tag">{{ statusText }}</span>
        <span v-if="book.wordCount">{{ words }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatWordCount, bookStatusText } from '@/utils/format'

const props = defineProps({
  book: { type: Object, required: true },
  horizontal: { type: Boolean, default: false }
})
const router = useRouter()
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="160"><rect width="120" height="160" fill="%23eae4db"/><text x="60" y="80" font-size="12" fill="%239a9a9a" text-anchor="middle">封面</text></svg>'
const coverUrl = computed(() => props.book.picUrl ? IMG + props.book.picUrl : FALLBACK)
const statusText = computed(() => bookStatusText(props.book.bookStatus))
const words = computed(() => formatWordCount(props.book.wordCount))
const goDetail = () => router.push(`/book/${props.book.id ?? props.book.bookId}`)
const onImgError = (e) => { e.target.src = FALLBACK }
</script>

<style scoped>
.book-card { display: flex; flex-direction: column; cursor: pointer; background: var(--surface); border-radius: var(--radius); overflow: hidden; border: 1px solid var(--border); }
.book-card .cover { aspect-ratio: 3/4; overflow: hidden; }
.book-card .cover img { width: 100%; height: 100%; object-fit: cover; }
.book-card .info { padding: var(--sp-3); }
.book-name { font-size: 15px; margin-bottom: 4px; }
.author { color: var(--text-2); font-size: 12px; }
.book-card.horizontal { flex-direction: row; }
.book-card.horizontal .cover { width: 120px; flex: 0 0 120px; }
.desc { color: var(--text-2); font-size: 13px; margin: 8px 0; }
.meta { display: flex; gap: 12px; font-size: 12px; color: var(--text-3); }
.meta .tag { color: var(--brand); }
</style>
```

- [ ] **Step 2: Pagination.vue**

```vue
<template>
  <div v-if="total > 0" class="pagination-wrap">
    <el-pagination
      background layout="prev, pager, next"
      :total="total" :current-page="pageNum" :page-size="pageSize"
      @current-change="onChange" />
  </div>
</template>

<script setup>
const props = defineProps({
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 }
})
const emit = defineEmits(['update:pageNum'])
const onChange = (p) => emit('update:pageNum', p)
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: center; padding: var(--sp-5) 0; }
</style>
```

- [ ] **Step 3: EmptyBlock.vue**

```vue
<template>
  <div class="empty-block">
    <el-icon :size="48" color="#c9c2b8"><Document /></el-icon>
    <p>{{ text }}</p>
  </div>
</template>

<script setup>
defineProps({ text: { type: String, default: '暂无数据' } })
</script>

<style scoped>
.empty-block { text-align: center; padding: 48px 0; color: var(--text-3); }
.empty-block p { margin-top: 12px; }
</style>
```

- [ ] **Step 4: BackTop.vue**

```vue
<template>
  <transition name="fade">
    <div v-show="show" class="back-top" @click="toTop">
      <el-icon :size="20"><Top /></el-icon>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
const show = ref(false)
const onScroll = () => { show.value = window.scrollY > 300 }
const toTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
onMounted(() => window.addEventListener('scroll', onScroll))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.back-top { position: fixed; right: 32px; bottom: 96px; width: 44px; height: 44px; border-radius: 50%; background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-md); display: flex; align-items: center; justify-content: center; cursor: pointer; color: var(--brand); z-index: 50; }
</style>
```

- [ ] **Step 5: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 6: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/components/common && git commit -m "feat(c48-web): 通用组件 BookCard/Pagination/EmptyBlock/BackTop

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: 门户布局（PortalLayout + AppNavbar + AppFooter）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/layouts/PortalLayout.vue`
- Create: `front/Claude4-8frontWeb/src/components/common/AppNavbar.vue`
- Create: `front/Claude4-8frontWeb/src/components/common/AppFooter.vue`

**Interfaces:**
- Consumes: `useUserStore`（登录态/昵称/登出）、vue-router。
- Produces: 顶部导航（Logo「墨书」+ 导航链接 首页/分类/榜单 + 搜索框 + 登录态区）；底部信息；`<router-view>` 出口 + 挂 `BackTop`。

- [ ] **Step 1: AppNavbar.vue**

```vue
<template>
  <header class="navbar">
    <div class="container nav-inner">
      <router-link to="/home" class="logo">
        <span class="logo-mark">墨</span><span class="logo-text">墨书</span>
      </router-link>
      <nav class="nav-links">
        <router-link to="/home">首页</router-link>
        <router-link to="/category">分类</router-link>
        <router-link to="/rank">榜单</router-link>
      </nav>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索书名 / 作者" @keyup.enter="doSearch" clearable>
          <template #suffix><el-icon @click="doSearch" style="cursor:pointer"><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="user-area">
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="onCommand">
            <span class="user-trigger">{{ userStore.nickName || '我的' }}<el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="bookshelf">我的书架</el-dropdown-item>
                <el-dropdown-item command="comments">我的评论</el-dropdown-item>
                <el-dropdown-item command="feedback">意见反馈</el-dropdown-item>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-text">登录</router-link>
          <router-link to="/register" class="btn-brand">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const doSearch = () => router.push({ name: 'search', query: { keyword: keyword.value } })
const onCommand = (cmd) => {
  if (cmd === 'logout') { userStore.logout(); router.push('/home'); return }
  const map = { bookshelf: '/user/bookshelf', comments: '/user/comments', feedback: '/user/feedback', profile: '/user/profile' }
  router.push(map[cmd])
}
</script>

<style scoped>
.navbar { background: var(--surface); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 40; }
.nav-inner { display: flex; align-items: center; gap: var(--sp-5); height: 64px; }
.logo { display: flex; align-items: center; gap: 8px; }
.logo-mark { width: 34px; height: 34px; background: var(--brand); color: #fff; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 20px; }
.logo-text { font-family: var(--font-serif); font-size: 22px; font-weight: 700; }
.nav-links { display: flex; gap: var(--sp-4); }
.nav-links a { color: var(--text-2); padding: 4px 0; }
.nav-links a.router-link-active { color: var(--brand); border-bottom: 2px solid var(--brand); }
.search-box { flex: 1; max-width: 360px; }
.user-area { display: flex; align-items: center; gap: var(--sp-3); }
.user-trigger { display: flex; align-items: center; gap: 4px; cursor: pointer; color: var(--text); }
.btn-text { color: var(--text-2); }
.btn-brand { background: var(--brand); color: #fff; padding: 6px 16px; border-radius: var(--radius-sm); }
</style>
```

- [ ] **Step 2: AppFooter.vue**

```vue
<template>
  <footer class="footer">
    <div class="container">
      <p class="brand"><span class="logo-mark">墨</span> 墨书 · 在线书城</p>
      <p class="sub">基于 Spring Boot 3 + Vue 3 的小说阅读平台演示 · 内含 AI 智能书童</p>
      <p class="copy">© 2026 墨书 Demo. 仅供学习交流。</p>
    </div>
  </footer>
</template>

<script setup></script>

<style scoped>
.footer { background: #2b241f; color: #cbbfae; margin-top: var(--sp-6); padding: var(--sp-6) 0; text-align: center; }
.footer .brand { font-family: var(--font-serif); font-size: 18px; color: #fff; }
.footer .logo-mark { color: var(--brand); }
.footer .sub { margin: 8px 0; font-size: 13px; }
.footer .copy { font-size: 12px; color: #8a7f70; }
</style>
```

- [ ] **Step 3: PortalLayout.vue（替换占位）**

```vue
<template>
  <div class="portal">
    <AppNavbar />
    <main class="portal-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in"><component :is="Component" /></transition>
      </router-view>
    </main>
    <AppFooter />
    <BackTop />
  </div>
</template>

<script setup>
import AppNavbar from '@/components/common/AppNavbar.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import BackTop from '@/components/common/BackTop.vue'
</script>

<style scoped>
.portal { min-height: 100vh; display: flex; flex-direction: column; }
.portal-main { flex: 1; }
</style>
```

- [ ] **Step 4: dev 手动验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。手动 `npm run dev` 打开 `http://localhost:2048`，可见顶部「墨书」导航、搜索框、登录/注册按钮，底部页脚。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/layouts front/Claude4-8frontWeb/src/components/common/AppNavbar.vue front/Claude4-8frontWeb/src/components/common/AppFooter.vue && git commit -m "feat(c48-web): 门户布局与顶部导航/页脚

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 12: 首页（Home + 首页子组件）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/Home.vue`
- Create: `front/Claude4-8frontWeb/src/components/home/HomeRecommend.vue`
- Create: `front/Claude4-8frontWeb/src/components/home/RankPanel.vue`
- Create: `front/Claude4-8frontWeb/src/components/home/LatestNews.vue`
- Create: `front/Claude4-8frontWeb/src/components/home/FriendLinks.vue`

**Interfaces:**
- Consumes: `@/api/home`（listHomeBooks/listHomeFriendLinks）、`@/api/book`（三榜 + rec）、`@/api/news`（listLatestNews）、`BookCard`。
- `HomeBookRespDto.type`：0-轮播 1-顶部栏 2-本周强推 3-热门推荐 4-精品推荐 → 按 type 分组展示。
- `RankPanel` props: `title`(String)、`books`(Array)。

- [ ] **Step 1: HomeRecommend.vue（按 type 分组）**

```vue
<template>
  <div class="home-recommend">
    <div v-if="carousel.length" class="carousel-row">
      <el-carousel height="260px" :interval="4000">
        <el-carousel-item v-for="b in carousel" :key="b.bookId" @click="go(b.bookId)">
          <div class="slide">
            <img :src="cover(b.picUrl)" @error="onErr" />
            <div class="slide-info">
              <h3>{{ b.bookName }}</h3>
              <p>{{ b.authorName }}</p>
              <p class="text-clamp-2">{{ b.bookDesc }}</p>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </div>
    <div v-for="grp in groups" :key="grp.type" class="rec-group">
      <h3 class="section-title">{{ grp.label }}</h3>
      <div class="rec-grid">
        <BookCard v-for="b in grp.items" :key="b.bookId" :book="toBook(b)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BookCard from '@/components/common/BookCard.vue'

const props = defineProps({ books: { type: Array, default: () => [] } })
const router = useRouter()
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="260"><rect width="200" height="260" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const carousel = computed(() => props.books.filter(b => b.type === 0))
const LABELS = { 2: '本周强推', 3: '热门推荐', 4: '精品推荐' }
const groups = computed(() =>
  [2, 3, 4].map(t => ({ type: t, label: LABELS[t], items: props.books.filter(b => b.type === t) }))
           .filter(g => g.items.length))
const toBook = (b) => ({ id: b.bookId, picUrl: b.picUrl, bookName: b.bookName, authorName: b.authorName, bookDesc: b.bookDesc })
const go = (id) => router.push(`/book/${id}`)
</script>

<style scoped>
.slide { position: relative; height: 260px; cursor: pointer; }
.slide img { width: 100%; height: 100%; object-fit: cover; filter: brightness(0.6); }
.slide-info { position: absolute; left: 32px; bottom: 32px; color: #fff; max-width: 60%; }
.slide-info h3 { font-size: 26px; color: #fff; }
.rec-group { margin-top: var(--sp-6); }
.rec-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--sp-4); }
@media (max-width: 900px) { .rec-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
```

- [ ] **Step 2: RankPanel.vue**

```vue
<template>
  <div class="rank-panel card">
    <h4 class="rank-title">{{ title }}</h4>
    <ol class="rank-list">
      <li v-for="(b, i) in books" :key="b.id" @click="go(b.id)">
        <span class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <span class="rank-name text-ellipsis">{{ b.bookName }}</span>
      </li>
    </ol>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
defineProps({ title: String, books: { type: Array, default: () => [] } })
const router = useRouter()
const go = (id) => router.push(`/book/${id}`)
</script>

<style scoped>
.rank-panel { padding: var(--sp-4); }
.rank-title { font-family: var(--font-serif); font-size: 17px; margin-bottom: var(--sp-3); border-left: 4px solid var(--brand); padding-left: 10px; }
.rank-list { list-style: none; margin: 0; padding: 0; }
.rank-list li { display: flex; align-items: center; gap: 10px; padding: 7px 0; cursor: pointer; }
.rank-list li:hover .rank-name { color: var(--brand); }
.rank-no { width: 20px; text-align: center; color: var(--text-3); font-weight: 600; }
.rank-no.top { color: var(--brand); }
.rank-name { flex: 1; font-size: 14px; }
</style>
```

- [ ] **Step 3: LatestNews.vue 与 FriendLinks.vue**

`LatestNews.vue`:
```vue
<template>
  <div class="latest-news card">
    <h4 class="ln-title">最新资讯</h4>
    <ul>
      <li v-for="n in news" :key="n.id" @click="go(n.id)">
        <span class="dot"></span><span class="text-ellipsis">{{ n.title }}</span>
      </li>
    </ul>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'
defineProps({ news: { type: Array, default: () => [] } })
const router = useRouter()
const go = (id) => router.push(`/news/${id}`)
</script>
<style scoped>
.latest-news { padding: var(--sp-4); }
.ln-title { font-family: var(--font-serif); font-size: 17px; margin-bottom: var(--sp-3); border-left: 4px solid var(--brand); padding-left: 10px; }
.latest-news ul { list-style: none; margin: 0; padding: 0; }
.latest-news li { display: flex; align-items: center; gap: 8px; padding: 6px 0; cursor: pointer; font-size: 14px; color: var(--text-2); }
.latest-news li:hover { color: var(--brand); }
.dot { width: 5px; height: 5px; border-radius: 50%; background: var(--brand); flex: 0 0 5px; }
</style>
```

`FriendLinks.vue`:
```vue
<template>
  <div v-if="links.length" class="friend-links container">
    <span class="fl-label">友情链接：</span>
    <a v-for="(l, i) in links" :key="i" :href="l.linkUrl" target="_blank" rel="noopener">{{ l.linkName }}</a>
  </div>
</template>
<script setup>
defineProps({ links: { type: Array, default: () => [] } })
</script>
<style scoped>
.friend-links { padding: var(--sp-4) var(--sp-4) 0; display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
.fl-label { color: var(--text-3); font-size: 13px; }
.friend-links a { color: var(--text-2); font-size: 13px; }
.friend-links a:hover { color: var(--brand); }
</style>
```

- [ ] **Step 4: Home.vue（组装 + 拉数据）**

```vue
<template>
  <div class="home">
    <div class="container home-layout">
      <div class="home-main">
        <HomeRecommend :books="homeBooks" />
      </div>
      <aside class="home-side">
        <RankPanel title="点击榜" :books="visitRank" />
        <RankPanel title="新书榜" :books="newestRank" />
        <RankPanel title="更新榜" :books="updateRank" />
        <LatestNews :news="news" />
      </aside>
    </div>
    <FriendLinks :links="links" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listHomeBooks, listHomeFriendLinks } from '@/api/home'
import { listVisitRankBooks, listNewestRankBooks, listUpdateRankBooks } from '@/api/book'
import { listLatestNews } from '@/api/news'
import HomeRecommend from '@/components/home/HomeRecommend.vue'
import RankPanel from '@/components/home/RankPanel.vue'
import LatestNews from '@/components/home/LatestNews.vue'
import FriendLinks from '@/components/home/FriendLinks.vue'

const homeBooks = ref([]), visitRank = ref([]), newestRank = ref([])
const updateRank = ref([]), news = ref([]), links = ref([])

async function safe(fn, target) {
  try { const { data } = await fn(); target.value = data || [] } catch (e) { /* 拦截器已提示 */ }
}
onMounted(() => {
  safe(listHomeBooks, homeBooks)
  safe(listVisitRankBooks, visitRank)
  safe(listNewestRankBooks, newestRank)
  safe(listUpdateRankBooks, updateRank)
  safe(listLatestNews, news)
  safe(listHomeFriendLinks, links)
})
</script>

<style scoped>
.home-layout { display: grid; grid-template-columns: 1fr 300px; gap: var(--sp-5); padding-top: var(--sp-5); }
.home-side { display: flex; flex-direction: column; gap: var(--sp-4); }
@media (max-width: 900px) { .home-layout { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 5: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 6: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/Home.vue front/Claude4-8frontWeb/src/components/home && git commit -m "feat(c48-web): 首页(推荐分组/三榜/资讯/友链)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 13: 搜索页（Search）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/Search.vue`

**Interfaces:**
- Consumes: `@/api/search`（searchBooks）、`@/stores/category`（分类筛选）、`BookCard`(horizontal)、`Pagination`、`EmptyBlock`。
- 读路由 query `keyword` 初始化搜索；筛选项对齐 `BookSearchReqDto`。

- [ ] **Step 1: Search.vue**

```vue
<template>
  <div class="container search-page">
    <div class="filter-bar card">
      <el-input v-model="cond.keyword" placeholder="书名 / 作者关键字" class="kw" @keyup.enter="doSearch" clearable />
      <el-select v-model="cond.workDirection" placeholder="频道" clearable @change="onDirChange" style="width:120px">
        <el-option label="男频" :value="0" /><el-option label="女频" :value="1" />
      </el-select>
      <el-select v-model="cond.categoryId" placeholder="分类" clearable style="width:140px">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="cond.bookStatus" placeholder="状态" clearable style="width:120px">
        <el-option label="连载中" :value="0" /><el-option label="已完结" :value="1" />
      </el-select>
      <el-select v-model="cond.isVip" placeholder="收费" clearable style="width:120px">
        <el-option label="免费" :value="0" /><el-option label="收费" :value="1" />
      </el-select>
      <el-button type="primary" @click="doSearch">搜索</el-button>
    </div>

    <div v-loading="loading" class="result">
      <template v-if="list.length">
        <div class="result-list">
          <BookCard v-for="b in list" :key="b.id" :book="b" horizontal />
        </div>
        <Pagination :total="total" :page-num="cond.pageNum" :page-size="cond.pageSize"
                    @update:pageNum="onPage" />
      </template>
      <EmptyBlock v-else-if="!loading" text="没有找到符合条件的书籍" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchBooks } from '@/api/search'
import { useCategoryStore } from '@/stores/category'
import BookCard from '@/components/common/BookCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'

const route = useRoute()
const categoryStore = useCategoryStore()
const categories = ref([])
const list = ref([]), total = ref(0), loading = ref(false)
const cond = reactive({
  keyword: route.query.keyword || '',
  workDirection: null, categoryId: null, bookStatus: null, isVip: null,
  pageNum: 1, pageSize: 10
})

async function onDirChange(v) {
  cond.categoryId = null
  categories.value = v === null || v === undefined ? [] : await categoryStore.load(v)
}
async function doSearch() {
  cond.pageNum = 1
  await load()
}
async function onPage(p) { cond.pageNum = p; await load() }
async function load() {
  loading.value = true
  try {
    const params = {}
    Object.entries(cond).forEach(([k, v]) => { if (v !== null && v !== '' && v !== undefined) params[k] = v })
    const { data } = await searchBooks(params)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.search-page { padding-top: var(--sp-5); }
.filter-bar { display: flex; gap: var(--sp-3); padding: var(--sp-4); flex-wrap: wrap; align-items: center; }
.filter-bar .kw { flex: 1; min-width: 220px; }
.result-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-4); margin-top: var(--sp-4); }
@media (max-width: 760px) { .result-list { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 3: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/Search.vue && git commit -m "feat(c48-web): 搜索页(多条件筛选+分页)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 14: 分类页（BookClass）与榜单页（BookRank）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/BookClass.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/BookRank.vue`

**Interfaces:**
- BookClass Consumes: `useCategoryStore`；点击分类跳 `/search?workDirection=&categoryId=`。
- BookRank Consumes: 三榜 api + `BookCard`；Tab 切换点击/新书/更新榜。

- [ ] **Step 1: BookClass.vue**

```vue
<template>
  <div class="container class-page">
    <h3 class="section-title">小说分类</h3>
    <el-tabs v-model="dir" @tab-change="loadCats">
      <el-tab-pane label="男频" :name="0" />
      <el-tab-pane label="女频" :name="1" />
    </el-tabs>
    <div v-loading="loading" class="cat-grid">
      <div v-for="c in cats" :key="c.id" class="cat-item card hover-lift" @click="go(c)">
        <span class="cat-name">{{ c.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCategoryStore } from '@/stores/category'
const router = useRouter()
const store = useCategoryStore()
const dir = ref(0), cats = ref([]), loading = ref(false)
async function loadCats() {
  loading.value = true
  try { cats.value = await store.load(dir.value) } finally { loading.value = false }
}
const go = (c) => router.push({ name: 'search', query: { workDirection: dir.value, categoryId: c.id } })
onMounted(loadCats)
</script>

<style scoped>
.class-page { padding-top: var(--sp-5); }
.cat-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--sp-3); margin-top: var(--sp-4); }
.cat-item { padding: 20px; text-align: center; cursor: pointer; }
.cat-name { font-family: var(--font-serif); font-size: 16px; }
@media (max-width: 760px) { .cat-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
```

> 注：Search.vue 的 onMounted 已处理 query 中 keyword；分类跳转带的 workDirection/categoryId 会被 `load()` 组进 params（reactive 初值需接收 route.query，扩展 cond 初始化：`categoryId: route.query.categoryId ? Number(route.query.categoryId) : null`，`workDirection` 同理）。执行本任务时同步补 Search.vue 的 cond 初始化读取这两个 query。

- [ ] **Step 2: 回填 Search.vue 的 query 初始化**

将 Search.vue 中 `cond` 初始化改为：
```js
const cond = reactive({
  keyword: route.query.keyword || '',
  workDirection: route.query.workDirection !== undefined ? Number(route.query.workDirection) : null,
  categoryId: route.query.categoryId !== undefined ? Number(route.query.categoryId) : null,
  bookStatus: null, isVip: null, pageNum: 1, pageSize: 10
})
```

- [ ] **Step 3: BookRank.vue**

```vue
<template>
  <div class="container rank-page">
    <h3 class="section-title">小说榜单</h3>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="点击榜" name="visit" />
      <el-tab-pane label="新书榜" name="newest" />
      <el-tab-pane label="更新榜" name="update" />
    </el-tabs>
    <div v-loading="loading" class="rank-books">
      <div v-for="(b, i) in list" :key="b.id" class="rank-row card hover-lift" @click="go(b.id)">
        <span class="no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <img :src="cover(b.picUrl)" @error="onErr" />
        <div class="meta">
          <h4>{{ b.bookName }}</h4>
          <p class="author">{{ b.authorName }}</p>
          <p class="desc text-clamp-2">{{ b.bookDesc }}</p>
          <p class="last">最新：{{ b.lastChapterName || '-' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listVisitRankBooks, listNewestRankBooks, listUpdateRankBooks } from '@/api/book'
const router = useRouter()
const tab = ref('visit'), list = ref([]), loading = ref(false)
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="90" height="120"><rect width="90" height="120" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const FN = { visit: listVisitRankBooks, newest: listNewestRankBooks, update: listUpdateRankBooks }
async function load() {
  loading.value = true
  try { const { data } = await FN[tab.value](); list.value = data || [] }
  catch (e) { /* ignore */ } finally { loading.value = false }
}
const go = (id) => router.push(`/book/${id}`)
onMounted(load)
</script>

<style scoped>
.rank-page { padding-top: var(--sp-5); }
.rank-books { display: flex; flex-direction: column; gap: var(--sp-3); }
.rank-row { display: flex; gap: var(--sp-4); padding: var(--sp-4); cursor: pointer; align-items: center; }
.no { font-family: var(--font-serif); font-size: 22px; width: 34px; text-align: center; color: var(--text-3); }
.no.top { color: var(--brand); }
.rank-row img { width: 90px; height: 120px; object-fit: cover; border-radius: var(--radius-sm); }
.meta { flex: 1; }
.author { color: var(--text-2); font-size: 13px; }
.desc { color: var(--text-2); font-size: 13px; margin: 6px 0; }
.last { color: var(--text-3); font-size: 12px; }
</style>
```

- [ ] **Step 4: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/BookClass.vue front/Claude4-8frontWeb/src/views/portal/BookRank.vue front/Claude4-8frontWeb/src/views/portal/Search.vue && git commit -m "feat(c48-web): 分类页与榜单页

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 15: 小说详情页（BookDetail）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/BookDetail.vue`

**Interfaces:**
- Consumes: `@/api/book`（getBookById/getLastChapterAbout/listRecBooks/listNewestComments/addVisitCount）、`@/api/user`（getBookshelfStatus/addBookshelf/removeBookshelf/comment）、`useUserStore`、`BookCard`、`@/utils/format`。
- 路由参数 `id`。进入时 `addVisitCount(id)`（静默）。

- [ ] **Step 1: BookDetail.vue**

```vue
<template>
  <div v-loading="loading" class="container detail-page">
    <div v-if="book" class="detail-head card">
      <img class="cover" :src="cover(book.picUrl)" @error="onErr" />
      <div class="info">
        <h2 class="name">{{ book.bookName }}</h2>
        <div class="sub">
          <span>{{ book.authorName }}</span>
          <span class="tag">{{ book.categoryName }}</span>
          <span class="tag">{{ statusText }}</span>
        </div>
        <div class="stats">
          <div><b>{{ visit }}</b><span>点击</span></div>
          <div><b>{{ words }}</b><span>字数</span></div>
          <div><b>{{ book.commentCount || 0 }}</b><span>评论</span></div>
        </div>
        <p class="desc text-clamp-3">{{ book.bookDesc }}</p>
        <div class="actions">
          <el-button type="primary" size="large" @click="startRead">开始阅读</el-button>
          <el-button size="large" @click="toCatalog">目录</el-button>
          <el-button size="large" :type="inShelf ? 'success' : 'default'" @click="toggleShelf">
            {{ inShelf ? '已在书架' : '加入书架' }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <div class="main-col">
        <div class="last-chapter card" v-if="lastAbout">
          <h4 class="section-title">最新章节</h4>
          <p class="lc-name" @click="readChapter(lastAbout.chapterInfo?.id)">{{ lastAbout.chapterInfo?.chapterName }}</p>
          <p class="lc-summary">{{ lastAbout.contentSummary }}</p>
          <p class="lc-total">共 {{ lastAbout.chapterTotal }} 章</p>
        </div>

        <div class="comments card">
          <h4 class="section-title">最新评论（{{ commentData.commentTotal || 0 }}）</h4>
          <div v-if="userStore.isLoggedIn" class="comment-form">
            <el-input v-model="newComment" type="textarea" :rows="3" maxlength="512"
                      placeholder="写下你的评论（10-512 字）" />
            <el-button type="primary" @click="submitComment">发表评论</el-button>
          </div>
          <p v-else class="login-tip"><router-link to="/login">登录</router-link> 后可发表评论</p>
          <div v-if="commentData.comments && commentData.comments.length" class="comment-list">
            <div v-for="c in commentData.comments" :key="c.id" class="comment-item">
              <img class="c-avatar" :src="cover(c.commentUserPhoto)" @error="onErr" />
              <div class="c-body">
                <div class="c-user">{{ c.commentUser }}</div>
                <div class="c-content">{{ c.commentContent }}</div>
                <div class="c-time">{{ formatTime(c.commentTime) }}</div>
              </div>
            </div>
          </div>
          <EmptyBlock v-else text="还没有评论，来抢沙发" />
        </div>
      </div>

      <aside class="rec-col">
        <h4 class="section-title">相关推荐</h4>
        <div class="rec-list">
          <BookCard v-for="b in recBooks" :key="b.id" :book="b" />
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getBookById, getLastChapterAbout, listRecBooks, listNewestComments, addVisitCount
} from '@/api/book'
import {
  getBookshelfStatus, addBookshelf, removeBookshelf, comment as postComment
} from '@/api/user'
import { useUserStore } from '@/stores/user'
import { formatWordCount, formatVisit, bookStatusText, formatTime } from '@/utils/format'
import BookCard from '@/components/common/BookCard.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'

const route = useRoute(), router = useRouter()
const userStore = useUserStore()
const bookId = route.params.id
const book = ref(null), lastAbout = ref(null), recBooks = ref([])
const commentData = ref({ commentTotal: 0, comments: [] })
const inShelf = ref(false), loading = ref(false), newComment = ref('')

const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="160" height="210"><rect width="160" height="210" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const statusText = computed(() => bookStatusText(book.value?.bookStatus))
const words = computed(() => formatWordCount(book.value?.wordCount))
const visit = computed(() => formatVisit(book.value?.visitCount))

const startRead = () => { if (book.value?.firstChapterId) router.push(`/read/${book.value.firstChapterId}`) }
const toCatalog = () => router.push(`/book/${bookId}/catalog`)
const readChapter = (cid) => { if (cid) router.push(`/read/${cid}`) }

async function toggleShelf() {
  if (!userStore.isLoggedIn) { router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  try {
    if (inShelf.value) { await removeBookshelf(bookId); inShelf.value = false; ElMessage.success('已移出书架') }
    else { await addBookshelf(bookId); inShelf.value = true; ElMessage.success('已加入书架') }
  } catch (e) { /* ignore */ }
}
async function submitComment() {
  if (newComment.value.trim().length < 10) { ElMessage.warning('评论至少 10 个字'); return }
  try {
    await postComment({ bookId: Number(bookId), commentContent: newComment.value })
    ElMessage.success('评论成功'); newComment.value = ''
    const { data } = await listNewestComments(bookId); commentData.value = data || { comments: [] }
  } catch (e) { /* ignore */ }
}

async function safe(fn, target, def) { try { const { data } = await fn; target.value = data ?? def } catch (e) {} }
onMounted(async () => {
  loading.value = true
  try {
    const { data } = await getBookById(bookId); book.value = data
    addVisitCount(bookId).catch(() => {})
    getLastChapterAbout(bookId).then(r => lastAbout.value = r.data).catch(() => {})
    listRecBooks(bookId).then(r => recBooks.value = r.data || []).catch(() => {})
    listNewestComments(bookId).then(r => commentData.value = r.data || { comments: [] }).catch(() => {})
    if (userStore.isLoggedIn) {
      getBookshelfStatus(bookId).then(r => inShelf.value = r.data === 1).catch(() => {})
    }
  } catch (e) { /* ignore */ } finally { loading.value = false }
})
</script>

<style scoped>
.detail-page { padding-top: var(--sp-5); }
.detail-head { display: flex; gap: var(--sp-5); padding: var(--sp-5); }
.detail-head .cover { width: 160px; height: 210px; object-fit: cover; border-radius: var(--radius-sm); }
.info { flex: 1; }
.name { font-size: 26px; margin-bottom: 8px; }
.sub { display: flex; gap: 12px; align-items: center; color: var(--text-2); margin-bottom: var(--sp-3); }
.sub .tag { background: var(--brand-soft); color: var(--brand); padding: 2px 10px; border-radius: 12px; font-size: 12px; }
.stats { display: flex; gap: var(--sp-6); margin: var(--sp-3) 0; }
.stats b { font-family: var(--font-serif); font-size: 20px; color: var(--brand); display: block; }
.stats span { font-size: 12px; color: var(--text-3); }
.desc { color: var(--text-2); margin-bottom: var(--sp-4); }
.detail-body { display: grid; grid-template-columns: 1fr 260px; gap: var(--sp-5); margin-top: var(--sp-5); }
.main-col { display: flex; flex-direction: column; gap: var(--sp-4); }
.last-chapter, .comments { padding: var(--sp-4); }
.lc-name { color: var(--brand); cursor: pointer; font-size: 15px; }
.lc-summary { color: var(--text-2); font-size: 13px; margin: 8px 0; }
.lc-total { color: var(--text-3); font-size: 12px; }
.comment-form { display: flex; flex-direction: column; gap: 10px; align-items: flex-end; margin-bottom: var(--sp-4); }
.login-tip { color: var(--text-3); margin-bottom: var(--sp-3); }
.login-tip a { color: var(--brand); }
.comment-item { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--border); }
.c-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.c-user { font-weight: 600; font-size: 14px; }
.c-content { color: var(--text); margin: 4px 0; }
.c-time { color: var(--text-3); font-size: 12px; }
.rec-list { display: flex; flex-direction: column; gap: var(--sp-3); }
@media (max-width: 900px) { .detail-body { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 3: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/BookDetail.vue && git commit -m "feat(c48-web): 小说详情页(信息/书架/评论/推荐)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 16: 章节目录（ChapterList）与阅读页（BookReader）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/ChapterList.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/BookReader.vue`

**Interfaces:**
- ChapterList Consumes: `@/api/book`（listChapters/getBookById）；路由 `id`；点击章节跳 `/read/:chapterId`。
- BookReader Consumes: `@/api/book`（getBookContent/getPreChapterId/getNextChapterId）；路由 `chapterId`；三主题 + 字号调节（持久化 localStorage）。

- [ ] **Step 1: ChapterList.vue**

```vue
<template>
  <div v-loading="loading" class="container catalog-page">
    <div class="catalog-head">
      <h3 class="book-name">{{ book?.bookName }}</h3>
      <span class="total">共 {{ chapters.length }} 章</span>
    </div>
    <div class="chapter-grid">
      <div v-for="c in chapters" :key="c.id" class="chapter-item" @click="read(c.id)">
        <span class="text-ellipsis">{{ c.chapterName }}</span>
        <el-icon v-if="c.isVip === 1" color="#b5352b"><Lock /></el-icon>
      </div>
    </div>
    <EmptyBlock v-if="!loading && !chapters.length" text="暂无章节" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listChapters, getBookById } from '@/api/book'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const route = useRoute(), router = useRouter()
const bookId = route.params.id
const chapters = ref([]), book = ref(null), loading = ref(false)
const read = (cid) => router.push(`/read/${cid}`)
onMounted(async () => {
  loading.value = true
  try {
    getBookById(bookId).then(r => book.value = r.data).catch(() => {})
    const { data } = await listChapters(bookId); chapters.value = data || []
  } catch (e) {} finally { loading.value = false }
})
</script>

<style scoped>
.catalog-page { padding-top: var(--sp-5); }
.catalog-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: var(--sp-4); }
.book-name { font-size: 22px; }
.total { color: var(--text-3); font-size: 13px; }
.chapter-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--sp-2); }
.chapter-item { display: flex; justify-content: space-between; align-items: center; gap: 6px; padding: 10px 14px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-sm); cursor: pointer; font-size: 14px; }
.chapter-item:hover { color: var(--brand); border-color: var(--brand); }
@media (max-width: 760px) { .chapter-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
```

- [ ] **Step 2: BookReader.vue**

```vue
<template>
  <div class="reader-page" :class="`reader-theme-${theme}`" :style="readerVars">
    <div class="reader-topbar">
      <div class="container topbar-inner">
        <span class="back" @click="goDetail">‹ {{ content?.bookInfo?.bookName || '返回' }}</span>
        <div class="tools">
          <el-button-group>
            <el-button size="small" @click="setTheme('day')">日间</el-button>
            <el-button size="small" @click="setTheme('sepia')">护眼</el-button>
            <el-button size="small" @click="setTheme('night')">夜间</el-button>
          </el-button-group>
          <el-button size="small" @click="fs = Math.max(14, fs - 2)">A-</el-button>
          <el-button size="small" @click="fs = Math.min(28, fs + 2)">A+</el-button>
          <el-button size="small" @click="goCatalog">目录</el-button>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="reader-content">
      <h2 class="chapter-title">{{ content?.chapterInfo?.chapterName }}</h2>
      <div class="reader-body" v-html="renderedContent"></div>
      <div class="reader-nav">
        <el-button :disabled="!hasPrev" @click="goPrev">上一章</el-button>
        <el-button @click="goCatalog">目录</el-button>
        <el-button :disabled="!hasNext" @click="goNext">下一章</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBookContent, getPreChapterId, getNextChapterId } from '@/api/book'

const route = useRoute(), router = useRouter()
const content = ref(null), loading = ref(false)
const prevId = ref(null), nextId = ref(null)
const theme = ref(localStorage.getItem('reader_theme') || 'day')
const fs = ref(Number(localStorage.getItem('reader_fs')) || 18)

const readerVars = computed(() => ({ '--reader-fs': fs.value + 'px', '--reader-lh': 1.9 }))
const hasPrev = computed(() => prevId.value && prevId.value > 0)
const hasNext = computed(() => nextId.value && nextId.value > 0)
const renderedContent = computed(() => {
  const c = content.value?.bookContent || ''
  return c.split(/\n+/).filter(Boolean).map(p => `<p>${p}</p>`).join('')
})

const setTheme = (t) => { theme.value = t; localStorage.setItem('reader_theme', t) }
watch(fs, (v) => localStorage.setItem('reader_fs', String(v)))

const goDetail = () => { if (content.value?.bookInfo?.id) router.push(`/book/${content.value.bookInfo.id}`) }
const goCatalog = () => { if (content.value?.bookInfo?.id) router.push(`/book/${content.value.bookInfo.id}/catalog`) }
const goPrev = () => hasPrev.value && router.push(`/read/${prevId.value}`)
const goNext = () => hasNext.value && router.push(`/read/${nextId.value}`)

async function load(chapterId) {
  loading.value = true
  try {
    const { data } = await getBookContent(chapterId); content.value = data
    getPreChapterId(chapterId).then(r => prevId.value = r.data).catch(() => prevId.value = null)
    getNextChapterId(chapterId).then(r => nextId.value = r.data).catch(() => nextId.value = null)
    window.scrollTo({ top: 0 })
  } catch (e) { ElMessage.error('章节加载失败') } finally { loading.value = false }
}
watch(() => route.params.chapterId, (id) => { if (id) load(id) })
onMounted(() => load(route.params.chapterId))
</script>

<style scoped>
.reader-topbar { position: sticky; top: 0; background: rgba(255,255,255,0.02); backdrop-filter: blur(4px); border-bottom: 1px solid rgba(150,150,150,0.15); padding: 10px 0; z-index: 20; }
.topbar-inner { display: flex; justify-content: space-between; align-items: center; }
.back { cursor: pointer; font-size: 15px; }
.tools { display: flex; gap: 8px; align-items: center; }
.reader-content { padding-bottom: 60px; }
.chapter-title { text-align: center; padding-top: 40px; font-family: var(--font-serif); }
.reader-nav { display: flex; justify-content: center; gap: 16px; padding: 40px 0; }
</style>
```

- [ ] **Step 3: 构建验证**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -8`
Expected: 构建通过。

- [ ] **Step 4: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/ChapterList.vue front/Claude4-8frontWeb/src/views/portal/BookReader.vue && git commit -m "feat(c48-web): 章节目录与沉浸式阅读页(三主题/字号/翻章)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 17: 新闻详情页（News）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/News.vue`

**Interfaces:**
- Consumes: `@/api/news`（getNewsById）、`@/utils/format`（formatTime）。路由 `id`。

- [ ] **Step 1: News.vue**

```vue
<template>
  <div v-loading="loading" class="container news-page">
    <article v-if="news" class="news card">
      <h1 class="title">{{ news.title }}</h1>
      <div class="meta">
        <span>{{ news.sourceName }}</span>
        <span>{{ formatTime(news.updateTime) }}</span>
        <span v-if="news.categoryName" class="tag">{{ news.categoryName }}</span>
      </div>
      <div class="content" v-html="news.content"></div>
    </article>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getNewsById } from '@/api/news'
import { formatTime } from '@/utils/format'
const route = useRoute()
const news = ref(null), loading = ref(false)
onMounted(async () => {
  loading.value = true
  try { const { data } = await getNewsById(route.params.id); news.value = data }
  catch (e) {} finally { loading.value = false }
})
</script>

<style scoped>
.news-page { padding-top: var(--sp-5); max-width: 820px; }
.news { padding: var(--sp-6); }
.title { font-size: 26px; text-align: center; margin-bottom: var(--sp-4); }
.meta { display: flex; gap: 16px; justify-content: center; color: var(--text-3); font-size: 13px; padding-bottom: var(--sp-4); border-bottom: 1px solid var(--border); }
.meta .tag { color: var(--brand); }
.content { padding-top: var(--sp-4); line-height: 1.9; font-size: 16px; }
.content :deep(p) { margin: 0 0 1em; }
</style>
```

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -6`
Expected: 构建通过。

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/News.vue && git commit -m "feat(c48-web): 新闻详情页

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 18: 登录页（Login）与注册页（Register）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/Login.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/Register.vue`

**Interfaces:**
- Login Consumes: `useUserStore`（loginAction）；成功后按 `route.query.redirect` 跳转。
- Register Consumes: `@/api/resource`（getImgVerifyCode 拿 sessionId + base64 图）、`@/api/user`（register）；注册成功跳登录。
- 后端校验：username `^1[3-9]\d{9}$`；password 非空；velCode `^\d{4}$`；sessionId 长度 32（来自验证码接口）。

- [ ] **Step 1: Login.vue**

```vue
<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">登录墨书</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="username">
          <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">登录</el-button>
      </el-form>
      <p class="switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter(), route = useRoute()
const userStore = useUserStore()
const formRef = ref(), loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.loginAction({ ...form })
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/home')
    } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
  })
}
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 60px 16px; }
.auth-card { width: 400px; max-width: 100%; padding: var(--sp-6); }
.auth-title { text-align: center; font-size: 24px; margin-bottom: var(--sp-5); }
.submit-btn { width: 100%; margin-top: var(--sp-2); }
.switch { text-align: center; margin-top: var(--sp-4); color: var(--text-2); font-size: 14px; }
.switch a { color: var(--brand); }
</style>
```

- [ ] **Step 2: Register.vue**

```vue
<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">注册墨书</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="username">
          <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请设置密码" show-password />
        </el-form-item>
        <el-form-item label="图形验证码" prop="velCode">
          <div class="vel-row">
            <el-input v-model="form.velCode" placeholder="4 位验证码" maxlength="4" />
            <img class="vel-img" :src="velImg" alt="验证码" title="点击刷新" @click="loadCode" />
          </div>
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">注册</el-button>
      </el-form>
      <p class="switch">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getImgVerifyCode } from '@/api/resource'
import { register } from '@/api/user'

const router = useRouter()
const formRef = ref(), loading = ref(false)
const velImg = ref('')
const form = reactive({ username: '', password: '', velCode: '', sessionId: '' })
const rules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请设置密码', trigger: 'blur' }],
  velCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '验证码为 4 位数字', trigger: 'blur' }
  ]
}
async function loadCode() {
  try {
    const { data } = await getImgVerifyCode()
    velImg.value = 'data:image/png;base64,' + data.img
    form.sessionId = data.sessionId
  } catch (e) { /* ignore */ }
}
async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!form.sessionId) { ElMessage.warning('请先获取验证码'); return }
    loading.value = true
    try {
      await register({ ...form })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (e) { loadCode() } finally { loading.value = false }
  })
}
onMounted(loadCode)
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 60px 16px; }
.auth-card { width: 400px; max-width: 100%; padding: var(--sp-6); }
.auth-title { text-align: center; font-size: 24px; margin-bottom: var(--sp-5); }
.vel-row { display: flex; gap: 12px; width: 100%; }
.vel-img { height: 40px; border-radius: var(--radius-sm); cursor: pointer; border: 1px solid var(--border); }
.submit-btn { width: 100%; margin-top: var(--sp-2); }
.switch { text-align: center; margin-top: var(--sp-4); color: var(--text-2); font-size: 14px; }
.switch a { color: var(--brand); }
</style>
```

- [ ] **Step 3: 构建验证 + Commit**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -6`
Expected: 构建通过。

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/Login.vue front/Claude4-8frontWeb/src/views/portal/Register.vue && git commit -m "feat(c48-web): 登录与注册页(含图形验证码流程)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 19: 个人中心（Bookshelf / MyComments / Feedback / Profile）

**Files:**
- Modify: `front/Claude4-8frontWeb/src/views/portal/user/Bookshelf.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/user/MyComments.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/user/Feedback.vue`
- Modify: `front/Claude4-8frontWeb/src/views/portal/user/Profile.vue`

**Interfaces:**
- Consumes: `@/api/user`（listBookshelf/removeBookshelf、listComments、submitFeedback/listFeedbacks/deleteFeedback、getUserInfo/updateUserInfo）、`Pagination`、`EmptyBlock`、`BookCard`、`useUserStore`。
- 均为需登录页（路由守卫已保障）。

- [ ] **Step 1: Bookshelf.vue**

```vue
<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">我的书架</h3>
    <template v-if="list.length">
      <div class="shelf-grid">
        <div v-for="b in list" :key="b.id" class="shelf-item">
          <BookCard :book="b" />
          <el-button size="small" text type="danger" @click="remove(b.id)">移出</el-button>
        </div>
      </div>
      <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
    </template>
    <EmptyBlock v-else-if="!loading" text="书架空空如也，去首页逛逛吧" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listBookshelf, removeBookshelf } from '@/api/user'
import BookCard from '@/components/common/BookCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 12 })
async function load() {
  loading.value = true
  try { const { data } = await listBookshelf({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
async function remove(id) {
  try { await removeBookshelf(id); ElMessage.success('已移出书架'); load() } catch (e) {}
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.shelf-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--sp-4); }
.shelf-item { display: flex; flex-direction: column; gap: 6px; align-items: center; }
@media (max-width: 900px) { .shelf-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
```

- [ ] **Step 2: MyComments.vue**

```vue
<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">我的评论</h3>
    <template v-if="list.length">
      <div class="comment-list">
        <div v-for="c in list" :key="c.id" class="comment-card card">
          <img class="c-cover" :src="cover(c.commentBookPic)" @error="onErr" />
          <div class="c-main">
            <div class="c-book">{{ c.commentBook }}</div>
            <div class="c-content">{{ c.commentContent }}</div>
            <div class="c-foot">
              <span>{{ formatTime(c.commentTime) }}</span>
              <span class="audit" :class="'a' + c.auditStatus">{{ auditText(c.auditStatus) }}</span>
            </div>
          </div>
        </div>
      </div>
      <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
    </template>
    <EmptyBlock v-else-if="!loading" text="还没有发表过评论" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listComments } from '@/api/user'
import { formatTime } from '@/utils/format'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="60" height="80"><rect width="60" height="80" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const auditText = (s) => (s === 1 ? '已通过' : s === 2 ? '未通过' : '待审核')
async function load() {
  loading.value = true
  try { const { data } = await listComments({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.comment-list { display: flex; flex-direction: column; gap: var(--sp-3); }
.comment-card { display: flex; gap: var(--sp-3); padding: var(--sp-4); }
.c-cover { width: 60px; height: 80px; object-fit: cover; border-radius: var(--radius-sm); }
.c-main { flex: 1; }
.c-book { font-weight: 600; }
.c-content { color: var(--text-2); margin: 6px 0; }
.c-foot { display: flex; gap: 16px; font-size: 12px; color: var(--text-3); }
.audit.a1 { color: #67c23a; } .audit.a2 { color: var(--brand); } .audit.a0 { color: #e6a23c; }
</style>
```

- [ ] **Step 3: Feedback.vue**

```vue
<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">意见反馈</h3>
    <div class="fb-form card">
      <el-input v-model="content" type="textarea" :rows="4" maxlength="512" show-word-limit
                placeholder="请描述你的问题或建议（5-512 字）" />
      <el-button type="primary" @click="submit">提交反馈</el-button>
    </div>
    <div class="fb-list">
      <div v-for="f in list" :key="f.id" class="fb-item card">
        <div class="fb-content">{{ f.content }}</div>
        <div class="fb-foot">
          <span>{{ formatTime(f.createTime) }}</span>
          <el-button size="small" text type="danger" @click="remove(f.id)">删除</el-button>
        </div>
      </div>
      <EmptyBlock v-if="!loading && !list.length" text="还没有反馈记录" />
    </div>
    <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { submitFeedback, listFeedbacks, deleteFeedback } from '@/api/user'
import { formatTime } from '@/utils/format'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const content = ref(''), list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })
async function submit() {
  if (content.value.trim().length < 5) { ElMessage.warning('反馈至少 5 个字'); return }
  try { await submitFeedback(content.value); ElMessage.success('反馈已提交'); content.value = ''; load() } catch (e) {}
}
async function remove(id) { try { await deleteFeedback(id); ElMessage.success('已删除'); load() } catch (e) {} }
async function load() {
  loading.value = true
  try { const { data } = await listFeedbacks({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.fb-form { padding: var(--sp-4); display: flex; flex-direction: column; gap: 12px; align-items: flex-end; margin-bottom: var(--sp-4); }
.fb-list { display: flex; flex-direction: column; gap: var(--sp-3); }
.fb-item { padding: var(--sp-4); }
.fb-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; color: var(--text-3); font-size: 12px; }
</style>
```

- [ ] **Step 4: Profile.vue**

```vue
<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">个人资料</h3>
    <div class="profile card">
      <el-form :model="form" label-width="80px">
        <el-form-item label="头像">
          <el-upload :show-file-list="false" :before-upload="onUpload" accept="image/*">
            <img v-if="form.userPhoto" :src="photoUrl" class="avatar" />
            <div v-else class="avatar avatar-empty">上传</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickName" maxlength="10" style="max-width:300px" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.userSex">
            <el-radio :value="0">男</el-radio>
            <el-radio :value="1">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" @click="save">保存修改</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { uploadImage } from '@/api/resource'
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ nickName: '', userPhoto: '', userSex: 0 })
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const photoUrl = computed(() => form.userPhoto ? IMG + form.userPhoto : '')
async function load() {
  loading.value = true
  try {
    const { data } = await getUserInfo()
    form.nickName = data.nickName || ''
    form.userPhoto = data.userPhoto || ''
    form.userSex = data.userSex ?? 0
  } catch (e) {} finally { loading.value = false }
}
async function onUpload(file) {
  try { const { data } = await uploadImage(file); form.userPhoto = data; ElMessage.success('头像已上传') } catch (e) {}
  return false
}
async function save() {
  try {
    await updateUserInfo({ nickName: form.nickName, userPhoto: form.userPhoto, userSex: form.userSex })
    ElMessage.success('资料已更新')
    userStore.nickName = form.nickName
    await userStore.fetchUserInfo()
  } catch (e) {}
}
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.profile { padding: var(--sp-6); max-width: 560px; }
.avatar { width: 84px; height: 84px; border-radius: 50%; object-fit: cover; }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--brand-soft); color: var(--brand); border: 1px dashed var(--brand); }
</style>
```

> 注：`updateUserInfo` 后端对 `userPhoto` 有正则校验 `^/[^\s]{10,}\.(png|jpg|...)$`；若上传返回的路径不满足，保存会 400。保存时若 `userPhoto` 为空则不传该字段（后端字段可空）。执行时在 save() 里过滤空 userPhoto：仅当有值才带上。

- [ ] **Step 5: 构建验证 + Commit**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -6`
Expected: 构建通过。

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/views/portal/user && git commit -m "feat(c48-web): 个人中心(书架/评论/反馈/资料)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 20: AI 智能书童组件（AiCompanion）

**Files:**
- Create: `front/Claude4-8frontWeb/src/components/ai/AiCompanion.vue`

**Interfaces:**
- Consumes: `@/api/ai`（getConversationId/newConversation/streamChat/getChatHistory/clearMemory/recommendSimilar）、`useUserStore`。
- Produces: 悬浮按钮 + Drawer 聊天面板；流式打字机；快捷指令；新对话；相似推荐（登录后）。

- [ ] **Step 1: AiCompanion.vue**

```vue
<template>
  <div class="ai-wrap">
    <div class="ai-fab" :class="{ open: visible }" @click="toggle">
      <span class="fab-icon">🖋️</span>
      <span class="fab-text">书童</span>
    </div>

    <el-drawer v-model="visible" :with-header="false" size="420px" direction="rtl">
      <div class="chat-box">
        <div class="chat-head">
          <div class="head-left">
            <span class="avatar">🧙</span>
            <div>
              <div class="h-title">智能书童 · 小墨</div>
              <div class="h-desc">找书 / 榜单 / 推荐，问我就好</div>
            </div>
          </div>
          <div class="head-actions">
            <el-button size="small" text @click="startNew" :disabled="loading">新对话</el-button>
            <el-button size="small" text @click="visible = false">收起</el-button>
          </div>
        </div>

        <div class="quick">
          <span v-for="(q, i) in quicks" :key="i" class="quick-tag" @click="send(q)">{{ q }}</span>
        </div>

        <div class="msg-list" ref="listRef">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <span class="m-avatar">{{ m.role === 'assistant' ? '🧙' : '👤' }}</span>
            <div class="m-bubble" v-html="renderMd(m.content)"></div>
          </div>
          <div v-if="thinking" class="msg assistant">
            <span class="m-avatar">🧙</span>
            <div class="m-bubble typing">正在翻阅藏书……</div>
          </div>
        </div>

        <div v-if="userStore.isLoggedIn && recs.length" class="recs">
          <div class="recs-title">📖 为你推荐</div>
          <div v-for="r in recs" :key="r.bookId" class="rec-item" @click="goBook(r.bookId)">
            <span class="rec-name">{{ r.bookName }}</span>
            <span class="rec-reason">{{ r.recommendReason }}</span>
          </div>
        </div>

        <div class="chat-input">
          <el-input v-model="input" type="textarea" :rows="2" resize="none"
                    placeholder="输入你想找的书，回车发送" @keyup.enter.exact.prevent="send()" />
          <el-button type="primary" :disabled="loading || !input.trim()" @click="send()">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getConversationId, newConversation, streamChat,
  getChatHistory, clearMemory, recommendSimilar
} from '@/api/ai'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const visible = ref(false)
const input = ref(''), loading = ref(false), thinking = ref(false)
const messages = ref([]), recs = ref([])
const listRef = ref()
const convId = ref(getConversationId())
const quicks = ['推荐几本修仙小说', '最近更新榜有什么', '找一本轻松搞笑的', '有什么完结的好书']

function renderMd(text) {
  if (!text) return ''
  return String(text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br/>')
}
async function scrollToBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}
function goBook(id) { visible.value = false; router.push(`/book/${id}`) }

async function send(preset) {
  const text = (preset ?? input.value).trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '' })
  const idx = messages.value.length - 1
  loading.value = true; thinking.value = true
  await scrollToBottom()
  await streamChat(text, convId.value, {
    onChunk: (chunk) => {
      thinking.value = false
      messages.value[idx].content += chunk
      scrollToBottom()
    },
    onDone: () => { loading.value = false; thinking.value = false },
    onError: (err) => {
      loading.value = false; thinking.value = false
      messages.value[idx].content = messages.value[idx].content || ('❌ ' + (err.message || '对话失败'))
    }
  })
}

async function startNew() {
  try { await clearMemory(convId.value) } catch (e) {}
  convId.value = newConversation()
  messages.value = []
}

async function loadHistory() {
  try {
    const { data } = await getChatHistory(convId.value)
    if (Array.isArray(data)) messages.value = data.map(m => ({ role: m.role, content: m.content }))
    scrollToBottom()
  } catch (e) {}
}
async function loadRecs() {
  if (!userStore.isLoggedIn) return
  try { const { data } = await recommendSimilar(4); recs.value = data || [] } catch (e) {}
}

const toggle = () => { visible.value = !visible.value }
watch(visible, (v) => { if (v) { loadHistory(); loadRecs() } })
</script>

<style scoped>
.ai-fab { position: fixed; right: 28px; bottom: 28px; z-index: 60; display: flex; flex-direction: column; align-items: center; gap: 2px; width: 60px; height: 60px; justify-content: center; background: var(--brand); color: #fff; border-radius: 50%; box-shadow: var(--shadow-md); cursor: pointer; transition: transform .2s; }
.ai-fab:hover { transform: scale(1.08); }
.fab-icon { font-size: 20px; }
.fab-text { font-size: 11px; }
.chat-box { display: flex; flex-direction: column; height: 100%; }
.chat-head { display: flex; justify-content: space-between; align-items: center; padding-bottom: var(--sp-3); border-bottom: 1px solid var(--border); }
.head-left { display: flex; gap: 10px; align-items: center; }
.avatar { font-size: 28px; }
.h-title { font-weight: 600; }
.h-desc { font-size: 12px; color: var(--text-3); }
.quick { display: flex; flex-wrap: wrap; gap: 6px; padding: var(--sp-3) 0; }
.quick-tag { font-size: 12px; padding: 4px 10px; background: var(--brand-soft); color: var(--brand); border-radius: 12px; cursor: pointer; }
.msg-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; padding: var(--sp-3) 0; }
.msg { display: flex; gap: 8px; }
.msg.user { flex-direction: row-reverse; }
.m-avatar { font-size: 22px; flex: 0 0 auto; }
.m-bubble { max-width: 78%; padding: 10px 12px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); font-size: 14px; line-height: 1.6; word-break: break-word; }
.msg.user .m-bubble { background: var(--brand); color: #fff; border-color: var(--brand); }
.m-bubble.typing { color: var(--text-3); }
.recs { border-top: 1px solid var(--border); padding: var(--sp-3) 0; }
.recs-title { font-size: 13px; color: var(--text-2); margin-bottom: 6px; }
.rec-item { display: flex; flex-direction: column; padding: 6px 0; cursor: pointer; }
.rec-item:hover .rec-name { color: var(--brand); }
.rec-name { font-size: 14px; font-weight: 600; }
.rec-reason { font-size: 12px; color: var(--text-3); }
.chat-input { display: flex; gap: 8px; padding-top: var(--sp-3); border-top: 1px solid var(--border); align-items: flex-end; }
</style>
```

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run build 2>&1 | tail -6`
Expected: 构建通过。

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/components/ai && git commit -m "feat(c48-web): AI 智能书童组件(SSE流式/历史/推荐/新对话)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 21: 挂载书童 + 全局集成

**Files:**
- Modify: `front/Claude4-8frontWeb/src/App.vue`

**Interfaces:**
- 在 App.vue 挂全局 `AiCompanion`，但阅读页（沉浸模式）与登录/注册页不显示（可选：全局都显示）。本期策略：全局显示。

- [ ] **Step 1: 修改 App.vue**

```vue
<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <AiCompanion />
</template>

<script setup>
import AiCompanion from '@/components/ai/AiCompanion.vue'
</script>

<style>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
```

- [ ] **Step 2: 全量测试 + 构建**

Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npx vitest run 2>&1 | tail -12 && npm run build 2>&1 | tail -6`
Expected: 全部单测 PASS；构建通过。

- [ ] **Step 3: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/src/App.vue && git commit -m "feat(c48-web): 全局挂载 AI 书童

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 22: 联调验证与 README

**Files:**
- Create: `front/Claude4-8frontWeb/README.md`

**Interfaces:** 无新增代码接口；本任务做真实后端联调核对与文档。

- [ ] **Step 1: 启动后端（若可用）并联调五条主链路**

前置：本地后端 `http://localhost:8888` 已启动（需 MySQL/Redis，ES/AI 视配置）。
Run: `cd "C:/Users/35998/Desktop/job/project/novel/front/Claude4-8frontWeb" && npm run dev`
手动核对（逐条记录通过/失败）：
1. 首页：推荐位/三榜/资讯/友链是否有数据。
2. 搜索：关键字 + 频道 + 分类筛选分页。
3. 详情→目录→阅读：三主题切换、字号、上/下一章。
4. 登录/注册：验证码 sessionId 流程、token 写入 reader 槽（DevTools 看 localStorage `novel_reader_token`）。
5. 个人中心：书架增删、评论列表、反馈增删、资料保存。
6. AI 书童：SSE 流式输出、历史回显、新对话清记忆、（登录后）相似推荐。

> 若后端未就绪，记录「未联调」并说明阻塞原因；单测与构建仍须通过。

- [ ] **Step 2: 编写 README.md**

```markdown
# Claude4-8frontWeb（墨书）

Novel 小说系统前端（Vue 3 + Vite + Element Plus），对接后端读者门户与 AI 智能书童。

## 开发
\`\`\`bash
npm install
npm run dev      # http://localhost:2048，通过 proxy 转发 /api → localhost:8888
\`\`\`

## 构建 / 测试
\`\`\`bash
npm run build
npm run test
\`\`\`

## 环境变量
- `VITE_BASE_API_URL`：后端 API 基址（开发 `/api`，生产完整域名 + `/api`）
- `VITE_BASE_IMG_URL`：图片资源基址（开发留空走 proxy）

## 目录
- `src/api`：与后端模块 1:1 的接口层
- `src/utils`：request（RestResp 拆包/A0230/双槽 token）、auth、format
- `src/stores`：Pinia（user/category）
- `src/views/portal`：读者门户页面
- `src/components/ai/AiCompanion.vue`：AI 书童（SSE 流式）

## 鉴权说明
读者 token 存 `novel_reader_token`，管理员 token 存 `novel_admin_token`，请求拦截器按路径自动选取，两者互不覆盖。

## 后续
- 第 2 期：作家专区（`/author/*`）
- 第 3 期：管理后台（`/admin/*`）
```

- [ ] **Step 3: Commit**

```bash
cd "C:/Users/35998/Desktop/job/project/novel" && git add front/Claude4-8frontWeb/README.md && git commit -m "docs(c48-web): 项目 README 与联调记录

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review（计划自查）

**Spec coverage：**
- 接口契约(§2)→Task 3/4/5；API 层 9 模块(§3)→Task 6/7；设计系统(§4)→Task 2；目录(§5)→贯穿；路由守卫(§6)→Task 9；AI 数据流(§7)→Task 20/21；错误处理(§8)→Task 5；测试(§9)→Task 3/4/5/6/8 + 各构建；验收(§11)→Task 22。读者门户全部页面→Task 12-19。✅ 无遗漏。
- 第 2/3 期页面为 spec 明确非目标，API 文件已在 Task 7 建好，页面留到后续期。

**Placeholder scan：** 无 TBD/TODO；每步含实际代码或命令。两处「执行时同步补」（Search.vue query 初始化、Profile 空 userPhoto 过滤）已在对应 Step 给出确切改法，非占位。

**Type consistency：**
- `request` 成功返回整个 `RestResp`，调用方统一 `const { data } = await api()` 取业务数据——各 view 一致。
- auth 函数名（getReaderToken/setReaderToken/tokenForUrl 等）Task 4 定义，Task 5/8 引用一致。
- api 函数名 Task 6/7 定义，Task 12-20 引用一致（listVisitRankBooks/getBookContent/searchBooks/streamChat/recommendSimilar 等核对无误）。
- store（useUserStore.isLoggedIn/loginAction/logout、useCategoryStore.load）Task 8 定义，Task 11/13/14/15/18 引用一致。
