package io.github.xxyopen.novel.crawl.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交爬取小说任务的请求参数
 */
@Data
public class CrawlBookRequest {

    // 站点标识（对应配置 novel-crawl.sources[].name；留空则按URL前缀自动匹配，匹配不到取第一个站点规则）
    private String sourceName;

    // 小说目录页（书籍详情页）地址
    @NotBlank(message = "小说目录页地址不能为空")
    private String bookUrl;

    // 类别ID（对应 book_category.id，可空）
    private Long categoryId;

    // 作品方向;0-男频 1-女频（可空，传了类别ID时以类别表为准）
    private Integer workDirection;

    // 是否覆盖重导：true-删除该书已有章节后重新爬取 false-增量续爬（默认）
    private Boolean override = false;

}
