package novel.core.common.validation;

import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;

/** 发布与编辑评论共用的内容规则；不裁剪或改写用户输入。 */
public final class CommentContentRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 512;

    private CommentContentRules() {}

    /** 长度沿用原 @Length 的 UTF-16 计数方式，Emoji 等补充字符占两个单位。 */
    public static boolean isValid(String content) {
        return content != null && !content.isBlank()
            && content.length() >= MIN_LENGTH && content.length() <= MAX_LENGTH;
    }

    /** 服务内同样执行规则，避免非 HTTP 调用绕过 DTO 校验。 */
    public static void requireValid(String content) {
        if (!isValid(content)) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
    }
}
