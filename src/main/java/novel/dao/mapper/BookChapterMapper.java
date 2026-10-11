package novel.dao.mapper;

import novel.dao.entity.BookChapter;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 小说章节 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface BookChapterMapper extends BaseMapper<BookChapter> {

    /** 先持有书籍行锁，再以当前读获取章节，不复用事务中的旧快照。 */
    @Select("SELECT * FROM book_chapter WHERE id = #{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    BookChapter selectByIdForUpdate(@Param("id") Long id);

    /** 先持有书籍行锁，再分配章节号或重算末章。 */
    @Select("SELECT * FROM book_chapter WHERE book_id = #{bookId} "
        + "ORDER BY chapter_num DESC LIMIT 1 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    BookChapter selectLastByBookIdForUpdate(@Param("bookId") Long bookId);

}
