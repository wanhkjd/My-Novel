package novel.core.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import novel.core.common.validation.CommentContentRules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** HTTP 请求的校验适配器，业务规则只定义在 CommentContentRules 中。 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCommentContent.ContentValidator.class)
public @interface ValidCommentContent {
    String message() default "评论不能为空且长度必须在 " + CommentContentRules.MIN_LENGTH
        + " 到 " + CommentContentRules.MAX_LENGTH + " 个字符之间";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    final class ContentValidator implements ConstraintValidator<ValidCommentContent, String> {
        @Override
        public boolean isValid(String content, ConstraintValidatorContext context) {
            return CommentContentRules.isValid(content);
        }
    }
}
