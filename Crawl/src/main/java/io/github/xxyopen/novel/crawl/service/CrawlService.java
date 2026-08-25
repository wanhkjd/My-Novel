package io.github.xxyopen.novel.crawl.service;

import io.github.xxyopen.novel.crawl.config.CrawlProperties;
import io.github.xxyopen.novel.crawl.crawl.CrawlRule;
import io.github.xxyopen.novel.crawl.crawl.HttpFetcher;
import io.github.xxyopen.novel.crawl.dto.CrawlBookRequest;
import io.github.xxyopen.novel.crawl.dto.ParsedChapter;
import io.github.xxyopen.novel.crawl.entity.BookInfo;
import io.github.xxyopen.novel.crawl.task.TaskInfo;
import io.github.xxyopen.novel.crawl.task.TaskManager;
import io.github.xxyopen.novel.crawl.util.ContentFormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说爬取服务：按站点规则抓取书籍信息、目录、章节内容并写入数据库
 *
 * <p>特性：增量续爬（按已入库最大章节号续传）、限速、失败即停（重新提交任务即可断点续爬）、
 * 广告行过滤、可选封面下载。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {

    // 爬虫与导入配置
    private final CrawlProperties properties;

    // 网页抓取器
    private final HttpFetcher httpFetcher;

    // 书籍入库服务
    private final BookStoreService bookStoreService;

    // 任务管理器
    private final TaskManager taskManager;

    // 后台任务线程池
    private final ThreadPoolTaskExecutor crawlExecutor;

    /**
     * 目录中的一条章节链接
     *
     * @param title 目录里的章节标题
     * @param url   章节页面绝对地址
     */
    public record ChapterLink(String title, String url) {
    }

    /**
     * 提交爬取任务（异步执行，立即返回任务信息供轮询进度）
     *
     * @param request 爬取请求参数
     * @return 新建的任务信息
     * @throws IllegalArgumentException 站点规则不存在时抛出
     */
    public TaskInfo submit(CrawlBookRequest request) {
        // 先在提交线程里解析站点规则，配置错误能立刻反馈给调用方
        CrawlRule rule = resolveRule(request);
        TaskInfo task = taskManager.create("CRAWL", request.getBookUrl());
        try {
            crawlExecutor.execute(() -> doCrawl(task, rule, request));
        } catch (RuntimeException e) {
            // 线程池队列已满等提交失败场景：终结任务，避免留下永远RUNNING的幽灵任务
            task.markFailed("任务提交失败：执行队列已满，请稍后重试");
            throw new IllegalStateException("任务队列已满，请稍后重试");
        }
        return task;
    }

    /**
     * 解析本次任务使用的站点规则
     *
     * @param request 爬取请求参数
     * @return 站点规则
     * @throws IllegalArgumentException 找不到可用规则时抛出
     */
    private CrawlRule resolveRule(CrawlBookRequest request) {
        if (properties.getSources().isEmpty()) {
            throw new IllegalArgumentException("未配置任何站点规则（novel-crawl.sources）");
        }
        // 指定了站点标识时按名称查找
        if (StringUtils.hasText(request.getSourceName())) {
            CrawlRule rule = properties.findSourceByName(request.getSourceName());
            if (rule == null) {
                throw new IllegalArgumentException("站点规则不存在：" + request.getSourceName());
            }
            return rule;
        }
        // 未指定站点时按URL前缀自动匹配，匹配不到取第一个规则
        CrawlRule rule = properties.matchSourceByUrl(request.getBookUrl());
        return rule != null ? rule : properties.getSources().get(0);
    }

    /**
     * 执行爬取任务（在后台线程池中运行）
     *
     * @param task    任务信息（过程中不断更新进度）
     * @param rule    站点规则
     * @param request 爬取请求参数
     */
    private void doCrawl(TaskInfo task, CrawlRule rule, CrawlBookRequest request) {
        Long bookId = null;
        // 已成功锁定的书名/作者（用于 finally 中释放锁）
        String lockedBookName = null;
        String lockedAuthorName = null;
        try {
            // 1. 抓取书籍目录页
            Document bookDoc = httpFetcher.fetchPage(request.getBookUrl(), rule);
            // 2. 解析书籍基础信息
            String bookName = extract(bookDoc, rule.getBook().getNameSelector(), rule.getBook().getNameRegex());
            if (!StringUtils.hasText(bookName)) {
                throw new IllegalStateException("未解析到书名，请检查站点规则 book.name-selector 与书籍URL是否匹配");
            }
            task.setBookName(bookName);
            String authorName = extract(bookDoc, rule.getBook().getAuthorSelector(), rule.getBook().getAuthorRegex());
            if (!StringUtils.hasText(authorName)) {
                authorName = "佚名";
            }
            // 同一本书禁止并发任务，避免章节号交叉冲突
            if (!bookStoreService.tryLockBook(bookName, authorName)) {
                throw new IllegalStateException("《" + bookName + "》已有任务正在执行，请等待其结束后再提交");
            }
            lockedBookName = bookName;
            lockedAuthorName = authorName;
            String bookDesc = extract(bookDoc, rule.getBook().getDescSelector(), null);
            String statusText = extract(bookDoc, rule.getBook().getStatusSelector(), null);
            String remotePic = extractPicUrl(bookDoc, rule);
            // 3. 解析章节目录
            List<ChapterLink> toc = parseToc(bookDoc, rule);
            if (toc.isEmpty()) {
                throw new IllegalStateException("未解析到章节目录，请检查站点规则 toc.item-selector");
            }
            // 4. 建书或定位已有书籍
            BookInfo book = prepareBook(bookName, authorName, bookDesc, statusText, remotePic, rule, request);
            bookId = book.getId();
            task.setBookId(String.valueOf(bookId));
            // 5. 计算增量起点：已入库最大章节号即目录中已完成的条数
            int startNum = bookStoreService.getMaxChapterNum(bookId);
            if (startNum >= toc.size()) {
                task.markSuccess("没有新章节需要爬取（本地已有 " + startNum + " 章，目录共 " + toc.size() + " 章）");
                return;
            }
            List<ChapterLink> pending = toc.subList(startNum, toc.size());
            task.setTotal(pending.size());
            log.info("开始爬取《{}》：目录共 {} 章，本地已有 {} 章，本次需爬 {} 章", bookName, toc.size(), startNum, pending.size());
            // 6. 预编译广告过滤正则
            List<Pattern> filters = compileFilters(rule.getChapter().getFilterRegexes());
            // 7. 逐章抓取入库（任一章节失败即中止，重新提交任务可从断点续爬）
            int nextNum = startNum;
            int isVip = book.getIsVip() == null ? 0 : book.getIsVip();
            for (ChapterLink link : pending) {
                // 响应取消请求
                if (task.isCancelRequested()) {
                    task.markCancelled("任务已取消，本次已入库 " + task.getSaved().get() + " 章，可再次提交断点续爬");
                    bookStoreService.refreshBookStatsQuietly(bookId);
                    return;
                }
                try {
                    ParsedChapter chapter = crawlChapter(link, rule, filters);
                    bookStoreService.saveChapterBatch(bookId, nextNum, isVip, List.of(chapter), properties.getContentFormat());
                    nextNum++;
                    task.getSaved().incrementAndGet();
                    task.getDone().incrementAndGet();
                } catch (Exception e) {
                    // 带上章节上下文重新抛出，方便定位
                    throw new IllegalStateException("在第 " + (nextNum + 1) + " 章（" + link.title() + "）处失败：" + e.getMessage(), e);
                }
                // 每爬 20 章刷新一次书籍统计，让前台能看到字数在涨
                if (task.getDone().get() % 20 == 0) {
                    bookStoreService.refreshBookStats(bookId);
                }
                // 限速，降低被源站封禁的概率
                sleepQuietly(rule.getIntervalMs());
            }
            // 8. 收尾：刷新书籍统计
            bookStoreService.refreshBookStats(bookId);
            task.markSuccess("爬取完成：本次新增入库 " + task.getSaved().get() + " 章");
            log.info("《{}》爬取完成，新增 {} 章", bookName, task.getSaved().get());
        } catch (Exception e) {
            log.error("爬取任务失败：{}", request.getBookUrl(), e);
            // 先终结任务状态（避免统计刷新再出错时任务卡在RUNNING），再尽力刷新统计
            task.markFailed(e.getMessage() + "（已入库 " + task.getSaved().get() + " 章，重新提交任务可断点续爬）");
            bookStoreService.refreshBookStatsQuietly(bookId);
        } finally {
            // 释放本书的任务锁
            if (lockedBookName != null) {
                bookStoreService.unlockBook(lockedBookName, lockedAuthorName);
            }
        }
    }

    /**
     * 建书或定位已有书籍（覆盖重导时清空旧章节并刷新书籍信息）
     *
     * @param bookName   书名
     * @param authorName 作者名
     * @param bookDesc   简介（可空）
     * @param statusText 连载状态文本（可空）
     * @param remotePic  封面图片远程地址（可空）
     * @param rule       站点规则
     * @param request    爬取请求参数
     * @return 可用于入库章节的书籍对象（主键已就绪）
     */
    private BookInfo prepareBook(String bookName, String authorName, String bookDesc, String statusText,
                                 String remotePic, CrawlRule rule, CrawlBookRequest request) {
        // 状态文本包含完结关键字则视为已完结
        Integer bookStatus = null;
        if (StringUtils.hasText(statusText) && StringUtils.hasText(rule.getBook().getFinishedKeyword())) {
            bookStatus = statusText.contains(rule.getBook().getFinishedKeyword()) ? 1 : 0;
        }
        BookInfo existing = bookStoreService.findByNameAndAuthor(bookName, authorName);
        if (existing == null) {
            // 新书：组装信息后入库
            BookInfo book = new BookInfo();
            book.setBookName(bookName);
            book.setAuthorName(authorName);
            book.setBookDesc(bookDesc);
            book.setBookStatus(bookStatus);
            book.setPicUrl(resolveCover(remotePic, rule));
            bookStoreService.fillCategory(book, request.getCategoryId(), request.getWorkDirection());
            return bookStoreService.createBook(book);
        }
        // 已有书籍且要求覆盖重导：清空旧章节并刷新书籍信息
        if (Boolean.TRUE.equals(request.getOverride())) {
            bookStoreService.clearChapters(existing.getId());
            if (StringUtils.hasText(bookDesc)) {
                existing.setBookDesc(bookDesc);
            }
            if (bookStatus != null) {
                existing.setBookStatus(bookStatus);
            }
            if (StringUtils.hasText(remotePic)) {
                existing.setPicUrl(resolveCover(remotePic, rule));
            }
            bookStoreService.fillCategory(existing, request.getCategoryId(), request.getWorkDirection());
            bookStoreService.updateBook(existing);
        }
        return existing;
    }

    /**
     * 抓取并解析单个章节
     *
     * @param link    目录中的章节链接
     * @param rule    站点规则
     * @param filters 已编译的广告过滤正则
     * @return 解析好的章节（标题 + 清洗后的纯文本正文）
     * @throws IllegalStateException 正文为空时抛出
     */
    private ParsedChapter crawlChapter(ChapterLink link, CrawlRule rule, List<Pattern> filters) {
        Document doc = httpFetcher.fetchPage(link.url(), rule);
        // 标题：优先用章节页里的标题选择器，取不到用目录里的链接文字
        String title = extract(doc, rule.getChapter().getTitleSelector(), null);
        if (!StringUtils.hasText(title)) {
            title = link.title();
        }
        // 正文：支持章节内分页合并
        String rawText = extractContent(doc, rule);
        String cleanText = ContentFormatUtil.cleanContent(rawText, filters, title);
        if (cleanText.isBlank()) {
            throw new IllegalStateException("正文为空，请检查站点规则 chapter.content-selector");
        }
        return new ParsedChapter(title, cleanText);
    }

    /**
     * 提取章节正文（把正文容器转成保留换行的纯文本，支持"下一页"分页合并）
     *
     * @param firstDoc 章节第一页的文档
     * @param rule     站点规则
     * @return 正文纯文本（未清洗）
     * @throws IllegalStateException 正文容器不存在时抛出
     */
    private String extractContent(Document firstDoc, CrawlRule rule) {
        CrawlRule.ChapterRule chapterRule = rule.getChapter();
        // 配置项空值兜底
        int maxPages = chapterRule.getMaxPages() == null ? 20 : chapterRule.getMaxPages();
        String nextPageText = StringUtils.hasText(chapterRule.getNextPageText())
                ? chapterRule.getNextPageText() : "下一页";
        StringBuilder builder = new StringBuilder();
        Document doc = firstDoc;
        int pages = 0;
        while (true) {
            pages++;
            Element content = doc.selectFirst(chapterRule.getContentSelector());
            if (content == null) {
                throw new IllegalStateException("正文容器未找到（" + chapterRule.getContentSelector() + "）");
            }
            // 先移除脚本/广告等无关节点
            if (StringUtils.hasText(chapterRule.getRemoveSelectors())) {
                content.select(chapterRule.getRemoveSelectors()).remove();
            }
            builder.append(htmlToText(content)).append('\n');
            // 未配置分页选择器或达到翻页上限则结束
            if (!StringUtils.hasText(chapterRule.getNextPageSelector()) || pages >= maxPages) {
                break;
            }
            // 找文字包含"下一页"关键字的链接（避免误跟"下一章"）
            Element next = doc.select(chapterRule.getNextPageSelector()).stream()
                    .filter(a -> a.text().contains(nextPageText))
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                break;
            }
            String href = next.absUrl("href");
            if (!StringUtils.hasText(href)) {
                break;
            }
            // 翻页间同样限速
            sleepQuietly(rule.getIntervalMs());
            doc = httpFetcher.fetchPage(href, rule);
        }
        return builder.toString();
    }

    /**
     * 把正文容器的 HTML 转成保留段落换行的纯文本
     *
     * @param content 正文容器元素
     * @return 纯文本（段落以 \n 分隔）
     */
    private String htmlToText(Element content) {
        String html = content.html();
        // 换行类标签统一转成换行符
        html = html.replaceAll("(?i)<br[^>]*>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)<p[^>]*>", "");
        // 交给 Jsoup 去掉剩余标签并反转义 HTML 实体，wholeText 会保留我们插入的换行
        return Jsoup.parse(html).wholeText();
    }

    /**
     * 解析章节目录
     *
     * @param doc  书籍目录页文档
     * @param rule 站点规则
     * @return 章节链接列表（已按规则跳过头部、去重）
     */
    private List<ChapterLink> parseToc(Document doc, CrawlRule rule) {
        CrawlRule.TocRule tocRule = rule.getToc();
        List<ChapterLink> items = new ArrayList<>();
        // 抽取目录里的全部章节链接（absUrl 转绝对地址）
        for (Element a : doc.select(tocRule.getItemSelector())) {
            String url = a.absUrl("href");
            if (!StringUtils.hasText(url)) {
                continue;
            }
            items.add(new ChapterLink(a.text().trim(), url));
        }
        // 跳过目录最前面的 N 条
        int skip = tocRule.getSkip() == null ? 0 : tocRule.getSkip();
        if (skip > 0) {
            items = skip >= items.size() ? new ArrayList<>() : new ArrayList<>(items.subList(skip, items.size()));
        }
        // 按URL去重：同一URL保留最后一次出现（去掉目录顶部"最新章节"重复块）
        if (Boolean.TRUE.equals(tocRule.getDedupe())) {
            Map<String, ChapterLink> map = new LinkedHashMap<>();
            for (ChapterLink item : items) {
                map.remove(item.url());
                map.put(item.url(), item);
            }
            items = new ArrayList<>(map.values());
        }
        return items;
    }

    /**
     * 提取封面图片的远程地址
     *
     * @param doc  书籍页文档
     * @param rule 站点规则
     * @return 封面绝对地址，取不到返回 null
     */
    private String extractPicUrl(Document doc, CrawlRule rule) {
        CrawlRule.BookRule bookRule = rule.getBook();
        if (!StringUtils.hasText(bookRule.getPicSelector())) {
            return null;
        }
        Element img = doc.selectFirst(bookRule.getPicSelector());
        if (img == null) {
            return null;
        }
        // 用 abs: 前缀取绝对地址
        String attr = StringUtils.hasText(bookRule.getPicAttr()) ? bookRule.getPicAttr() : "src";
        String url = img.absUrl(attr.startsWith("abs:") ? attr.substring(4) : attr);
        return StringUtils.hasText(url) ? url : null;
    }

    /**
     * 处理封面：按配置决定直接存远程地址，还是下载到本地后存本地路径
     *
     * @param remotePicUrl 封面远程地址（可空）
     * @param rule         站点规则
     * @return 写入 book_info.pic_url 的地址
     */
    private String resolveCover(String remotePicUrl, CrawlRule rule) {
        if (!StringUtils.hasText(remotePicUrl)) {
            return properties.getDefaultPicUrl();
        }
        // 不下载时直接存远程地址
        if (!Boolean.TRUE.equals(properties.getDownloadCover())) {
            return ContentFormatUtil.truncate(remotePicUrl, 200);
        }
        try {
            byte[] data = httpFetcher.fetchBytes(remotePicUrl, rule);
            // 从URL里取扩展名，取不到默认 .jpg
            String ext = ".jpg";
            String path = remotePicUrl.toLowerCase(Locale.ROOT);
            for (String candidate : new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp"}) {
                if (path.contains(candidate)) {
                    ext = candidate;
                    break;
                }
            }
            String fileName = "cover_" + System.currentTimeMillis() + ext;
            Path dir = Paths.get(properties.getCoverSavePath());
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), data);
            return ContentFormatUtil.truncate(properties.getCoverUrlPrefix() + fileName, 200);
        } catch (Exception e) {
            // 封面下载失败不影响主流程，退回存远程地址
            log.warn("封面下载失败，改存远程地址：{}", remotePicUrl, e);
            return ContentFormatUtil.truncate(remotePicUrl, 200);
        }
    }

    /**
     * 按"选择器 + 可选正则"从文档中提取文本
     *
     * @param doc      页面文档
     * @param selector CSS 选择器（空则返回 null）
     * @param regex    二次提取正则（可空；有捕获分组取第1个分组，无分组取整体匹配）
     * @return 提取并去除首尾空白后的文本，取不到返回 null
     */
    private String extract(Document doc, String selector, String regex) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        Element element = doc.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String text = ContentFormatUtil.stripWide(element.text());
        // 配置了正则时做二次提取
        if (StringUtils.hasText(regex)) {
            Matcher matcher = Pattern.compile(regex.trim()).matcher(text);
            if (matcher.find()) {
                text = matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
            } else {
                return null;
            }
        }
        return ContentFormatUtil.stripWide(text);
    }

    /**
     * 预编译广告过滤正则（无效正则跳过并告警，不影响任务执行）
     *
     * @param regexes 正则字符串列表
     * @return 已编译的正则列表
     */
    private List<Pattern> compileFilters(List<String> regexes) {
        List<Pattern> patterns = new ArrayList<>();
        if (regexes == null) {
            return patterns;
        }
        for (String regex : regexes) {
            if (!StringUtils.hasText(regex)) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(regex.trim()));
            } catch (Exception e) {
                log.warn("忽略无效的广告过滤正则：{}", regex);
            }
        }
        return patterns;
    }

    /**
     * 静默休眠（吞掉中断异常并恢复中断标记）
     *
     * @param millis 休眠毫秒数（null 或非正数时不休眠）
     */
    private void sleepQuietly(Long millis) {
        if (millis == null || millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
