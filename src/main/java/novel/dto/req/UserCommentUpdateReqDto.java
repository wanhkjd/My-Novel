package novel.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import novel.core.annotation.ValidCommentContent;

/** 修改评论的表单请求，保持既有 content 字段与 URL 不变。 */
@Data
public class UserCommentUpdateReqDto {

    @Schema(description = "评论内容", required = true)
    @ValidCommentContent
    private String content;
}
