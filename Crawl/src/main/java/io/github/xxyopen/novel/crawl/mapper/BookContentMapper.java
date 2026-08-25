package io.github.xxyopen.novel.crawl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.xxyopen.novel.crawl.entity.BookContent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 小说内容 Mapper（表 book_content）
 */
public interface BookContentMapper extends BaseMapper<BookContent> {

    /**
     * 删除某本小说全部章节对应的内容记录
     *
     * @param bookId 小说ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM book_content WHERE chapter_id IN (SELECT id FROM book_chapter WHERE book_id = #{bookId})")
    int deleteByBookId(@Param("bookId") Long bookId);

}
