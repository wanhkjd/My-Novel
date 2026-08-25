package io.github.xxyopen.novel.crawl.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：把异常统一转成 ApiResult 结构返回给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验失败异常
     *
     * @param e 参数校验异常
     * @return 带首个校验错误信息的失败结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidException(MethodArgumentNotValidException e) {
        // 取第一条校验错误提示返回
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ApiResult.fail(message);
    }

    /**
     * 处理上传文件超过大小限制异常
     *
     * @param e 文件超限异常
     * @return 失败结果
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleMaxUploadException(MaxUploadSizeExceededException e) {
        return ApiResult.fail("上传文件超过大小限制，请调整 spring.servlet.multipart 配置");
    }

    /**
     * 处理业务异常（参数不合法、状态冲突等，直接把原因返回给前端）
     *
     * @param e 业务异常
     * @return 失败结果
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ApiResult<Void> handleBizException(RuntimeException e) {
        return ApiResult.fail(e.getMessage());
    }

    /**
     * 处理其余未预期异常
     *
     * @param e 未预期异常
     * @return 失败结果
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        log.error("接口处理异常", e);
        return ApiResult.fail("服务器异常：" + e.getMessage());
    }

}
