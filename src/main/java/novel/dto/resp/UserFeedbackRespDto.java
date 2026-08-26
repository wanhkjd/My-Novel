package novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 用户反馈响应 Dto
 */
@Data
@Builder
public class UserFeedbackRespDto {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "提交时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
