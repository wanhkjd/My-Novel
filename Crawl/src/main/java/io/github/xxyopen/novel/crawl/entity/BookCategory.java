package io.github.xxyopen.novel.crawl.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说类别实体（对应表 book_category）
 */
@Data
@TableName("book_category")
public class BookCategory {

    // 主键
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 作品方向;0-男频 1-女频
    private Integer workDirection;

    // 类别名
    private String name;

    // 排序
    private Integer sort;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

}
