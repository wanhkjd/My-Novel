package novel.controller.admin;

import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.dto.req.AdminFriendLinkReqDto;
import novel.dto.req.AdminHomeBookReqDto;
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

@Tag(name = "SHomeController", description = "平台管理后台-首页模块")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX + "/home")
@RequiredArgsConstructor
public class SHomeController {

    private final AdminService adminService;

    @Operation(summary = "首页推荐分页")
    @GetMapping("book/page")
    public RestResp<PageRespDto<Map<String, Object>>> listHomeBooks(@ParameterObject PageReqDto pageReqDto) {
        return adminService.listHomeBooks(pageReqDto);
    }

    @Operation(summary = "新增首页推荐")
    @PostMapping("book")
    public RestResp<Void> saveHomeBook(@Valid @RequestBody AdminHomeBookReqDto dto) {
        return adminService.saveHomeBook(dto);
    }

    @Operation(summary = "修改首页推荐")
    @PutMapping("book/{id}")
    public RestResp<Void> updateHomeBook(@PathVariable Long id, @Valid @RequestBody AdminHomeBookReqDto dto) {
        return adminService.updateHomeBook(id, dto);
    }

    @Operation(summary = "删除首页推荐")
    @DeleteMapping("book/{id}")
    public RestResp<Void> deleteHomeBook(@PathVariable Long id) {
        return adminService.deleteHomeBook(id);
    }

    @Operation(summary = "友情链接分页")
    @GetMapping("friend-link/page")
    public RestResp<PageRespDto<Map<String, Object>>> listFriendLinks(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword) {
        return adminService.listFriendLinks(pageReqDto, keyword);
    }

    @Operation(summary = "新增友情链接")
    @PostMapping("friend-link")
    public RestResp<Void> saveFriendLink(@Valid @RequestBody AdminFriendLinkReqDto dto) {
        return adminService.saveFriendLink(dto);
    }

    @Operation(summary = "修改友情链接")
    @PutMapping("friend-link/{id}")
    public RestResp<Void> updateFriendLink(@PathVariable Long id, @Valid @RequestBody AdminFriendLinkReqDto dto) {
        return adminService.updateFriendLink(id, dto);
    }

    @Operation(summary = "删除友情链接")
    @DeleteMapping("friend-link/{id}")
    public RestResp<Void> deleteFriendLink(@PathVariable Long id) {
        return adminService.deleteFriendLink(id);
    }

}

