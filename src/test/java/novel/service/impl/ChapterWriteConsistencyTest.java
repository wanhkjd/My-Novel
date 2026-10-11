package novel.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import novel.core.auth.UserHolder;
import novel.core.common.exception.BusinessException;
import novel.core.common.resp.RestResp;
import novel.dao.entity.BookChapter;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookCommentMapper;
import novel.dao.mapper.BookContentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.req.ChapterAddReqDto;
import novel.dto.req.ChapterUpdateReqDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.BookCategoryCacheManager;
import novel.manager.cache.BookChapterCacheManager;
import novel.manager.cache.BookContentCacheManager;
import novel.manager.cache.BookInfoCacheManager;
import novel.manager.cache.BookRankCacheManager;
import novel.manager.dao.UserDaoManager;
import novel.manager.mq.AmqpMsgManager;
import novel.service.UserService;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Real Spring proxy / MyBatis / JDBC transaction tests with an isolated in-memory database.
 * No Boot context, component scan, application configuration, Redis, MQ, ES or ShardingSphere.
 * H2 uses MySQL syntax mode but READ_COMMITTED isolation: these tests do NOT establish MySQL
 * REPEATABLE_READ current-read, gap-lock or ShardingSphere routing/transaction correctness.
 * AmqpMsgManager is a mock; its actual afterCommit behavior belongs in a separate test.
 */
@Timeout(60)
@Execution(ExecutionMode.SAME_THREAD)
class ChapterWriteConsistencyTest {

    private static final long BOOK = 101L;
    private static final long OTHER_BOOK = 202L;
    private static final long OWNER = 10L;
    private static final long FIRST = 1001L;
    private static final long SECOND = 1002L;
    private static final long MISSING = 9999L;
    private static final long WAIT_SECONDS = 10L;
    private static final LocalDateTime FIXTURE_TIME = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
    private static final String BOOK_LOCK = BookInfoMapper.class.getName() + ".selectByIdForUpdate";
    private static final String CHAPTER_LOOKUP = BookChapterMapper.class.getName() + ".selectById";
    private static final String CHAPTER_LOCK = BookChapterMapper.class.getName() + ".selectByIdForUpdate";
    private static final String LAST_LOCK = BookChapterMapper.class.getName() + ".selectLastByBookIdForUpdate";

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private BookServiceImpl service;
    private SqlProbe probe;
    private AmqpMsgManager messages;
    private ExecutorService workers;

    @BeforeEach
    void setUp() {
        UserHolder.clear();
        UserHolder.setUserId(1L);
        UserHolder.setAuthorId(OWNER);
        ReadCommittedDataSource dataSource = new ReadCommittedDataSource();
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        probe = new SqlProbe();
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(SqlSessionFactory.class, () -> sqlSessionFactory(dataSource, probe));
        context.registerBean(SqlSessionTemplate.class,
            () -> new SqlSessionTemplate(context.getBean(SqlSessionFactory.class)));
        registerMapper(BookInfoMapper.class);
        registerMapper(BookChapterMapper.class);
        registerMapper(BookContentMapper.class);
        registerMock(BookCategoryCacheManager.class);
        registerMock(BookRankCacheManager.class);
        registerMock(BookInfoCacheManager.class);
        registerMock(BookChapterCacheManager.class);
        registerMock(BookContentCacheManager.class);
        registerMock(AuthorInfoCacheManager.class);
        registerMock(BookCommentMapper.class);
        registerMock(UserDaoManager.class);
        registerMock(AmqpMsgManager.class);
        // Class registration lets Spring resolve an additional local UserService constructor field.
        registerMock(UserService.class);
        context.registerBean(BookServiceImpl.class);
        context.refresh();
        service = context.getBean(BookServiceImpl.class);
        messages = context.getBean(AmqpMsgManager.class);
        workers = Executors.newFixedThreadPool(3);
        assertThat(AopUtils.isAopProxy(service)).as("service must run through the transaction proxy").isTrue();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        UserHolder.clear();
        if (probe != null) {
            probe.releaseGates();
        }
        boolean terminated = true;
        if (workers != null) {
            workers.shutdownNow();
            terminated = workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (jdbc != null) {
                jdbc.execute("SHUTDOWN");
            }
        }
        assertThat(terminated).as("all bounded worker tasks must finish before the next test").isTrue();
    }

    @Test
    void lockingStatementsBypassCachesAndDataSourceUsesReadCommitted() throws SQLException {
        try (Connection connection = context.getBean(DataSource.class).getConnection()) {
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
        org.apache.ibatis.session.Configuration configuration =
            context.getBean(SqlSessionFactory.class).getConfiguration();
        assertThat(configuration.getLocalCacheScope()).isEqualTo(LocalCacheScope.SESSION);
        for (String id : List.of(BOOK_LOCK, CHAPTER_LOCK, LAST_LOCK)) {
            MappedStatement statement = configuration.getMappedStatement(id);
            assertThat(statement.isUseCache()).as(id + " must bypass second-level cache").isFalse();
            assertThat(statement.isFlushCacheRequired()).as(id + " must invalidate session cache").isTrue();
            assertThat(statement.getBoundSql(Map.of("id", BOOK, "bookId", BOOK)).getSql())
                .containsIgnoringCase("FOR UPDATE");
        }
    }

    @ParameterizedTest(name = "add/add, initially populated = {0}")
    @ValueSource(booleans = {false, true})
    void concurrentAddsAllocateUniqueNumbersAndPreserveTotal(boolean populated) throws Exception {
        seedBook(BOOK, OWNER);
        if (populated) {
            seedChapter(BOOK, FIRST, 0, "existing", text(50));
        }
        BookGate gate = probe.gate("add-first", Set.of("add-second"));
        Future<RestResp<Void>> first = submit("add-first", () -> service.saveBookChapter(add(BOOK, "one", 73)));
        Future<RestResp<Void>> second;
        try {
            await(gate.held, "first writer acquired its book row lock");
            second = submit("add-second", () -> service.saveBookChapter(add(BOOK, "two", 91)));
            await(gate.followersAttempted, "second writer reached the same book lock");
            assertThat(second.isDone()).isFalse();
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertOk(get(second));
        List<ChapterRow> chapters = chapters(BOOK);
        assertThat(chapters).hasSize(populated ? 3 : 2);
        assertThat(chapters.stream().map(ChapterRow::number).toList())
            .containsExactlyElementsOf(populated ? List.of(0, 1, 2) : List.of(0, 1));
        assertThat(book(BOOK).words()).isEqualTo((populated ? 50 : 0) + 73 + 91);
        assertThat(chapters.stream().map(ChapterRow::content).toList()).contains(text(73), text(91));
        assertConsistent(BOOK);
        assertMutableCachesUnused();
    }

    @Test
    void concurrentEditsOfDifferentChaptersPreserveBothDeltas() throws Exception {
        seedTwoChapters();
        BookGate gate = probe.gate("edit-first", Set.of("edit-second"));
        Future<RestResp<Void>> first = submit("edit-first",
            () -> service.updateBookChapter(FIRST, edit("first-edited", 130)));
        Future<RestResp<Void>> second;
        try {
            await(gate.held, "first editor acquired the book lock");
            second = submit("edit-second", () -> service.updateBookChapter(SECOND, edit("last-edited", 170)));
            await(gate.followersAttempted, "other chapter editor reached the book lock");
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertOk(get(second));
        assertThat(book(BOOK).words()).isEqualTo(300);
        assertThat(chapters(BOOK).stream().map(ChapterRow::content).toList())
            .containsExactly(text(130), text(170));
        assertThat(book(BOOK).lastName()).isEqualTo("last-edited");
        assertConsistent(BOOK);
        assertMutableCachesUnused();
    }

    @Test
    void sameChapterEditRereadsAfterWaitingInsteadOfUsingLocatorWordCount() throws Exception {
        seedTwoChapters();
        BookGate gate = probe.gate("edit-first", Set.of("edit-second"));
        Future<RestResp<Void>> first = submit("edit-first",
            () -> service.updateBookChapter(SECOND, edit("intermediate", 120)));
        Future<RestResp<Void>> second;
        try {
            await(gate.held, "first editor acquired the book lock");
            second = submit("edit-second", () -> service.updateBookChapter(SECOND, edit("final", 180)));
            await(gate.followersAttempted, "second editor has read the old locator");
            assertThat(probe.chapterWords("edit-second", CHAPTER_LOOKUP)).isEqualTo(80);
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertOk(get(second));
        assertThat(probe.chapterWords("edit-second", CHAPTER_LOCK)).isEqualTo(120);
        assertThat(book(BOOK).words()).isEqualTo(60 + 180);
        assertThat(chapters(BOOK).get(1).content()).isEqualTo(text(180));
        assertThat(book(BOOK).lastName()).isEqualTo("final");
        assertConsistent(BOOK);
    }

    @Test
    void deleteAfterConcurrentEditSubtractsTheCommittedChapterWordCount() throws Exception {
        seedTwoChapters();
        BookGate gate = probe.gate("editor", Set.of("deleter"));
        Future<RestResp<Void>> first = submit("editor",
            () -> service.updateBookChapter(SECOND, edit("expanded", 137)));
        Future<RestResp<Void>> second;
        try {
            await(gate.held, "editor acquired the book lock");
            second = submit("deleter", () -> service.deleteBookChapter(SECOND));
            await(gate.followersAttempted, "deleter has read the old locator");
            assertThat(probe.chapterWords("deleter", CHAPTER_LOOKUP)).isEqualTo(80);
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertOk(get(second));
        assertThat(probe.chapterWords("deleter", CHAPTER_LOCK)).isEqualTo(137);
        assertThat(book(BOOK).words()).isEqualTo(60);
        assertThat(book(BOOK).lastId()).isEqualTo(FIRST);
        assertConsistent(BOOK);
    }

    @ParameterizedTest(name = "waiting {0} sees chapter deleted after its ordinary lookup")
    @EnumSource(value = Mutation.class, names = {"EDIT", "DELETE"})
    void chapterDeletedWhileWaitingIsNotFoundWithoutFurtherWrites(Mutation waitingOperation) throws Exception {
        seedTwoChapters();
        BookGate gate = probe.gate("deleter", Set.of("waiter"));
        Future<RestResp<Void>> first = submit("deleter", () -> service.deleteBookChapter(SECOND));
        Future<String> waiter;
        try {
            await(gate.held, "deleter acquired the book lock");
            waiter = submit("waiter", () -> resultCode(() -> mutate(waitingOperation, BOOK, SECOND)));
            await(gate.followersAttempted, "waiter located the chapter before deletion committed");
            assertThat(probe.chapterWords("waiter", CHAPTER_LOOKUP)).isEqualTo(80);
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertThat(get(waiter)).isEqualTo("A2002");
        assertThat(probe.queryRows("waiter", CHAPTER_LOCK)).isEmpty();
        assertThat(probe.writes.stream().filter(write -> write.actor().equals("waiter")).toList()).isEmpty();
        assertThat(book(BOOK).words()).isEqualTo(60);
        assertConsistent(BOOK);
    }

    @Test
    void twoEditsAndDeleteOfSameChapterLeaveNoOrphanOrWordCountDrift() throws Exception {
        seedTwoChapters();
        BookGate gate = probe.gate("first-editor", Set.of("second-editor", "deleter"));
        Future<RestResp<Void>> first = submit("first-editor",
            () -> service.updateBookChapter(SECOND, edit("first", 123)));
        Future<String> second;
        Future<RestResp<Void>> deletion;
        try {
            await(gate.held, "first of three writers acquired the book lock");
            second = submit("second-editor",
                () -> resultCode(() -> service.updateBookChapter(SECOND, edit("second", 177))));
            deletion = submit("deleter", () -> service.deleteBookChapter(SECOND));
            await(gate.followersAttempted, "both other writers located the chapter and reached the lock");
        } finally {
            gate.release.countDown();
        }
        assertOk(get(first));
        assertThat(get(second)).isIn("00000", "A2002");
        assertOk(get(deletion));
        assertThat(book(BOOK).words()).isEqualTo(60);
        assertThat(chapters(BOOK)).extracting(ChapterRow::id).containsExactly(FIRST);
        assertConsistent(BOOK);
    }

    @Test
    void deletingLatestThenLastChapterRestoresMetadataAndClearsTimestampToSqlNull() {
        seedTwoChapters();
        assertOk(service.deleteBookChapter(SECOND));
        assertThat(book(BOOK).lastId()).isEqualTo(FIRST);
        assertThat(book(BOOK).lastName()).isEqualTo("first");
        assertThat(book(BOOK).lastUpdated()).isEqualTo(FIXTURE_TIME);
        assertConsistent(BOOK);
        assertOk(service.deleteBookChapter(FIRST));
        BookRow empty = book(BOOK);
        assertThat(empty.words()).isZero();
        assertThat(empty.lastId()).isZero();
        assertThat(empty.lastName()).isEmpty();
        assertThat(empty.lastUpdated()).as("explicit SQL SET NULL, not a skipped entity null").isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_content", Integer.class)).isZero();
        assertConsistent(BOOK);
        assertMutableCachesUnused();
    }

    @ParameterizedTest(name = "content failure rolls back prior chapter {0}")
    @EnumSource(Mutation.class)
    void contentFailureRollsBackAlreadyExecutedChapterWrite(Mutation operation) {
        seedTwoChapters();
        DatabaseRows before = snapshot();
        probe.failNextContentWrite.set(true);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> mutate(operation, BOOK, SECOND));
        assertThat(causedBy(failure, InjectedContentFailure.class)).as("the injected content failure surfaced").isTrue();
        assertThat(probe.failNextContentWrite).isFalse();
        assertThat(probe.writes.stream()
            .filter(write -> write.statement().startsWith(BookChapterMapper.class.getName() + "."))
            .map(Write::affected).toList()).containsExactly(1);
        assertThat(snapshot()).as("chapter, content and book rows must all roll back").isEqualTo(before);
        verifyNoInteractions(messages);
    }

    @Test
    void missingContentOnEditRollsBackChapterUpdateWhenSqlAffectsZeroRows() {
        seedTwoChapters();
        jdbc.update("DELETE FROM book_content WHERE chapter_id = ?", SECOND);
        DatabaseRows before = snapshot();
        assertThat(resultCode(() -> service.updateBookChapter(SECOND, edit("must-roll-back", 199))))
            .isEqualTo("A2002");
        assertThat(probe.writes).anySatisfy(write -> {
            assertThat(write.statement()).isEqualTo(BookChapterMapper.class.getName() + ".updateById");
            assertThat(write.affected()).isEqualTo(1);
        }).anySatisfy(write -> {
            assertThat(write.statement()).isEqualTo(BookContentMapper.class.getName() + ".update");
            assertThat(write.affected()).isZero();
        });
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(messages);
    }

    @Test
    void deleteAllowsCleanupOfChapterWhoseContentIsAlreadyMissing() {
        seedTwoChapters();
        jdbc.update("DELETE FROM book_content WHERE chapter_id = ?", SECOND);
        assertOk(service.deleteBookChapter(SECOND));
        assertThat(book(BOOK).words()).isEqualTo(60);
        assertThat(book(BOOK).lastId()).isEqualTo(FIRST);
        assertConsistent(BOOK);
    }

    @ParameterizedTest(name = "{0}: caller author {1}, stored author {2} is unauthorized")
    @MethodSource("unauthorizedCases")
    void unauthorizedMutationsDoNotWriteOrNotify(Mutation operation, Long caller, Long storedAuthor) {
        seedBook(BOOK, storedAuthor);
        seedChapter(BOOK, FIRST, 0, "first", text(60));
        UserHolder.setAuthorId(caller);
        DatabaseRows before = snapshot();
        assertThat(resultCode(() -> mutate(operation, BOOK, FIRST))).isEqualTo("A0301");
        assertThat(probe.writes).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(messages);
    }

    static Stream<Arguments> unauthorizedCases() {
        return Stream.of(Mutation.values()).flatMap(operation -> Stream.of(
            Arguments.of(operation, 99L, OWNER),
            Arguments.of(operation, null, OWNER),
            Arguments.of(operation, OWNER, null),
            Arguments.of(operation, null, null)));
    }

    @ParameterizedTest(name = "missing rows: {0}, orphan chapter = {1}")
    @MethodSource("missingCases")
    void missingBookOrChapterDoesNotWriteOrNotify(Mutation operation, boolean orphanChapter) {
        seedTwoChapters();
        if (orphanChapter) {
            jdbc.update("INSERT INTO book_chapter (id, book_id, chapter_num, chapter_name, word_count, is_vip) "
                + "VALUES (?, ?, 0, 'orphan', 50, 0)", MISSING, MISSING);
        }
        DatabaseRows before = snapshot();
        assertThat(resultCode(() -> mutate(operation, MISSING, MISSING))).isEqualTo("A2002");
        assertThat(probe.writes).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(messages);
    }

    static Stream<Arguments> missingCases() {
        return Stream.of(Arguments.of(Mutation.ADD, false), Arguments.of(Mutation.EDIT, false),
            Arguments.of(Mutation.DELETE, false), Arguments.of(Mutation.EDIT, true),
            Arguments.of(Mutation.DELETE, true));
    }

    @Test
    void anotherBookCanCommitWhileFirstBookWriterStillHoldsItsRowLock() throws Exception {
        seedBook(BOOK, OWNER);
        seedBook(OTHER_BOOK, OWNER);
        BookGate gate = probe.gate("held-book", Set.of());
        Future<RestResp<Void>> held = submit("held-book",
            () -> service.saveBookChapter(add(BOOK, "held", 71)));
        try {
            await(gate.held, "first book is held inside a real transaction");
            Future<RestResp<Void>> independent = submit("independent-book",
                () -> service.saveBookChapter(add(OTHER_BOOK, "independent", 93)));
            // Crucially, wait for COMMIT before releasing the other book, not merely for a query attempt.
            assertOk(get(independent));
            assertThat(held.isDone()).isFalse();
            assertThat(book(OTHER_BOOK).words()).isEqualTo(93);
            assertThat(chapters(BOOK)).isEmpty();
            assertConsistent(OTHER_BOOK);
        } finally {
            gate.release.countDown();
        }
        assertOk(get(held));
        assertThat(book(BOOK).words()).isEqualTo(71);
        assertConsistent(BOOK);
    }

    private <T> void registerMapper(Class<T> type) {
        context.registerBean(type, () -> context.getBean(SqlSessionTemplate.class).getMapper(type));
    }

    private <T> void registerMock(Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource, SqlProbe probe) {
        try {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            // Keep SESSION cache enabled so FOR UPDATE's flushCache requirement is exercised.
            configuration.setLocalCacheScope(LocalCacheScope.SESSION);
            configuration.addInterceptor(probe);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setBanner(false).setDbConfig(new GlobalConfig.DbConfig()));
            SqlSessionFactory result = factory.getObject();
            result.getConfiguration().addMapper(BookInfoMapper.class);
            result.getConfiguration().addMapper(BookChapterMapper.class);
            result.getConfiguration().addMapper(BookContentMapper.class);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create isolated chapter test MyBatis factory", exception);
        }
    }

    private void createSchema() {
        jdbc.execute("""
            CREATE TABLE book_info (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, work_direction INT, category_id BIGINT,
                category_name VARCHAR(100), pic_url VARCHAR(255), book_name VARCHAR(100), author_id BIGINT,
                author_name VARCHAR(100), book_desc VARCHAR(1000), score INT DEFAULT 0,
                book_status INT DEFAULT 0, visit_count BIGINT DEFAULT 0, word_count INT DEFAULT 0,
                comment_count INT DEFAULT 0, last_chapter_id BIGINT DEFAULT 0,
                last_chapter_name VARCHAR(100) DEFAULT '', last_chapter_update_time TIMESTAMP(6),
                is_vip INT DEFAULT 0, create_time TIMESTAMP(6), update_time TIMESTAMP(6))
            """);
        // Match the production chapter-number uniqueness constraint; every concurrent writer must
        // still return success. No FK permits deliberate missing-row fixtures.
        jdbc.execute("""
            CREATE TABLE book_chapter (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, book_id BIGINT NOT NULL, chapter_num INT NOT NULL,
                chapter_name VARCHAR(100), word_count INT NOT NULL, is_vip INT,
                create_time TIMESTAMP(6), update_time TIMESTAMP(6),
                CONSTRAINT uk_bookId_chapterNum UNIQUE (book_id, chapter_num))
            """);
        jdbc.execute("""
            CREATE TABLE book_content (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, chapter_id BIGINT NOT NULL UNIQUE, content CLOB,
                create_time TIMESTAMP(6), update_time TIMESTAMP(6))
            """);
    }

    private void seedBook(long bookId, Long authorId) {
        jdbc.update("INSERT INTO book_info (id, book_name, author_id, create_time, update_time) VALUES (?, ?, ?, ?, ?)",
            bookId, "book-" + bookId, authorId, FIXTURE_TIME, FIXTURE_TIME);
    }

    private void seedChapter(long bookId, long chapterId, int number, String name, String content) {
        jdbc.update("INSERT INTO book_chapter (id, book_id, chapter_num, chapter_name, word_count, is_vip, "
            + "create_time, update_time) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
            chapterId, bookId, number, name, content.length(), FIXTURE_TIME, FIXTURE_TIME);
        jdbc.update("INSERT INTO book_content (chapter_id, content, create_time, update_time) VALUES (?, ?, ?, ?)",
            chapterId, content, FIXTURE_TIME, FIXTURE_TIME);
        jdbc.update("UPDATE book_info SET word_count = word_count + ?, last_chapter_id = ?, "
            + "last_chapter_name = ?, last_chapter_update_time = ? WHERE id = ?",
            content.length(), chapterId, name, FIXTURE_TIME, bookId);
    }

    private void seedTwoChapters() {
        seedBook(BOOK, OWNER);
        seedChapter(BOOK, FIRST, 0, "first", text(60));
        seedChapter(BOOK, SECOND, 1, "last", text(80));
    }

    private static String text(int length) {
        return "x".repeat(length);
    }

    private static ChapterAddReqDto add(long bookId, String name, int length) {
        ChapterAddReqDto dto = new ChapterAddReqDto();
        dto.setBookId(bookId);
        dto.setChapterName(name);
        dto.setChapterContent(text(length));
        dto.setIsVip(0);
        return dto;
    }

    private static ChapterUpdateReqDto edit(String name, int length) {
        ChapterUpdateReqDto dto = new ChapterUpdateReqDto();
        dto.setChapterName(name);
        dto.setChapterContent(text(length));
        dto.setIsVip(0);
        return dto;
    }

    private RestResp<Void> mutate(Mutation operation, long bookId, long chapterId) {
        return switch (operation) {
            case ADD -> service.saveBookChapter(add(bookId, "new", 101));
            case EDIT -> service.updateBookChapter(chapterId, edit("edited", 151));
            case DELETE -> service.deleteBookChapter(chapterId);
        };
    }

    private static String resultCode(Supplier<RestResp<Void>> operation) {
        try {
            return operation.get().getCode();
        } catch (BusinessException exception) {
            return exception.getErrorCodeEnum().getCode();
        }
    }

    private static void assertOk(RestResp<Void> response) {
        assertThat(response.getCode()).isEqualTo("00000");
    }

    private <T> Future<T> submit(String actor, Callable<T> work) {
        return workers.submit(() -> {
            UserHolder.clear();
            UserHolder.setUserId(1L);
            UserHolder.setAuthorId(OWNER);
            probe.actor.set(actor);
            try {
                return work.call();
            } finally {
                probe.actor.remove();
                UserHolder.clear();
            }
        });
    }

    private static <T> T get(Future<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch, String description) throws InterruptedException {
        assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as(description).isTrue();
    }

    private BookRow book(long bookId) {
        return jdbc.queryForObject("SELECT word_count, last_chapter_id, last_chapter_name, "
            + "last_chapter_update_time FROM book_info WHERE id = ?",
            (rs, row) -> new BookRow(rs.getInt(1), rs.getLong(2), rs.getString(3),
                rs.getObject(4, LocalDateTime.class)), bookId);
    }

    private List<ChapterRow> chapters(long bookId) {
        return jdbc.query("SELECT c.id, c.chapter_num, c.chapter_name, c.word_count, t.content, c.update_time "
            + "FROM book_chapter c LEFT JOIN book_content t ON t.chapter_id = c.id "
            + "WHERE c.book_id = ? ORDER BY c.chapter_num",
            (rs, row) -> new ChapterRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getObject(6, LocalDateTime.class)), bookId);
    }

    private void assertConsistent(long bookId) {
        BookRow book = book(bookId);
        List<ChapterRow> chapters = chapters(bookId);
        assertThat(book.words()).isEqualTo(chapters.stream().mapToInt(ChapterRow::words).sum());
        assertThat(chapters.stream().map(ChapterRow::number).toList()).doesNotHaveDuplicates();
        for (ChapterRow chapter : chapters) {
            assertThat(chapter.content()).as("every chapter must have persisted content").isNotNull();
            assertThat(chapter.words()).isEqualTo(chapter.content().length());
        }
        if (chapters.isEmpty()) {
            assertThat(book.lastId()).isZero();
            assertThat(book.lastName()).isEmpty();
            assertThat(book.lastUpdated()).isNull();
        } else {
            ChapterRow last = chapters.get(chapters.size() - 1);
            assertThat(book.lastId()).isEqualTo(last.id());
            assertThat(book.lastName()).isEqualTo(last.name());
            assertThat(book.lastUpdated()).isEqualTo(last.updated());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_content t LEFT JOIN book_chapter c "
            + "ON c.id = t.chapter_id WHERE c.id IS NULL", Integer.class)).as("no orphan content").isZero();
    }

    private DatabaseRows snapshot() {
        return new DatabaseRows(jdbc.queryForList("SELECT * FROM book_info ORDER BY id"),
            jdbc.queryForList("SELECT * FROM book_chapter ORDER BY id"),
            jdbc.queryForList("SELECT id, chapter_id, CAST(content AS VARCHAR) AS content, create_time, update_time "
                + "FROM book_content ORDER BY id"));
    }

    private void assertMutableCachesUnused() {
        verifyNoInteractions(context.getBean(BookInfoCacheManager.class),
            context.getBean(BookChapterCacheManager.class), context.getBean(BookContentCacheManager.class));
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    enum Mutation { ADD, EDIT, DELETE }

    private record BookRow(int words, long lastId, String lastName, LocalDateTime lastUpdated) { }

    private record ChapterRow(long id, int number, String name, int words, String content,
                              LocalDateTime updated) { }

    private record DatabaseRows(List<Map<String, Object>> books, List<Map<String, Object>> chapters,
                                List<Map<String, Object>> contents) { }

    private record Write(String actor, String statement, int affected) { }

    private record Read(String actor, String statement, List<?> rows) { }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    static class ReadCommittedDataSource extends DriverManagerDataSource {
        ReadCommittedDataSource() {
            setDriverClassName("org.h2.Driver");
            setUrl("jdbc:h2:mem:chapter-consistency-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
            setUsername("sa");
            setPassword("");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return readCommitted(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return readCommitted(super.getConnection(username, password));
        }

        private Connection readCommitted(Connection connection) throws SQLException {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            return connection;
        }
    }

    private static final class BookGate {
        final String holder;
        final Set<String> followers;
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch followersAttempted;
        final AtomicBoolean once = new AtomicBoolean();

        BookGate(String holder, Set<String> followers) {
            this.holder = holder;
            this.followers = followers;
            followersAttempted = new CountDownLatch(followers.size());
        }
    }

    private static final class InjectedContentFailure extends RuntimeException {
        InjectedContentFailure() {
            super("test-only failure after the chapter SQL has executed");
        }
    }

    @Intercepts({
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
    })
    public static class SqlProbe implements Interceptor {
        final ThreadLocal<String> actor = ThreadLocal.withInitial(() -> "main");
        final List<Write> writes = new CopyOnWriteArrayList<>();
        final List<Read> reads = new CopyOnWriteArrayList<>();
        final AtomicBoolean failNextContentWrite = new AtomicBoolean();
        private volatile BookGate bookGate;

        BookGate gate(String holder, Set<String> followers) {
            BookGate gate = new BookGate(holder, followers);
            bookGate = gate;
            return gate;
        }

        void releaseGates() {
            BookGate gate = bookGate;
            if (gate != null) {
                gate.release.countDown();
            }
        }

        List<?> queryRows(String actor, String statement) {
            return reads.stream().filter(read -> read.actor().equals(actor) && read.statement().equals(statement))
                .findFirst().orElseThrow(() -> new AssertionError("Missing read: " + actor + " " + statement)).rows();
        }

        int chapterWords(String actor, String statement) {
            return ((BookChapter) queryRows(actor, statement).get(0)).getWordCount();
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            String id = statement.getId();
            String who = actor.get();
            if (invocation.getMethod().getName().equals("update")) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("every service DML must participate in a real Spring transaction").isTrue();
                if (id.startsWith(BookContentMapper.class.getName() + ".")
                    && failNextContentWrite.compareAndSet(true, false)) {
                    // Record only COMPLETED writes. This proves failure happened after real chapter SQL,
                    // rather than before the service reached the database or on a mocked mapper.
                    assertThat(writes.stream().filter(write -> write.actor().equals(who)
                        && write.statement().startsWith(BookChapterMapper.class.getName() + "."))
                        .map(Write::affected).toList()).contains(1);
                    throw new InjectedContentFailure();
                }
                Object result = invocation.proceed();
                writes.add(new Write(who, id, (Integer) result));
                return result;
            }
            BookGate gate = bookGate;
            if (id.equals(BOOK_LOCK)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("book lock must be held by the service transaction").isTrue();
                if (gate != null && gate.followers.contains(who)) {
                    gate.followersAttempted.countDown();
                }
            }
            Object result = invocation.proceed();
            reads.add(new Read(who, id, new ArrayList<>((List<?>) result)));
            if (id.equals(BOOK_LOCK) && gate != null && gate.holder.equals(who)
                && gate.once.compareAndSet(false, true)) {
                gate.held.countDown();
                await(gate.release, "test must release the acquired book lock");
            }
            return result;
        }
    }
}
