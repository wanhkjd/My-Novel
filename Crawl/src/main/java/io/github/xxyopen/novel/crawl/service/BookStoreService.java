package io.github.xxyopen.novel.crawl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xxyopen.novel.crawl.config.CrawlProperties;
import io.github.xxyopen.novel.crawl.dto.ParsedChapter;
import io.github.xxyopen.novel.crawl.entity.BookCategory;
import io.github.xxyopen.novel.crawl.entity.BookChapter;
import io.github.xxyopen.novel.crawl.entity.BookContent;
import io.github.xxyopen.novel.crawl.entity.BookInfo;
import io.github.xxyopen.novel.crawl.mapper.BookCategoryMapper;
import io.github.xxyopen.novel.crawl.mapper.BookChapterMapper;
import io.github.xxyopen.novel.crawl.mapper.BookContentMapper;
import io.github.xxyopen.novel.crawl.mapper.BookInfoMapper;
import io.github.xxyopen.novel.crawl.util.ContentFormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 书籍入库服务：爬虫与 TXT 导入共用的数据库读写逻辑
 *
 * <p>包含：按书名+作者去重、创建书籍、批量保存章节与内容、清空章节、刷新书籍统计信息、
 * 同一本书的任务互斥锁。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookStoreService {

    // 章节号上限（book_chapter.chapter_num 为 smallint unsigned）
    private static final int MAX_CHAPTER_NUM = 65535;

    // 书名/作者名入库长度上限（book_info.book_name / author_name 均为 varchar(50)）
    private static final int NAME_MAX_LEN = 50;

    // 正在执行任务的书籍集合（key = 书名|作者名），防止同一本书被并发写入
    private final Set<String> runningBooks = ConcurrentHashMap.newKeySet();

    // 小说信息 Mapper
    private final BookInfoMapper bookInfoMapper;

    // 小说章节 Mapper
    private final BookChapterMapper bookChapterMapper;

    // 小说内容 Mapper
    private final BookContentMapper bookContentMapper;

    // 小说类别 Mapper
    private final BookCategoryMapper bookCategoryMapper;

    // 爬虫与导入配置
    private final CrawlProperties properties;

    /**
     * 按书名 + 作者名查找已存在的小说（book_info 表有 (book_name, author_name) 唯一键）
     *
     * <p>查询前会按与入库一致的规则截断到 50 字符，保证超长书名也能命中已有记录。</p>
     *
     * @param bookName   书名
     * @param authorName 作者名
     * @return 已存在的小说信息，不存在返回 null
     */
    public BookInfo findByNameAndAuthor(String bookName, String authorName) {
        return bookInfoMapper.selectOne(new LambdaQueryWrapper<BookInfo>()
                .eq(BookInfo::getBookName, ContentFormatUtil.truncate(bookName, NAME_MAX_LEN))
                .eq(BookInfo::getAuthorName, ContentFormatUtil.truncate(authorName, NAME_MAX_LEN))
                .last("LIMIT 1"));
    }

    /**
     * 尝试锁定一本书（同一本书同时只允许一个爬取/导入任务在执行）
     *
     * @param bookName   书名
     * @param authorName 作者名
     * @return true-锁定成功 false-该书已有任务在执行
     */
    public boolean tryLockBook(String bookName, String authorName) {
        return runningBooks.add(bookKey(bookName, authorName));
    }

    /**
     * 释放一本书的任务锁（任务结束时在 finally 中调用）
     *
     * @param bookName   书名
     * @param authorName 作者名
     */
    public void unlockBook(String bookName, String authorName) {
        runningBooks.remove(bookKey(bookName, authorName));
    }

    /**
     * 生成书籍锁的 key（与入库规则一致截断，避免超长名字绕过锁）
     *
     * @param bookName   书名
     * @param authorName 作者名
     * @return 锁 key
     */
    private String bookKey(String bookName, String authorName) {
        return ContentFormatUtil.truncate(bookName, NAME_MAX_LEN) + "|"
                + ContentFormatUtil.truncate(authorName, NAME_MAX_LEN);
    }

    /**
     * 按ID查询小说类别
     *
     * @param categoryId 类别ID
     * @return 类别信息，ID为空或不存在返回 null
     */
    public BookCategory getCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return bookCategoryMapper.selectById(categoryId);
    }

    /**
     * 查询全部小说类别（按作品方向、排序号排列）
     *
     * @return 类别列表
     */
    public List<BookCategory> listCategories() {
        return bookCategoryMapper.selectList(new LambdaQueryWrapper<BookCategory>()
                .orderByAsc(BookCategory::getWorkDirection)
                .orderByAsc(BookCategory::getSort));
    }

    /**
     * 给书籍填充类别信息（传了类别ID时以类别表的名称与作品方向为准）
     *
     * @param book          待填充的书籍
     * @param categoryId    类别ID（可空）
     * @param workDirection 作品方向（可空，类别查不到时使用）
     */
    public void fillCategory(BookInfo book, Long categoryId, Integer workDirection) {
        BookCategory category = getCategory(categoryId);
        if (category != null) {
            // 类别存在：类别ID/类别名/作品方向都以类别表为准
            book.setCategoryId(category.getId());
            book.setCategoryName(category.getName());
            book.setWorkDirection(category.getWorkDirection());
        } else {
            book.setWorkDirection(workDirection);
        }
    }

    /**
     * 创建新书：补齐默认值（作家ID、评分、封面、简介等）后插入 book_info
     *
     * @param book 已填好书名/作者名等基础信息的书籍对象
     * @return 插入后的书籍对象（主键已回填）
     */
    public BookInfo createBook(BookInfo book) {
        LocalDateTime now = LocalDateTime.now();
        // 补齐默认值
        if (book.getAuthorId() == null) {
            book.setAuthorId(properties.getDefaultAuthorId());
        }
        if (!StringUtils.hasText(book.getPicUrl())) {
            book.setPicUrl(properties.getDefaultPicUrl());
        }
        if (!StringUtils.hasText(book.getBookDesc())) {
            book.setBookDesc(properties.getDefaultBookDesc());
        }
        if (book.getScore() == null) {
            book.setScore(properties.getDefaultScore());
        }
        if (book.getBookStatus() == null) {
            book.setBookStatus(0);
        }
        if (book.getIsVip() == null) {
            book.setIsVip(0);
        }
        // 按数据库字段长度截断，避免超长报错
        book.setBookName(ContentFormatUtil.truncate(book.getBookName(), 50));
        book.setAuthorName(ContentFormatUtil.truncate(book.getAuthorName(), 50));
        book.setCategoryName(ContentFormatUtil.truncate(book.getCategoryName(), 50));
        book.setPicUrl(ContentFormatUtil.truncate(book.getPicUrl(), 200));
        book.setBookDesc(ContentFormatUtil.truncate(book.getBookDesc(), 2000));
        book.setCreateTime(now);
        book.setUpdateTime(now);
        bookInfoMapper.insert(book);
        log.info("新建书籍：{}（ID={}）", book.getBookName(), book.getId());
        return book;
    }

    /**
     * 更新书籍信息（覆盖重导时刷新简介/封面/状态等）
     *
     * @param book 待更新的书籍对象（须带主键）
     */
    public void updateBook(BookInfo book) {
        book.setBookDesc(ContentFormatUtil.truncate(book.getBookDesc(), 2000));
        book.setPicUrl(ContentFormatUtil.truncate(book.getPicUrl(), 200));
        book.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(book);
    }

    /**
     * 查询某本小说当前最大章节号（用于增量续传的起点）
     *
     * @param bookId 小说ID
     * @return 最大章节号，无章节返回 0
     */
    public int getMaxChapterNum(Long bookId) {
        return bookChapterMapper.selectMaxChapterNum(bookId);
    }

    /**
     * 批量保存章节及内容（单事务）：章节号从 startNum+1 开始依次递增
     *
     * @param bookId        小说ID
     * @param startNum      起始章节号（新章节号从 startNum+1 开始）
     * @param isVip         是否收费章节;1-收费 0-免费
     * @param chapters      解析好的章节列表
     * @param contentFormat 内容存储格式（html / text）
     * @return 实际入库的章节数
     * @throws IllegalStateException 章节号超过数据库上限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveChapterBatch(Long bookId, int startNum, int isVip, List<ParsedChapter> chapters, String contentFormat) {
        LocalDateTime now = LocalDateTime.now();
        int num = startNum;
        for (ParsedChapter parsed : chapters) {
            num++;
            // 章节号是 smallint unsigned，超限直接报错提示
            if (num > MAX_CHAPTER_NUM) {
                throw new IllegalStateException("章节号超过上限 " + MAX_CHAPTER_NUM + "，无法继续入库");
            }
            String plain = parsed.getContent() == null ? "" : parsed.getContent();
            // 先插入章节表（主键自增回填）
            BookChapter chapter = new BookChapter();
            chapter.setBookId(bookId);
            chapter.setChapterNum(num);
            chapter.setChapterName(ContentFormatUtil.truncate(
                    StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : ("第" + num + "章"), 100));
            chapter.setWordCount(ContentFormatUtil.countWords(plain));
            chapter.setIsVip(isVip);
            chapter.setCreateTime(now);
            chapter.setUpdateTime(now);
            bookChapterMapper.insert(chapter);
            // 再插入内容表（引用章节ID）
            BookContent content = new BookContent();
            content.setChapterId(chapter.getId());
            content.setContent(ContentFormatUtil.format(plain, contentFormat));
            content.setCreateTime(now);
            content.setUpdateTime(now);
            bookContentMapper.insert(content);
        }
        return chapters.size();
    }

    /**
     * 清空某本小说的全部章节与内容，并把书籍统计信息归零（覆盖重导前调用）
     *
     * @param bookId 小说ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearChapters(Long bookId) {
        // 先删内容（引用章节ID），再删章节
        int contentRows = bookContentMapper.deleteByBookId(bookId);
        int chapterRows = bookChapterMapper.deleteByBookId(bookId);
        bookInfoMapper.updateBookStats(bookId, 0, null, null, null);
        log.info("清空书籍章节：bookId={}，删除章节 {} 条、内容 {} 条", bookId, chapterRows, contentRows);
    }

    /**
     * 静默刷新书籍统计信息（刷新失败只记日志，不向上抛异常；用于失败/取消等兜底路径）
     *
     * @param bookId 小说ID（可为 null，为 null 时直接返回）
     */
    public void refreshBookStatsQuietly(Long bookId) {
        if (bookId == null) {
            return;
        }
        try {
            refreshBookStats(bookId);
        } catch (Exception e) {
            log.warn("刷新书籍统计失败：bookId={}", bookId, e);
        }
    }

    /**
     * 重算并刷新书籍统计信息（总字数、最新章节ID/名称/更新时间）
     *
     * @param bookId 小说ID
     */
    public void refreshBookStats(Long bookId) {
        long wordCount = bookChapterMapper.sumWordCount(bookId);
        BookChapter last = bookChapterMapper.selectLastChapter(bookId);
        if (last == null) {
            // 没有章节时统计清零
            bookInfoMapper.updateBookStats(bookId, 0, null, null, null);
            return;
        }
        // book_info.last_chapter_name 是 varchar(50)，需截断
        bookInfoMapper.updateBookStats(bookId, (int) wordCount, last.getId(),
                ContentFormatUtil.truncate(last.getChapterName(), 50),
                last.getUpdateTime() == null ? LocalDateTime.now() : last.getUpdateTime());
    }

}
