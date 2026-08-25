package io.github.xxyopen.novel.controller.admin;

import io.github.xxyopen.novel.core.common.req.PageReqDto;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.AdminNewsReqDto;
import io.github.xxyopen.novel.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SNewsController", description = "平台管理后台-新闻模块")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX + "/news")
@RequiredArgsConstructor
public class SNewsController {

    private final AdminService adminService;

    @Operation(summary = "新闻分页")
    @GetMapping("page")
    public RestResp<PageRespDto<Map<String, Object>>> listNews(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword) {
        return adminService.listNews(pageReqDto, keyword);
    }

    @Operation(summary = "新增新闻")
    @PostMapping
    public RestResp<Void> saveNews(@Valid @RequestBody AdminNewsReqDto dto) {
        return adminService.saveNews(dto);
    }

    @Operation(summary = "修改新闻")
    @PutMapping("{id}")
    public RestResp<Void> updateNews(@PathVariable Long id, @Valid @RequestBody AdminNewsReqDto dto) {
        return adminService.updateNews(id, dto);
    }

    @Operation(summary = "删除新闻")
    @DeleteMapping("{id}")
    public RestResp<Void> deleteNews(@PathVariable Long id) {
        return adminService.deleteNews(id);
    }

}

