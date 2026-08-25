package io.github.xxyopen.novel.crawl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析出的单个章节（爬虫与 TXT 导入共用的中间结构）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedChapter {

    // 章节标题
    private String title;

    // 章节正文（纯文本，段落间以换行符分隔）
    private String content;

}
