package novel.core.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对话历史消息 DTO（供前端刷新回显）
 *
 * @author AI
 * @date 2026/09/21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryMsgDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;
}
