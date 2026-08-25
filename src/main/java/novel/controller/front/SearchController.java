package novel.controller.front;

import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.dto.req.BookSearchReqDto;
import novel.dto.resp.BookInfoRespDto;
import novel.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台门户-搜索模块 API 控制器
 *
 * @author xiongxiaoyang
 * @date 2022/5/27
 */
@Tag(name = "SearchController", description = "前台门户-搜索模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_SEARCH_URL_PREFIX)
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 小说搜索接口
     */
    @Operation(summary = "小说搜索接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> searchBooks(
        @ParameterObject BookSearchReqDto condition) {
        return searchService.searchBooks(condition);
    }

}
