package io.github.xxyopen.novel.crawl.dto;

import lombok.Data;

/**
 * TXT 导入任务的请求参数（随文件一起以表单方式提交，均可空）
 */
@Data
public class TxtImportRequest {

    // 书名（留空则尝试从文件名解析，如《斗破苍穹》作者：天蚕土豆.txt）
    private String bookName;

    // 作者名（留空则尝试从文件名解析，解析不到用"佚名"）
    private String authorName;

    // 书籍简介（留空则尝试用TXT第一章之前的文字，再取不到用配置的默认简介）
    private String bookDesc;

    // 类别ID（对应 book_category.id，可空）
    private Long categoryId;

    // 作品方向;0-男频 1-女频（可空，传了类别ID时以类别表为准）
    private Integer workDirection;

    // 书籍状态;0-连载中 1-已完结（TXT一般是完本，默认1）
    private Integer bookStatus = 1;

    // 自定义章节标题正则（留空使用内置规则；用于章节命名特殊的TXT）
    private String chapterRegex;

    // 是否覆盖重导：true-删除该书已有章节后重新导入 false-增量追加（默认）
    private Boolean override = false;

}
