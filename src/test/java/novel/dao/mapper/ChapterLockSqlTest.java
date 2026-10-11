package novel.dao.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.shardingsphere.sql.parser.api.CacheOption;
import org.apache.shardingsphere.sql.parser.api.SQLParserEngine;
import org.apache.shardingsphere.sql.parser.api.SQLStatementVisitorEngine;
import org.apache.shardingsphere.sql.parser.statement.mysql.dml.MySQLSelectStatement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Offline syntax check for the MySQL parser shipped with our ShardingSphere version. */
class ChapterLockSqlTest {

    static Stream<Method> lockingQueries() throws NoSuchMethodException {
        return Stream.of(
            BookInfoMapper.class.getMethod("selectByIdForUpdate", Long.class),
            BookChapterMapper.class.getMethod("selectByIdForUpdate", Long.class),
            BookChapterMapper.class.getMethod("selectLastByBookIdForUpdate", Long.class));
    }

    @ParameterizedTest
    @MethodSource("lockingQueries")
    void lockingQueriesRetainForUpdateAndBypassMyBatisCaches(Method method) {
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
            .replaceAll("#\\{[^}]+}", "?");
        SQLParserEngine parser = new SQLParserEngine("MySQL", new CacheOption(1, 8));
        var statement = new SQLStatementVisitorEngine("MySQL").visit(parser.parse(sql, false));
        assertThat(statement).isInstanceOf(MySQLSelectStatement.class);
        MySQLSelectStatement select = (MySQLSelectStatement) statement;
        assertThat(select.getLock()).isPresent();
        if (method.getName().equals("selectLastByBookIdForUpdate")) {
            assertThat(select.getLimit()).isPresent();
        }
        Options options = method.getAnnotation(Options.class);
        assertThat(options.useCache()).isFalse();
        assertThat(options.flushCache()).isEqualTo(Options.FlushCachePolicy.TRUE);
    }
}
