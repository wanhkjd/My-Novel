package novel.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import novel.core.annotation.ValidCommentContent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户发表评论 请求DTO
 * @author xiongxiaoyang
 * @date 2022/5/17
 */
@Data
public class UserCommentReqDto {

    private Long userId;

    @Schema(description = "小说ID", required = true)
    @NotNull(message="小说ID不能为空！")
    private Long bookId;

    @Schema(description = "评论内容", required = true)
    @ValidCommentContent
    private String commentContent;

}
