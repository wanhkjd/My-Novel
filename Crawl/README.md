# novel-crawl 小说采集与导入工具

配套 [novel](https://github.com/201206030/novel) 小说系统的辅助工具，两大功能：

1. **爬取指定小说**：按配置文件里的站点规则（CSS 选择器 + 正则）抓取书籍信息、目录、章节正文，写入 novel 数据库；
2. **TXT 导入**：上传本地小说 TXT 文件，自动识别编码（UTF-8 / GBK / UTF-16）、按章节标题切分后写入数据库。

技术栈与 novel 主项目保持一致：**Spring Boot 3.3 + Java 21 + MyBatis-Plus 3.5.6 + Jsoup**。

写入的表：`book_info`（书籍）、`book_chapter`（章节）、`book_content`（内容），类别读取 `book_category`。

## 快速开始

1. 确认 novel 数据库可用（表结构即 `novel_struc.sql`）；
2. 按自己环境修改 `src/main/resources/application.yml` 里的数据库地址、账号、密码（默认与 novel 主项目相同：`192.168.100.129:3306/novel`，root/123）；
3. 启动：

```bash
mvn spring-boot:run
# 或打包运行
mvn clean package -DskipTests
java -jar target/novel-crawl-1.0.0.jar
```

4. 打开网页操作台：**http://localhost:8899/** （端口 8899，与 novel 的 8888 错开）。

页面上可以直接提交爬取任务、上传 TXT，任务列表每 2 秒自动刷新进度，执行中的任务可取消。`samples/` 目录里有一个示例 TXT 可用来试导入。

## REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/crawl` | 提交爬取任务（JSON 体） |
| POST | `/api/txt/import` | 上传 TXT 并提交导入任务（multipart 表单） |
| GET | `/api/tasks` | 任务列表（内存中，重启清空，已入库数据不受影响） |
| GET | `/api/tasks/{id}` | 单个任务进度 |
| POST | `/api/tasks/{id}/cancel` | 取消执行中的任务 |
| GET | `/api/crawl/sources` | 已配置的站点规则列表 |
| GET | `/api/categories` | 小说类别列表（book_category 表） |

`POST /api/crawl` 请求体：

```json
{
  "sourceName": "biquge",        // 站点标识，可空：按URL前缀自动匹配，匹配不到用第一个
  "bookUrl": "https://某站/book/123/",  // 必填，小说目录页地址
  "categoryId": 1,               // 可空，book_category.id
  "workDirection": 0,            // 可空，0-男频 1-女频（选了分类时以分类表为准）
  "override": false              // true=删除已有章节覆盖重爬，false=增量续爬（默认）
}
```

`POST /api/txt/import` 表单字段：`file`（TXT 文件，必填）、`bookName`、`authorName`、`bookDesc`、`categoryId`、`workDirection`、`bookStatus`（默认 1 已完结）、`chapterRegex`（自定义章节标题正则）、`override`。除 file 外均可空。

## 站点爬取规则配置

规则写在 `application.yml` 的 `novel-crawl.sources` 下，一个站点一套，全部用 Jsoup 的 CSS 选择器定位，正则做二次提取。内置了一套常见笔趣阁模板站（`#info` / `#list` / `#content` 结构）的示例规则，**使用前需按目标站点实际页面微调**。

| 配置项 | 说明 |
| --- | --- |
| `name` / `remark` | 站点标识（提交任务时用）/ 备注 |
| `url-prefixes` | 按书籍 URL 前缀自动匹配站点规则 |
| `charset` | 页面编码，留空自动识别；老站点常见 `GBK` |
| `interval-ms` | 每章抓取间隔（毫秒），限速防封，建议 ≥500 |
| `timeout-ms` / `retry` / `user-agent` | 超时、重试次数、请求 UA |
| `book.name-selector` 等 | 书名/作者/简介/封面/状态的选择器与提取正则 |
| `toc.item-selector` | 目录中章节链接（a 标签）的选择器 |
| `toc.skip` / `toc.dedupe` | 跳过目录前 N 条 / 按 URL 去重（自动去掉顶部"最新章节"重复块） |
| `chapter.title-selector` | 章节页标题选择器，留空用目录里的链接文字 |
| `chapter.content-selector` | 正文容器选择器 |
| `chapter.remove-selectors` | 解析前先移除的节点（script、广告块等） |
| `chapter.filter-regexes` | 按行过滤广告水印的正则列表 |
| `chapter.next-page-*` | 章节内容分页站点的"下一页"支持，默认关闭 |

给新站点写规则的步骤：浏览器打开目标站的书籍页 → F12 查看书名、作者、目录链接、正文容器的元素结构 → 照抄示例复制一段 sources 配置改选择器 → 重启应用，先用一本短篇小说试爬。

## TXT 导入说明

文件名推荐 `《书名》作者：某某.txt` 或 `书名 作者：某某.txt`，可自动解析出书名与作者；也可以在表单里手动填写（参数优先）。

章节识别内置规则支持：`第X章/节/回/集/部/篇/卷`（阿拉伯数字与中文数字均可）、`序章`、`楔子`、`引子`、`番外`、`尾声`、`后记`、`终章`、`大结局` 等标题行；正文中"第二章的内容还没写完，……"这类带句读的叙述句不会被误切；纯分卷标题行（下方无内容）自动跳过。命名特殊的 TXT 可传 `chapterRegex` 自定义正则。完全没有章节标题的文件默认按每章约 3000 字自动分章（`auto-split-when-no-chapter` 可关）。

第一章之前的文字会被提取为书籍简介（自动剔除书名/作者/网址等元信息行）。

## 入库行为

- **去重**：按（书名 + 作者名）判断是否同一本书，与 `book_info` 的唯一键一致；同一本书同时只允许一个任务在执行。
- **增量 / 断点续爬**：默认从"已入库最大章节号"继续处理。爬取中任一章失败会中止任务（说明源站限流或规则失配），**重新提交同一个任务即可从断点续爬**；TXT 重复上传同一本书时只追加新增章节。
- **覆盖重导**：勾选 override 会先删除该书全部章节和内容再重新导入（书籍记录保留，ID 不变）。
- **统计维护**：每次任务结束（含失败/取消）都会重算 `book_info` 的总字数、最新章节 ID/名称/更新时间。
- **默认值**：作家 ID 默认 0（`default-author-id`）、评分默认 80（8.0 分）、TXT 导入默认"已完结"、免费章节；封面默认存源站地址，`download-cover: true` 可下载到本地目录。
- **内容格式**：默认 `html`——每段 `&nbsp;&nbsp;&nbsp;&nbsp;` 缩进 + `<br/><br/>` 分段，与 novel 前端阅读页兼容；改 `content-format: text` 存纯文本。

## 常见问题

- **novel 前台看不到新导入的书？** novel 主项目有 Caffeine/Redis 缓存，首页推荐位来自 `home_book` 表需自行配置；书籍详情等缓存过期后即可见，急用可重启 novel 应用或清 Redis。
- **爬取速度慢？** 是故意的（`interval-ms` 限速防封）。内网/自建站点可调成 0。
- **章节乱码？** 在对应站点规则里显式指定 `charset: GBK`。
- **任务列表重启后没了？** 任务表在内存中，只影响进度展示，已入库数据都在。

## 开发与测试

`mvn test` 运行单元测试（章节切分 / 编码识别 / 格式化，不需要数据库）。代码注释规范：类与方法用 javadoc 文档注释，字段与方法内代码块用单行注释。

> 本工具仅供学习与技术研究使用。请尊重原创版权，勿爬取、传播受版权保护的内容。
