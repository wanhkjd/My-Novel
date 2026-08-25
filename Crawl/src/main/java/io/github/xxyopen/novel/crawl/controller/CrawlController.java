package io.github.xxyopen.novel.crawl.controller;

import io.github.xxyopen.novel.crawl.common.ApiResult;
import io.github.xxyopen.novel.crawl.dto.CrawlBookRequest;
import io.github.xxyopen.novel.crawl.service.CrawlService;
import io.github.xxyopen.novel.crawl.task.TaskInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 爬取接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrawlController {

    // 爬取服务
    private final CrawlService crawlService;

    /**
     * 提交爬取小说任务
     *
     * @param request 爬取请求参数（站点标识、书籍目录页URL、类别等）
     * @return 新建的任务信息（含任务ID，用于轮询进度）
     */
    @PostMapping("/crawl")
    public ApiResult<TaskInfo> crawl(@Valid @RequestBody CrawlBookRequest request) {
        return ApiResult.ok(crawlService.submit(request));
    }

}
