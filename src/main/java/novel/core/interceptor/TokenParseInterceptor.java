package novel.core.interceptor;

import novel.core.auth.UserHolder;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Token 解析拦截器
 *
 * @author xiongxiaoyang
 * @date 2022/5/27
 */
@Component
@RequiredArgsConstructor
public class TokenParseInterceptor implements AsyncHandlerInterceptor {

    private final JwtUtils jwtUtils;

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
        Object handler) throws Exception {
        // 可选登录入口也必须覆盖空 Token，不能继承上一请求的读者或作家身份。
        UserHolder.clear();
        // 获取登录 JWT
        String token = request.getHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME);
        if (StringUtils.hasText(token)) {
            // 解析 token 并保存
            UserHolder.setUserId(jwtUtils.parseToken(token, SystemConfigConsts.NOVEL_FRONT_KEY));
        }
        return AsyncHandlerInterceptor.super.preHandle(request, response, handler);
    }

    /**
     * 异步移交时立即清理原请求线程，不等待另一线程上的完成回调。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserHolder.clear();
    }

    /**
     * DispatcherServlet 完全处理完请求后调用，出现异常照常调用
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
        throws Exception {
        // 清理当前线程保存的用户数据
        UserHolder.clear();
        AsyncHandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

}
