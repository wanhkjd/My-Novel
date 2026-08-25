package io.github.xxyopen.novel.controller.admin;

import io.github.xxyopen.novel.core.common.req.PageReqDto;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.AdminStatusReqDto;
import io.github.xxyopen.novel.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SAuthorController", description = "平台管理后台-作者模块")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX + "/author")
@RequiredArgsConstructor
public class SAuthorController {

    private final AdminService adminService;

    @Operation(summary = "作者分页列表")
    @GetMapping("page")
    public RestResp<PageRespDto<Map<String, Object>>> listAuthors(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status) {
        return adminService.listAuthors(pageReqDto, keyword, status);
    }

    @Operation(summary = "作者详情")
    @GetMapping("{id}")
    public RestResp<Map<String, Object>> getAuthor(@PathVariable Long id) {
        return adminService.getAuthor(id);
    }

    @Operation(summary = "修改作者状态")
    @PutMapping("{id}/status")
    public RestResp<Void> updateAuthorStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusReqDto dto) {
        return adminService.updateAuthorStatus(id, dto);
    }

}

