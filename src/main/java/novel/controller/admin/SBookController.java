package novel.controller.admin;

import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.dto.req.AdminAuditReqDto;
import novel.dto.req.AdminBookCategoryReqDto;
import novel.dto.req.AdminStatusReqDto;
import novel.service.AdminService;
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

@Tag(name = "SBookController", description = "平台管理后台-小说模块")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX + "/book")
@RequiredArgsConstructor
public class SBookController {

    private final AdminService adminService;

    @Operation(summary = "小说分页列表")
    @GetMapping("page")
    public RestResp<PageRespDto<Map<String, Object>>> listBooks(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status) {
        return adminService.listBooks(pageReqDto, keyword, status);
    }

    @Operation(summary = "修改小说状态")
    @PutMapping("{id}/status")
    public RestResp<Void> updateBookStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusReqDto dto) {
        return adminService.updateBookStatus(id, dto);
    }

    @Operation(summary = "小说分类分页")
    @GetMapping("category/page")
    public RestResp<PageRespDto<Map<String, Object>>> listCategories(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword) {
        return adminService.listCategories(pageReqDto, keyword);
    }

    @Operation(summary = "新增小说分类")
    @PostMapping("category")
    public RestResp<Void> saveCategory(@Valid @RequestBody AdminBookCategoryReqDto dto) {
        return adminService.saveCategory(dto);
    }

    @Operation(summary = "修改小说分类")
    @PutMapping("category/{id}")
    public RestResp<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody AdminBookCategoryReqDto dto) {
        return adminService.updateCategory(id, dto);
    }

    @Operation(summary = "删除小说分类")
    @DeleteMapping("category/{id}")
    public RestResp<Void> deleteCategory(@PathVariable Long id) {
        return adminService.deleteCategory(id);
    }

    @Operation(summary = "小说评论分页")
    @GetMapping("comment/page")
    public RestResp<PageRespDto<Map<String, Object>>> listComments(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer auditStatus) {
        return adminService.listComments(pageReqDto, keyword, auditStatus);
    }

    @Operation(summary = "审核小说评论")
    @PutMapping("comment/{id}/audit")
    public RestResp<Void> auditComment(@PathVariable Long id, @Valid @RequestBody AdminAuditReqDto dto) {
        return adminService.auditComment(id, dto);
    }

    @Operation(summary = "删除小说评论")
    @DeleteMapping("comment/{id}")
    public RestResp<Void> deleteComment(@PathVariable Long id) {
        return adminService.deleteComment(id);
    }

}

