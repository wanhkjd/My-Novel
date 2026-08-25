package io.github.xxyopen.novel.crawl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.xxyopen.novel.crawl.entity.BookChapter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 小说章节 Mapper（表 book_chapter）
 */
public interface BookChapterMapper extends BaseMapper<BookChapter> {

    /**
     * 查询某本小说当前最大章节号（无章节时返回 0）
     *
     * @param bookId 小说ID
     * @return 最大章节号
     */
    @Select("SELECT IFNULL(MAX(chapter_num), 0) FROM book_chapter WHERE book_id = #{bookId}")
    int selectMaxChapterNum(@Param("bookId") Long bookId);

    /**
     * 统计某本小说所有章节的总字数
     *
     * @param bookId 小说ID
     * @return 总字数
     */
    @Select("SELECT IFNULL(SUM(word_count), 0) FROM book_chapter WHERE book_id = #{bookId}")
    long sumWordCount(@Param("bookId") Long bookId);

    /**
     * 查询某本小说的最新章节（按章节号倒序取第一条）
     *
     * @param bookId 小说ID
     * @return 最新章节，无章节时返回 null
     */
    @Select("SELECT * FROM book_chapter WHERE book_id = #{bookId} ORDER BY chapter_num DESC LIMIT 1")
    BookChapter selectLastChapter(@Param("bookId") Long bookId);

    /**
     * 删除某本小说的全部章节
     *
     * @param bookId 小说ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM book_chapter WHERE book_id = #{bookId}")
    int deleteByBookId(@Param("bookId") Long bookId);

}
