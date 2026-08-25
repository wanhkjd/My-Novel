package io.github.xxyopen.novel.crawl.controller;

import io.github.xxyopen.novel.crawl.common.ApiResult;
import io.github.xxyopen.novel.crawl.config.CrawlProperties;
import io.github.xxyopen.novel.crawl.entity.BookCategory;
import io.github.xxyopen.novel.crawl.service.BookStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 基础数据接口：站点规则列表、小说类别列表（供网页操作台的下拉框使用）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetaController {

    // 爬虫与导入配置
    private final CrawlProperties properties;

    // 书籍入库服务
    private final BookStoreService bookStoreService;

    /**
     * 查询已配置的站点规则列表（只返回展示需要的字段）
     *
     * @return 站点规则摘要列表
     */
    @GetMapping("/crawl/sources")
    public ApiResult<List<Map<String, Object>>> sources() {
        List<Map<String, Object>> list = properties.getSources().stream()
                .map(rule -> Map.<String, Object>of(
                        "name", rule.getName() == null ? "" : rule.getName(),
                        "remark", rule.getRemark() == null ? "" : rule.getRemark(),
                        "urlPrefixes", rule.getUrlPrefixes() == null ? List.of() : rule.getUrlPrefixes()))
                .toList();
        return ApiResult.ok(list);
    }

    /**
     * 查询小说类别列表（book_category 表）
     *
     * @return 类别列表
     */
    @GetMapping("/categories")
    public ApiResult<List<BookCategory>> categories() {
        return ApiResult.ok(bookStoreService.listCategories());
    }

}
