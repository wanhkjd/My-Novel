package novel.controller.admin;

import novel.core.auth.UserHolder;
import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.dto.req.AdminLoginReqDto;
import novel.dto.req.AdminStatusReqDto;
import novel.dto.resp.AdminLoginRespDto;
import novel.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminController", description = "平台管理后台-管理员模块")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX)
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "管理员登录")
    @PostMapping("login")
    public RestResp<AdminLoginRespDto> login(@Valid @RequestBody AdminLoginReqDto dto) {
        return adminService.login(dto);
    }

    @Operation(summary = "当前管理员信息")
    @GetMapping("info")
    public RestResp<Map<String, Object>> info() {
        return adminService.info(UserHolder.getUserId());
    }

    @Operation(summary = "管理员菜单树")
    @GetMapping("menus")
    public RestResp<List<Map<String, Object>>> menus() {
        return adminService.menus();
    }

    @Operation(summary = "平台数据总览")
    @GetMapping("dashboard")
    public RestResp<Map<String, Object>> dashboard() {
        return adminService.dashboard();
    }

    @Operation(summary = "会员用户分页列表")
    @GetMapping("user/page")
    public RestResp<PageRespDto<Map<String, Object>>> listUsers(@ParameterObject PageReqDto pageReqDto,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status) {
        return adminService.listUsers(pageReqDto, keyword, status);
    }

    @Operation(summary = "会员用户详情")
    @GetMapping("user/{id}")
    public RestResp<Map<String, Object>> getUser(@PathVariable Long id) {
        return adminService.getUser(id);
    }

    @Operation(summary = "修改会员状态")
    @PutMapping("user/{id}/status")
    public RestResp<Void> updateUserStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusReqDto dto) {
        return adminService.updateUserStatus(id, dto);
    }

}

