package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import novel.core.auth.UserHolder;
import novel.core.common.exception.BusinessException;
import novel.core.common.resp.RestResp;
import novel.dao.entity.BookInfo;
import novel.dao.mapper.BookCommentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.req.UserCommentReqDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Direct service calls using a real MyBatis-Plus BookCommentMapper and a private H2 database.
 * Other constructor dependencies are mocks, including any additional local UserService field.
 * No MVC/Bean Validation, Spring AOP, application configuration, Redis or external database is used.
 */
@Timeout(30)
class CommentWriteValidationTest {

    private static final long BOOK = 101L;
    private static final long OTHER_BOOK = 202L;
    private static final long OWNER = 10L;
    private static final long OTHER_USER = 20L;
    private static final long NEW_USER = 30L;
    private static final long TARGET = 1001L;
    private static final long SAME_OWNER_OTHER_COMMENT = 1002L;
    private static final long OTHER_OWNER_COMMENT = 1003L;
    private static final long MISSING = 9999L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2025, 2, 3, 4, 5, 6);
    private static final String MAPPER = BookCommentMapper.class.getName() + ".";
    private static final String EMOJI = "\uD83D\uDE00";

    private final Map<Class<?>, Object> mocks = new LinkedHashMap<>();
    private JdbcTemplate jdbc;
    private BookServiceImpl service;
    private BookInfoMapper bookInfoMapper;
    private SqlProbe probe;

    @BeforeEach
    void setUp() throws Exception {
        UserHolder.clear();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:comment_validation_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        // Deliberately wider than 512: rejected input must be stopped by the service,
        // not by an H2 column limit or a database CHECK constraint.
        jdbc.execute("""
            CREATE TABLE book_comment (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                book_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                comment_content VARCHAR(2048),
                reply_count INT DEFAULT 0,
                audit_status INT DEFAULT 0,
                create_time TIMESTAMP(6),
                update_time TIMESTAMP(6))
            """);
        seed(TARGET, BOOK, OWNER, "original approved comment", 3, 1);
        seed(SAME_OWNER_OTHER_COMMENT, OTHER_BOOK, OWNER, "original rejected comment", 4, 2);
        seed(OTHER_OWNER_COMMENT, BOOK, OTHER_USER, "another user's comment", 5, 1);

        probe = new SqlProbe();
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setCacheEnabled(false);
        configuration.addInterceptor(probe);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false).setDbConfig(new GlobalConfig.DbConfig()));
        SqlSessionFactory sessions = factory.getObject();
        assertThat(sessions).isNotNull();
        sessions.getConfiguration().addMapper(BookCommentMapper.class);
        BookCommentMapper commentMapper = new SqlSessionTemplate(sessions).getMapper(BookCommentMapper.class);
        service = newService(commentMapper);
        bookInfoMapper = (BookInfoMapper) mocks.get(BookInfoMapper.class);
        assertThat(bookInfoMapper).isNotNull();
        BookInfo book = new BookInfo();
        book.setId(BOOK);
        book.setCreateTime(CREATED);
        when(bookInfoMapper.selectById(BOOK)).thenReturn(book);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    @ParameterizedTest(name = "invalid save: {0}")
    @MethodSource("invalidContents")
    void invalidCreationThrowsA0400BeforeAnyMapperAndLeavesAllRowsUnchanged(String label, String content) {
        List<Map<String, Object>> before = snapshot();

        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.saveComment(request(NEW_USER, BOOK, content)));

        assertInvalidWithoutDatabaseAccess(failure, before);
    }

    @ParameterizedTest(name = "invalid edit: {0}")
    @MethodSource("invalidContents")
    void invalidEditThrowsA0400BeforeAnyMapperAndPreservesContentAuditAndTimestamps(String label, String content) {
        List<Map<String, Object>> before = snapshot();

        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.updateComment(OWNER, TARGET, content));

        assertInvalidWithoutDatabaseAccess(failure, before);
    }

    @ParameterizedTest(name = "invalid content takes precedence over existing or missing book: {0}")
    @ValueSource(longs = {BOOK, MISSING})
    void invalidCreationIsRejectedBeforeBookLookupOrDuplicateCommentCheck(long bookId) {
        // OWNER already commented on BOOK; MISSING has no stubbed book. Neither lookup
        // nor the duplicate-comment result may mask the content error on this direct call.
        List<Map<String, Object>> before = snapshot();

        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.saveComment(request(OWNER, bookId, "too short")));

        assertInvalidWithoutDatabaseAccess(failure, before);
    }

    @ParameterizedTest(name = "invalid content precedes update of missing row: {0}")
    @ValueSource(longs = {TARGET, MISSING})
    void invalidEditIsRejectedEvenForAnotherUsersCommentOrMissingId(long id) {
        List<Map<String, Object>> before = snapshot();

        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.updateComment(OTHER_USER, id, null));

        assertInvalidWithoutDatabaseAccess(failure, before);
    }

    @ParameterizedTest(name = "valid save: {0}, UTF-16 length {2}")
    @MethodSource("validContents")
    void validCreationPersistsExactUntrimmedContent(String label, String content, int length) {
        assertThat(content.length()).isEqualTo(length);
        assertThat(content.isBlank()).isFalse();
        List<Map<String, Object>> before = snapshot();

        RestResp<Void> response = service.saveComment(request(NEW_USER, BOOK, content));

        assertThat(response.isOk()).isTrue();
        List<Map<String, Object>> after = snapshot();
        assertThat(after).hasSize(before.size() + 1);
        assertThat(after.stream().filter(row -> number(row, "user_id") != NEW_USER).toList())
            .isEqualTo(before);
        Map<String, Object> inserted = after.stream().filter(row -> number(row, "user_id") == NEW_USER)
            .findFirst().orElseThrow();
        assertThat(number(inserted, "book_id")).isEqualTo(BOOK);
        assertThat(inserted.get("comment_content")).isEqualTo(content);
        assertThat(number(inserted, "audit_status")).isZero();
        assertThat(number(inserted, "reply_count")).isZero();
        assertThat(inserted.get("create_time")).isNotNull();
        assertThat(inserted.get("update_time")).isNotNull();
        assertThat(probe.reads).containsExactly(MAPPER + "selectCount");
        assertThat(probe.writes).containsExactly(new Write(MAPPER + "insert", 1));
        verify(bookInfoMapper).selectById(BOOK);
        verifyNoMoreInteractions(bookInfoMapper);
        assertOtherDependenciesUnused();
    }

    @ParameterizedTest(name = "valid edit: {0}, UTF-16 length {2}")
    @MethodSource("validContents")
    void validEditPersistsExactContentResetsAuditAndChangesOnlyTheOwnedId(String label, String content, int length) {
        assertThat(content.length()).isEqualTo(length);
        assertThat(content.isBlank()).isFalse();
        List<Map<String, Object>> before = snapshot();

        RestResp<Void> response = service.updateComment(OWNER, TARGET, content);

        assertThat(response.isOk()).isTrue();
        assertOnlyTargetChanged(before, TARGET, content);
        assertUpdateCount(1);
        verifyNoInteractions(mocks.values().toArray());
    }

    @Test
    void validEditResetsPreviouslyRejectedCommentToPending() {
        List<Map<String, Object>> before = snapshot();
        String content = "replacement for rejected comment";

        RestResp<Void> response = service.updateComment(OWNER, SAME_OWNER_OTHER_COMMENT, content);

        assertThat(response.isOk()).isTrue();
        assertOnlyTargetChanged(before, SAME_OWNER_OTHER_COMMENT, content);
        assertUpdateCount(1);
        verifyNoInteractions(mocks.values().toArray());
    }

    @ParameterizedTest(name = "valid zero-row update: {0}")
    @MethodSource("unmatchedEditors")
    void validEditOfAnotherUsersCommentOrMissingIdRemainsOkWithZeroRows(String label, long userId, long id) {
        List<Map<String, Object>> before = snapshot();

        RestResp<Void> response = service.updateComment(userId, id, "valid replacement comment");

        assertThat(response.isOk()).isTrue();
        assertThat(snapshot()).isEqualTo(before);
        assertUpdateCount(0);
        verifyNoInteractions(mocks.values().toArray());
    }

    static Stream<Arguments> invalidContents() {
        return Stream.of(
            Arguments.of("null", (String) null),
            Arguments.of("empty", ""),
            Arguments.of("ASCII spaces at minimum length", " ".repeat(10)),
            Arguments.of("tabs and line breaks", "\t\r\n".repeat(4)),
            Arguments.of("Unicode EM SPACE isBlank", "\u2003".repeat(10)),
            Arguments.of("one UTF-16 unit", "x"),
            Arguments.of("nine UTF-16 units", "x".repeat(9)),
            Arguments.of("513 UTF-16 units", "x".repeat(513)),
            Arguments.of("padding must not reduce raw length 513", " " + "x".repeat(511) + " "),
            Arguments.of("four emoji plus ASCII have length 9", EMOJI.repeat(4) + "x"),
            Arguments.of("256 emoji plus ASCII have length 513", EMOJI.repeat(256) + "x")
        );
    }

    static Stream<Arguments> validContents() {
        return Stream.of(
            Arguments.of("ASCII minimum", "x".repeat(10), 10),
            Arguments.of("ASCII maximum", "x".repeat(512), 512),
            Arguments.of("padding counts toward minimum and is retained", " " + "x".repeat(8) + " ", 10),
            Arguments.of("padding retained at maximum", " " + "x".repeat(510) + " ", 512),
            Arguments.of("tabs and line breaks surrounding text are retained", "\tabcdefgh\n", 10),
            Arguments.of("NBSP is not blank according to String.isBlank", "\u00A0".repeat(10), 10),
            Arguments.of("five emoji meet the UTF-16 minimum", EMOJI.repeat(5), 10),
            Arguments.of("256 emoji meet the UTF-16 maximum", EMOJI.repeat(256), 512)
        );
    }

    static Stream<Arguments> unmatchedEditors() {
        return Stream.of(
            Arguments.of("another owner", OTHER_USER, TARGET),
            Arguments.of("owner has a different existing comment", OWNER, OTHER_OWNER_COMMENT),
            Arguments.of("missing comment", OWNER, MISSING)
        );
    }

    private BookServiceImpl newService(BookCommentMapper commentMapper) throws Exception {
        // Do not pin this test to the baseline constructor: the integration checkout
        // also has a UserService dependency, which must remain a mock rather than real I/O.
        Constructor<?> constructor = Arrays.stream(BookServiceImpl.class.getDeclaredConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> type == BookCommentMapper.class ? commentMapper
                : mocks.computeIfAbsent(type, dependency -> mock(dependency)))
            .toArray();
        constructor.setAccessible(true);
        return (BookServiceImpl) constructor.newInstance(arguments);
    }

    private void seed(long id, long bookId, long userId, String content, int replies, int audit) {
        jdbc.update("""
            INSERT INTO book_comment
                (id, book_id, user_id, comment_content, reply_count, audit_status, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, bookId, userId, content, replies, audit, CREATED, UPDATED);
    }

    private static UserCommentReqDto request(long userId, long bookId, String content) {
        UserCommentReqDto dto = new UserCommentReqDto();
        dto.setUserId(userId);
        dto.setBookId(bookId);
        dto.setCommentContent(content);
        return dto;
    }

    private List<Map<String, Object>> snapshot() {
        // JDBC bypasses the MyBatis probe: fixture assertions cannot obscure service SQL.
        return jdbc.queryForList("SELECT * FROM book_comment ORDER BY id");
    }

    private void assertInvalidWithoutDatabaseAccess(BusinessException failure, List<Map<String, Object>> before) {
        assertThat(failure.getErrorCodeEnum().getCode()).isEqualTo("A0400");
        assertThat(probe.reads).as("invalid content must not execute any comment lookup").isEmpty();
        assertThat(probe.writes).as("invalid content must not attempt any comment mutation").isEmpty();
        verifyNoInteractions(mocks.values().toArray());
        assertThat(snapshot()).as("all columns of all existing comments must remain unchanged").isEqualTo(before);
    }

    private void assertOnlyTargetChanged(List<Map<String, Object>> before, long targetId, String content) {
        List<Map<String, Object>> after = snapshot();
        assertThat(after).hasSameSizeAs(before);
        assertThat(after.stream().filter(row -> number(row, "id") != targetId).toList())
            .isEqualTo(before.stream().filter(row -> number(row, "id") != targetId).toList());
        Map<String, Object> oldRow = before.stream().filter(row -> number(row, "id") == targetId)
            .findFirst().orElseThrow();
        Map<String, Object> newRow = after.stream().filter(row -> number(row, "id") == targetId)
            .findFirst().orElseThrow();
        assertThat(newRow.get("comment_content")).isEqualTo(content);
        assertThat(number(newRow, "audit_status")).isZero();
        for (String unchanged : List.of("id", "book_id", "user_id", "reply_count", "create_time")) {
            assertThat(newRow.get(unchanged)).as(unchanged).isEqualTo(oldRow.get(unchanged));
        }
    }

    private void assertUpdateCount(int affected) {
        assertThat(probe.reads).isEmpty();
        assertThat(probe.writes).containsExactly(new Write(MAPPER + "update", affected));
    }

    private void assertOtherDependenciesUnused() {
        verifyNoInteractions(mocks.entrySet().stream().filter(entry -> entry.getKey() != BookInfoMapper.class)
            .map(Map.Entry::getValue).toArray());
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private record Write(String statement, int affected) {}

    @Intercepts({
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
    })
    public static class SqlProbe implements Interceptor {
        private final List<String> reads = new ArrayList<>();
        private final List<Write> writes = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            String statement = ((MappedStatement) invocation.getArgs()[0]).getId();
            if (invocation.getMethod().getName().equals("update")) {
                int index = writes.size();
                writes.add(new Write(statement, -1));
                Object result = invocation.proceed();
                writes.set(index, new Write(statement, (Integer) result));
                return result;
            }
            reads.add(statement);
            return invocation.proceed();
        }
    }
}
