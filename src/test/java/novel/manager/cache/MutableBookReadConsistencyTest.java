package novel.manager.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import novel.core.constant.CacheConsts;
import novel.dao.entity.BookChapter;
import novel.dao.entity.BookContent;
import novel.dao.entity.BookInfo;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookContentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.resp.BookChapterRespDto;
import novel.dto.resp.BookInfoRespDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Exercises real Spring cache proxies with the production cache manager bean names.
 * Only mapper mocks and in-memory caches are registered; no external services are started.
 */
@ExtendWith(MockitoExtension.class)
class MutableBookReadConsistencyTest {

    private static final Long BOOK_ID = 10L;
    private static final Long CHAPTER_ID = 20L;

    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookContentMapper bookContentMapper;

    private AnnotationConfigApplicationContext context;
    private BookInfoCacheManager books;
    private BookChapterCacheManager chapters;
    private BookContentCacheManager contents;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean(CacheConsts.CAFFEINE_CACHE_MANAGER, ConcurrentMapCacheManager.class,
            () -> new ConcurrentMapCacheManager(CacheConsts.BOOK_INFO_CACHE_NAME,
                CacheConsts.BOOK_CHAPTER_CACHE_NAME, CacheConsts.LAST_UPDATE_BOOK_ID_LIST_CACHE_NAME),
            definition -> definition.setPrimary(true));
        context.registerBean(CacheConsts.REDIS_CACHE_MANAGER, ConcurrentMapCacheManager.class,
            () -> new ConcurrentMapCacheManager(CacheConsts.BOOK_CONTENT_CACHE_NAME));
        context.registerBean(BookInfoCacheManager.class,
            () -> new BookInfoCacheManager(bookInfoMapper, bookChapterMapper));
        context.registerBean(BookChapterCacheManager.class,
            () -> new BookChapterCacheManager(bookChapterMapper));
        context.registerBean(BookContentCacheManager.class,
            () -> new BookContentCacheManager(bookContentMapper));
        context.refresh();
        books = context.getBean(BookInfoCacheManager.class);
        chapters = context.getBean(BookChapterCacheManager.class);
        contents = context.getBean(BookContentCacheManager.class);
        assertThat(AopUtils.isAopProxy(books)).isTrue();
        assertThat(AopUtils.isAopProxy(chapters)).isTrue();
        assertThat(AopUtils.isAopProxy(contents)).isTrue();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Detail.class)
    void seededLegacyCacheIsIgnoredAcrossSequentialEditsAndDeletion(Detail detail) {
        AtomicReference<String> stored = new AtomicReference<>("original");
        stubDatabaseRead(detail, invocation -> stored.get());
        Cache cache = cache(detail);
        Object legacyValue = legacyValue(detail);
        cache.put(detail.key, legacyValue);

        assertThat(read(detail)).isEqualTo("original");
        stored.set("first edit");
        assertThat(read(detail)).isEqualTo("first edit");
        stored.set("second edit");
        assertThat(read(detail)).isEqualTo("second edit");
        stored.set(null);
        assertThat(read(detail)).isNull();
        assertThat(read(detail)).isNull();

        // No eviction is required, even for stale entries left on another application instance.
        assertThat(cache.get(detail.key).get()).isSameAs(legacyValue);
        verifyDatabaseReads(detail, 5);
    }

    @ParameterizedTest
    @EnumSource(Detail.class)
    void coldReadsNeverPopulateDetailCacheIncludingMissingRows(Detail detail) {
        AtomicReference<String> stored = new AtomicReference<>("original");
        stubDatabaseRead(detail, invocation -> stored.get());
        Cache cache = cache(detail);

        assertThat(read(detail)).isEqualTo("original");
        assertThat(cache.get(detail.key)).isNull();
        assertThat(read(detail)).isEqualTo("original");
        assertThat(cache.get(detail.key)).isNull();
        stored.set(null);
        assertThat(read(detail)).isNull();
        assertThat(cache.get(detail.key)).isNull();
        stored.set("recreated");
        assertThat(read(detail)).isEqualTo("recreated");
        assertThat(cache.get(detail.key)).isNull();
        verifyDatabaseReads(detail, 4);
    }

    @ParameterizedTest
    @EnumSource(Detail.class)
    void delayedOldLoadCannotRefillCacheAfterEvictionAndEdit(Detail detail) throws Exception {
        AtomicReference<String> stored = new AtomicReference<>("original");
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch oldValueCaptured = new CountDownLatch(1);
        CountDownLatch releaseOldLoad = new CountDownLatch(1);
        stubDatabaseRead(detail, invocation -> {
            String snapshot = stored.get();
            if (loads.incrementAndGet() == 1) {
                oldValueCaptured.countDown();
                assertThat(releaseOldLoad.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return snapshot;
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> oldLoad = executor.submit(() -> read(detail));
            assertThat(oldValueCaptured.await(10, TimeUnit.SECONDS)).isTrue();
            // Reproduce eviction before an edit becomes visible to subsequent DB reads.
            evict(detail);
            stored.set("edited");
            assertThat(read(detail)).isEqualTo("edited");

            releaseOldLoad.countDown();
            // An already-running read may return its old snapshot, but must not publish it.
            assertThat(oldLoad.get(10, TimeUnit.SECONDS)).isEqualTo("original");
            // The important behavior is the next response, not just whether a cache entry exists.
            assertThat(read(detail)).isEqualTo("edited");
            assertThat(cache(detail).get(detail.key)).isNull();
            stored.set(null);
            assertThat(read(detail)).isNull();
            assertThat(cache(detail).get(detail.key)).isNull();
            verifyDatabaseReads(detail, 4);
        } finally {
            releaseOldLoad.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(Detail.class)
    void legacyEvictionEntryPointsStillUseSpringCacheAdvice(Detail detail) {
        Cache cache = cache(detail);
        cache.put(detail.key, legacyValue(detail));

        evict(detail);

        assertThat(cache.get(detail.key)).isNull();
    }

    @Test
    void latestUpdateIdListRemainsCachedAndIsNotEvictedWithDetails() {
        Long categoryId = 30L;
        BookInfo original = databaseBook("original");
        BookInfo changed = databaseBook("changed");
        changed.setId(11L);
        when(bookInfoMapper.selectList(any())).thenReturn(List.of(original), List.of(changed));

        assertThat(books.getLastUpdateIdList(categoryId)).containsExactly(BOOK_ID);
        for (Detail detail : Detail.values()) {
            evict(detail);
        }
        assertThat(books.getLastUpdateIdList(categoryId)).containsExactly(BOOK_ID);
        Cache cache = context.getBean(CacheConsts.CAFFEINE_CACHE_MANAGER, CacheManager.class)
            .getCache(CacheConsts.LAST_UPDATE_BOOK_ID_LIST_CACHE_NAME);
        assertThat(cache).isNotNull();
        assertThat(cache.get(categoryId).get()).isEqualTo(List.of(BOOK_ID));
        verify(bookInfoMapper).selectList(any());
    }

    private void stubDatabaseRead(Detail detail, Answer<String> answer) {
        switch (detail) {
            case BOOK_INFO -> when(bookInfoMapper.selectById(BOOK_ID))
                .thenAnswer(invocation -> databaseBook(answer.answer(invocation)));
            case CHAPTER -> when(bookChapterMapper.selectById(CHAPTER_ID))
                .thenAnswer(invocation -> databaseChapter(answer.answer(invocation)));
            case CONTENT -> when(bookContentMapper.selectOne(any()))
                .thenAnswer(invocation -> databaseContent(answer.answer(invocation)));
        }
    }

    private void verifyDatabaseReads(Detail detail, int count) {
        switch (detail) {
            case BOOK_INFO -> verify(bookInfoMapper, times(count)).selectById(BOOK_ID);
            case CHAPTER -> verify(bookChapterMapper, times(count)).selectById(CHAPTER_ID);
            case CONTENT -> verify(bookContentMapper, times(count)).selectOne(any());
        }
    }

    private String read(Detail detail) {
        return switch (detail) {
            case BOOK_INFO -> {
                BookInfoRespDto book = books.getBookInfo(BOOK_ID);
                yield book == null ? null : book.getBookName();
            }
            case CHAPTER -> {
                BookChapterRespDto chapter = chapters.getChapter(CHAPTER_ID);
                yield chapter == null ? null : chapter.getChapterName();
            }
            case CONTENT -> contents.getBookContent(CHAPTER_ID);
        };
    }

    private Object legacyValue(Detail detail) {
        return switch (detail) {
            case BOOK_INFO -> BookInfoRespDto.builder().id(BOOK_ID).bookName("legacy").build();
            case CHAPTER -> BookChapterRespDto.builder().id(CHAPTER_ID).chapterName("legacy").build();
            case CONTENT -> "legacy";
        };
    }

    private void evict(Detail detail) {
        switch (detail) {
            case BOOK_INFO -> books.evictBookInfoCache(BOOK_ID);
            case CHAPTER -> chapters.evictBookChapterCache(CHAPTER_ID);
            case CONTENT -> contents.evictBookContentCache(CHAPTER_ID);
        }
    }

    private Cache cache(Detail detail) {
        Cache cache = context.getBean(detail.managerName, CacheManager.class).getCache(detail.cacheName);
        assertThat(cache).isNotNull();
        return cache;
    }

    private static BookInfo databaseBook(String name) {
        if (name == null) {
            return null;
        }
        BookInfo book = new BookInfo();
        book.setId(BOOK_ID);
        book.setBookName(name);
        return book;
    }

    private static BookChapter databaseChapter(String name) {
        if (name == null) {
            return null;
        }
        BookChapter chapter = new BookChapter();
        chapter.setId(CHAPTER_ID);
        chapter.setBookId(BOOK_ID);
        chapter.setChapterName(name);
        return chapter;
    }

    private static BookContent databaseContent(String text) {
        if (text == null) {
            return null;
        }
        BookContent content = new BookContent();
        content.setChapterId(CHAPTER_ID);
        content.setContent(text);
        return content;
    }

    private enum Detail {
        BOOK_INFO(CacheConsts.CAFFEINE_CACHE_MANAGER, CacheConsts.BOOK_INFO_CACHE_NAME, BOOK_ID),
        CHAPTER(CacheConsts.CAFFEINE_CACHE_MANAGER, CacheConsts.BOOK_CHAPTER_CACHE_NAME, CHAPTER_ID),
        CONTENT(CacheConsts.REDIS_CACHE_MANAGER, CacheConsts.BOOK_CONTENT_CACHE_NAME, CHAPTER_ID);

        private final String managerName;
        private final String cacheName;
        private final Long key;

        Detail(String managerName, String cacheName, Long key) {
            this.managerName = managerName;
            this.cacheName = cacheName;
            this.key = key;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    static class Config {
    }
}
