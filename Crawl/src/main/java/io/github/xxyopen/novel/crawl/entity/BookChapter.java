package io.github.xxyopen.novel.crawl.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说章节实体（对应表 book_chapter）
 */
@Data
@TableName("book_chapter")
public class BookChapter {

    // 主键
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 小说ID
    private Long bookId;

    // 章节号
    private Integer chapterNum;

    // 章节名
    private String chapterName;

    // 章节字数
    private Integer wordCount;

    // 是否收费;1-收费 0-免费
    private Integer isVip;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

}
