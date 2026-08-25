package io.github.xxyopen.novel.core.auth;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.constant.SystemConfigConsts;
import io.github.xxyopen.novel.core.util.JwtUtils;
import io.github.xxyopen.novel.dao.entity.SysUser;
import io.github.xxyopen.novel.dao.mapper.SysUserMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 平台后台管理系统认证授权策略
 */
@Component
@RequiredArgsConstructor
public class AdminAuthStrategy implements AuthStrategy {

    private final JwtUtils jwtUtils;

    private final SysUserMapper sysUserMapper;

    @Override
    public void auth(String token, String requestUri) throws BusinessException {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }
        Long adminId = jwtUtils.parseToken(token, SystemConfigConsts.NOVEL_ADMIN_KEY);
        if (Objects.isNull(adminId)) {
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }
        SysUser sysUser = sysUserMapper.selectById(adminId);
        if (Objects.isNull(sysUser)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
        if (Objects.equals(sysUser.getStatus(), 0)) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        UserHolder.setUserId(adminId);
    }

}

