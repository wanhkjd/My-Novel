package novel.controller.front;

import novel.core.auth.UserHolder;
import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.core.constant.SystemConfigConsts;
import novel.dto.req.UserCommentReqDto;
import novel.dto.req.UserInfoUptReqDto;
import novel.dto.req.UserLoginReqDto;
import novel.dto.req.UserRegisterReqDto;
import novel.dto.resp.UserCommentRespDto;
import novel.dto.resp.UserFeedbackRespDto;
import novel.dto.resp.UserInfoRespDto;
import novel.dto.resp.UserLoginRespDto;
import novel.dto.resp.UserRegisterRespDto;
import novel.dto.resp.BookInfoRespDto;
import novel.service.BookService;
import novel.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 前台门户-会员模块 API 控制器
 *
 * @author xiongxiaoyang
 * @date 2022/5/17
 */
@Tag(name = "UserController", description = "前台门户-会员模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX)
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final BookService bookService;

    /**
     * 用户注册接口
     */
    @Operation(summary = "用户注册接口")
    @PostMapping("register")
    public RestResp<UserRegisterRespDto> register(@Valid @RequestBody UserRegisterReqDto dto) {
        return userService.register(dto);
    }

    /**
     * 用户登录接口
     */
    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public RestResp<UserLoginRespDto> login(@Valid @RequestBody UserLoginReqDto dto) {
        return userService.login(dto);
    }

    /**
     * 用户信息查询接口
     */
    @Operation(summary = "用户信息查询接口")
    @GetMapping
    public RestResp<UserInfoRespDto> getUserInfo() {
        return userService.getUserInfo(UserHolder.getUserId());
    }

    /**
     * 用户信息修改接口
     */
    @Operation(summary = "用户信息修改接口")
    @PutMapping
    public RestResp<Void> updateUserInfo(@Valid @RequestBody UserInfoUptReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return userService.updateUserInfo(dto);
    }

    /**
     * 用户反馈提交接口
     */
    @Operation(summary = "用户反馈提交接口")
    @PostMapping("feedback")
    public RestResp<Void> submitFeedback(
        @Length(min = 5, max = 512, message = "反馈内容长度必须在 5 到 512 个字符之间")
        @RequestBody String content) {
        return userService.saveFeedback(UserHolder.getUserId(), content);
    }

    /**
     * 分页查询用户反馈接口
     */
    @Operation(summary = "查询会员反馈列表接口")
    @GetMapping("feedbacks")
    public RestResp<PageRespDto<UserFeedbackRespDto>> listFeedbacks(PageReqDto pageReqDto) {
        return userService.listFeedbacks(UserHolder.getUserId(), pageReqDto);
    }

    /**
     * 用户反馈删除接口
     */
    @Operation(summary = "用户反馈删除接口")
    @DeleteMapping("feedback/{id}")
    public RestResp<Void> deleteFeedback(@Parameter(description = "反馈ID") @PathVariable Long id) {
        return userService.deleteFeedback(UserHolder.getUserId(), id);
    }

    /**
     * 发表评论接口
     */
    @Operation(summary = "发表评论接口")
    @PostMapping("comment")
    public RestResp<Void> comment(@Valid @RequestBody UserCommentReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return bookService.saveComment(dto);
    }

    /**
     * 修改评论接口
     */
    @Operation(summary = "修改评论接口")
    @PutMapping("comment/{id}")
    public RestResp<Void> updateComment(@Parameter(description = "评论ID") @PathVariable Long id,
        String content) {
        return bookService.updateComment(UserHolder.getUserId(), id, content);
    }

    /**
     * 删除评论接口
     */
    @Operation(summary = "删除评论接口")
    @DeleteMapping("comment/{id}")
    public RestResp<Void> deleteComment(@Parameter(description = "评论ID") @PathVariable Long id) {
        return bookService.deleteComment(UserHolder.getUserId(), id);
    }

    /**
     * 查询书架状态接口 0-不在书架 1-已在书架
     */
    @Operation(summary = "查询书架状态接口")
    @GetMapping("bookshelf_status")
    public RestResp<Integer> getBookshelfStatus(@Parameter(description = "小说ID") String bookId) {
        return userService.getBookshelfStatus(UserHolder.getUserId(), bookId);
    }

    /**
     * 添加小说到书架接口
     */
    @Operation(summary = "添加小说到书架接口")
    @PostMapping("bookshelf/{bookId}")
    public RestResp<Void> addBookshelf(
        @Parameter(description = "小说ID") @PathVariable Long bookId) {
        return userService.addBookshelf(UserHolder.getUserId(), bookId);
    }

    /**
     * 分页查询书架接口
     */
    @Operation(summary = "查询会员书架列表接口")
    @GetMapping("bookshelf")
    public RestResp<PageRespDto<BookInfoRespDto>> listBookshelf(PageReqDto pageReqDto) {
        return userService.listBookshelf(UserHolder.getUserId(), pageReqDto);
    }

    /**
     * 从书架移除小说接口
     */
    @Operation(summary = "从书架移除小说接口")
    @DeleteMapping("bookshelf/{bookId}")
    public RestResp<Void> removeBookshelf(
        @Parameter(description = "小说ID") @PathVariable Long bookId) {
        return userService.removeBookshelf(UserHolder.getUserId(), bookId);
    }

    /**
     * 分页查询评论
     */
    @Operation(summary = "查询会员评论列表接口")
    @GetMapping("comments")
    public RestResp<PageRespDto<UserCommentRespDto>> listComments(PageReqDto pageReqDto) {
        return bookService.listComments(UserHolder.getUserId(), pageReqDto);
    }

}
