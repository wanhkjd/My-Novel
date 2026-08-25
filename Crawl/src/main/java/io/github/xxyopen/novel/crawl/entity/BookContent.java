package io.github.xxyopen.novel.crawl.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说内容实体（对应表 book_content）
 */
@Data
@TableName("book_content")
public class BookContent {

    // 主键
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 章节ID
    private Long chapterId;

    // 小说章节内容
    private String content;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

}
