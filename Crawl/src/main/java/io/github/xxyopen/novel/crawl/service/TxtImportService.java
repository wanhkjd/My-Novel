package io.github.xxyopen.novel.crawl.service;

import io.github.xxyopen.novel.crawl.config.CrawlProperties;
import io.github.xxyopen.novel.crawl.dto.ParsedChapter;
import io.github.xxyopen.novel.crawl.dto.TxtImportRequest;
import io.github.xxyopen.novel.crawl.entity.BookInfo;
import io.github.xxyopen.novel.crawl.task.TaskInfo;
import io.github.xxyopen.novel.crawl.task.TaskManager;
import io.github.xxyopen.novel.crawl.util.EncodingUtil;
import io.github.xxyopen.novel.crawl.util.TxtChapterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 导入服务：解析本地小说 TXT 文件（自动识别编码、按章节标题切分）并写入数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TxtImportService {

    // 文件名解析规则一：《书名》作者：某某
    private static final Pattern FILE_NAME_PATTERN_1 =
            Pattern.compile("《(.+?)》\\s*(?:作\\s*者\\s*[:：]?\\s*(.+))?");

    // 文件名解析规则二：书名 作者：某某
    private static final Pattern FILE_NAME_PATTERN_2 =
            Pattern.compile("^(.+?)\\s*作\\s*者\\s*[:：]\\s*(.+)$");

    // 爬虫与导入配置
    private final CrawlProperties properties;

    // 书籍入库服务
    private final BookStoreService bookStoreService;

    // 任务管理器
    private final TaskManager taskManager;

    // 后台任务线程池
    private final ThreadPoolTaskExecutor crawlExecutor;

    /**
     * 提交 TXT 导入任务（异步执行，立即返回任务信息供轮询进度）
     *
     * @param data     TXT 文件字节内容
     * @param fileName 上传的原始文件名（用于解析书名/作者）
     * @param request  导入参数
     * @return 新建的任务信息
     * @throws IllegalArgumentException 文件为空时抛出
     */
    public TaskInfo submit(byte[] data, String fileName, TxtImportRequest request) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("上传的文件内容为空");
        }
        TaskInfo task = taskManager.create("TXT",
                StringUtils.hasText(request.getBookName()) ? request.getBookName() : fileName);
        try {
            crawlExecutor.execute(() -> doImport(task, data, fileName, request));
        } catch (RuntimeException e) {
            // 线程池队列已满等提交失败场景：终结任务，避免留下永远RUNNING的幽灵任务
            task.markFailed("任务提交失败：执行队列已满，请稍后重试");
            throw new IllegalStateException("任务队列已满，请稍后重试");
        }
        return task;
    }

    /**
     * 执行 TXT 导入（在后台线程池中运行）
     *
     * @param task     任务信息（过程中不断更新进度）
     * @param data     TXT 文件字节内容
     * @param fileName 原始文件名
     * @param request  导入参数
     */
    private void doImport(TaskInfo task, byte[] data, String fileName, TxtImportRequest request) {
        Long bookId = null;
        // 已成功锁定的书名/作者（用于 finally 中释放锁）
        String lockedBookName = null;
        String lockedAuthorName = null;
        try {
            // 1. 自动识别编码并解码
            String text = EncodingUtil.decode(data);
            // 2. 确定书名与作者（参数优先，其次从文件名解析）
            String[] nameAndAuthor = parseFileName(fileName);
            String bookName = firstNonBlank(request.getBookName(), nameAndAuthor[0]);
            if (!StringUtils.hasText(bookName)) {
                throw new IllegalArgumentException("无法确定书名，请填写书名参数");
            }
            String authorName = firstNonBlank(request.getAuthorName(), nameAndAuthor[1], "佚名");
            task.setBookName(bookName);
            // 同一本书禁止并发任务，避免章节号交叉冲突
            if (!bookStoreService.tryLockBook(bookName, authorName)) {
                throw new IllegalStateException("《" + bookName + "》已有任务正在执行，请等待其结束后再提交");
            }
            lockedBookName = bookName;
            lockedAuthorName = authorName;
            // 3. 按章节标题切分全文
            TxtChapterParser.ParseResult parsed = TxtChapterParser.parse(text, request.getChapterRegex());
            List<ParsedChapter> chapters = parsed.getChapters();
            if (chapters.isEmpty()) {
                // 没有识别到章节标题：按配置决定自动分章还是报错
                if (Boolean.TRUE.equals(properties.getAutoSplitWhenNoChapter())) {
                    // 配置项空值兜底
                    int splitWords = properties.getAutoSplitWordCount() == null ? 3000 : properties.getAutoSplitWordCount();
                    chapters = TxtChapterParser.autoSplit(text, splitWords);
                    log.info("《{}》未识别到章节标题，已按每章约 {} 字自动分章，共 {} 章",
                            bookName, splitWords, chapters.size());
                } else {
                    throw new IllegalArgumentException("未识别到章节标题，可传入自定义章节正则（chapterRegex）后重试");
                }
            }
            if (chapters.isEmpty()) {
                throw new IllegalArgumentException("文件内容为空，没有可导入的章节");
            }
            // 4. 简介：参数 > TXT开头文字 > 默认值（建书时兜底）
            String bookDesc = firstNonBlank(request.getBookDesc(), TxtChapterParser.extractDesc(parsed.getPreface()));
            // 5. 建书或定位已有书籍
            BookInfo book = bookStoreService.findByNameAndAuthor(bookName, authorName);
            if (book == null) {
                BookInfo newBook = new BookInfo();
                newBook.setBookName(bookName);
                newBook.setAuthorName(authorName);
                newBook.setBookDesc(bookDesc);
                newBook.setBookStatus(request.getBookStatus() == null ? 1 : request.getBookStatus());
                bookStoreService.fillCategory(newBook, request.getCategoryId(), request.getWorkDirection());
                book = bookStoreService.createBook(newBook);
            } else if (Boolean.TRUE.equals(request.getOverride())) {
                // 覆盖重导：清空旧章节
                bookStoreService.clearChapters(book.getId());
            }
            bookId = book.getId();
            task.setBookId(String.valueOf(bookId));
            // 6. 增量处理：跳过已导入的章节数（用于同一本书的更新版TXT续传）
            int startNum = bookStoreService.getMaxChapterNum(bookId);
            if (startNum >= chapters.size()) {
                task.markSuccess("没有新章节需要导入（本地已有 " + startNum + " 章，文件共 " + chapters.size() + " 章）");
                return;
            }
            List<ParsedChapter> pending = chapters.subList(startNum, chapters.size());
            task.setTotal(pending.size());
            // 7. 分批入库（每批一个事务）
            int batchSize = properties.getBatchSize() == null ? 100 : Math.max(1, properties.getBatchSize());
            int isVip = book.getIsVip() == null ? 0 : book.getIsVip();
            int nextNum = startNum;
            for (int i = 0; i < pending.size(); i += batchSize) {
                // 响应取消请求
                if (task.isCancelRequested()) {
                    task.markCancelled("任务已取消，本次已入库 " + task.getSaved().get() + " 章");
                    bookStoreService.refreshBookStatsQuietly(bookId);
                    return;
                }
                List<ParsedChapter> batch = pending.subList(i, Math.min(i + batchSize, pending.size()));
                bookStoreService.saveChapterBatch(bookId, nextNum, isVip, batch, properties.getContentFormat());
                nextNum += batch.size();
                task.getDone().addAndGet(batch.size());
                task.getSaved().addAndGet(batch.size());
            }
            // 8. 收尾：刷新书籍统计
            bookStoreService.refreshBookStats(bookId);
            task.markSuccess("导入完成：本次入库 " + task.getSaved().get() + " 章");
            log.info("《{}》TXT 导入完成，入库 {} 章", bookName, task.getSaved().get());
        } catch (Exception e) {
            log.error("TXT 导入任务失败：{}", fileName, e);
            // 先终结任务状态（避免统计刷新再出错时任务卡在RUNNING），再尽力刷新统计
            task.markFailed(e.getMessage());
            bookStoreService.refreshBookStatsQuietly(bookId);
        } finally {
            // 释放本书的任务锁
            if (lockedBookName != null) {
                bookStoreService.unlockBook(lockedBookName, lockedAuthorName);
            }
        }
    }

    /**
     * 从文件名解析书名与作者
     *
     * @param fileName 原始文件名（可带路径与扩展名）
     * @return 长度为 2 的数组：[书名, 作者]，解析不到的位置为 null
     */
    private String[] parseFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return new String[]{null, null};
        }
        // 去掉路径与扩展名
        String base = fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.strip();
        // 规则一：《书名》作者：某某
        Matcher matcher = FILE_NAME_PATTERN_1.matcher(base);
        if (matcher.find()) {
            return new String[]{matcher.group(1).strip(), strip(matcher.group(2))};
        }
        // 规则二：书名 作者：某某
        matcher = FILE_NAME_PATTERN_2.matcher(base);
        if (matcher.find()) {
            return new String[]{matcher.group(1).strip(), strip(matcher.group(2))};
        }
        // 都不匹配：整个文件名作为书名
        return new String[]{base.isBlank() ? null : base, null};
    }

    /**
     * 安全去除首尾空白（null 直接返回 null）
     *
     * @param text 原字符串
     * @return 去除首尾空白后的字符串
     */
    private String strip(String text) {
        return text == null ? null : text.strip();
    }

    /**
     * 返回第一个非空白字符串
     *
     * @param candidates 候选字符串
     * @return 第一个非空白的候选值，全为空返回 null
     */
    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.strip();
            }
        }
        return null;
    }

}
