package novel.core.auth;

import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.constant.ApiRouterConsts;
import novel.core.constant.DatabaseConsts;
import novel.core.util.JwtUtils;
import novel.dto.AuthorInfoDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.UserInfoCacheManager;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 作家后台管理系统 认证授权策略
 *
 * @author xiongxiaoyang
 * @date 2022/5/18
 */
@Component
@RequiredArgsConstructor
public class AuthorAuthStrategy implements AuthStrategy {

    private final JwtUtils jwtUtils;

    private final UserInfoCacheManager userInfoCacheManager;

    private final AuthorInfoCacheManager authorInfoCacheManager;

    /**
     * 不需要进行作家权限认证的 URI
     */
    private static final List<String> EXCLUDE_URI = List.of(
        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/register",
        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/status"
    );

    @Override
    public void auth(String token, String requestUri) throws BusinessException {
        // 统一账号认证
        Long userId = authSSO(jwtUtils, userInfoCacheManager, token);
        if (EXCLUDE_URI.contains(requestUri)) {
            // 该请求不需要进行作家权限认证
            return;
        }
        // 作家权限认证
        AuthorInfoDto authorInfo = authorInfoCacheManager.getAuthorForAuth(userId);
        if (Objects.isNull(authorInfo)) {
            // 作家账号不存在，无权访问作家专区
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }

        if (Objects.equals(authorInfo.getStatus(), DatabaseConsts.AuthorInfoTable.STATUS_BANNED)) {
            throw new BusinessException(ErrorCodeEnum.AUTHOR_ACCOUNT_BANNED);
        }
        if (!Objects.equals(authorInfo.getStatus(), DatabaseConsts.AuthorInfoTable.STATUS_NORMAL)) {
            // 只有明确正常的作家可获得写作权限，空值或未知状态不放行。
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 设置作家ID到当前线程
        UserHolder.setAuthorId(authorInfo.getId());
    }
    
}