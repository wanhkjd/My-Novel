package io.github.xxyopen.novel.crawl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.xxyopen.novel.crawl.entity.BookInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 小说信息 Mapper（表 book_info）
 */
public interface BookInfoMapper extends BaseMapper<BookInfo> {

    /**
     * 更新小说的统计信息（总字数、最新章节三项），允许把最新章节字段清空
     *
     * @param bookId                小说ID
     * @param wordCount             总字数
     * @param lastChapterId         最新章节ID（可为 null）
     * @param lastChapterName       最新章节名（可为 null）
     * @param lastChapterUpdateTime 最新章节更新时间（可为 null）
     * @return 受影响行数
     */
    @Update("UPDATE book_info SET word_count = #{wordCount}, last_chapter_id = #{lastChapterId}, "
            + "last_chapter_name = #{lastChapterName}, last_chapter_update_time = #{lastChapterUpdateTime}, "
            + "update_time = NOW() WHERE id = #{bookId}")
    int updateBookStats(@Param("bookId") Long bookId,
                        @Param("wordCount") Integer wordCount,
                        @Param("lastChapterId") Long lastChapterId,
                        @Param("lastChapterName") String lastChapterName,
                        @Param("lastChapterUpdateTime") LocalDateTime lastChapterUpdateTime);

}
