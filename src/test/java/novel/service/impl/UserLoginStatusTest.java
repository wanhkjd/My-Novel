package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.BCryptPasswordHelper;
import novel.core.util.JwtUtils;
import novel.dao.entity.UserInfo;
import novel.dao.mapper.UserInfoMapper;
import novel.dto.req.UserLoginReqDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLoginStatusTest {
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private BCryptPasswordHelper passwordHelper;
    @Mock private JwtUtils jwtUtils;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void normalUserCanLogIn() {
        stubUser(0, true);
        when(jwtUtils.generateToken(10L, SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn("token");

        var response = userService.login(request());

        assertThat(response.isOk()).isTrue();
        assertThat(response.getData().getToken()).isEqualTo("token");
        assertThat(response.getData().getUid()).isEqualTo(10L);
    }

    @Test
    void bannedUserCannotObtainToken() {
        stubUser(1, true);
        assertLoginFails(ErrorCodeEnum.USER_ACCOUNT_BANNED);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void unbannedUserCanLogInAgain() {
        UserInfo user = stubUser(1, true);
        assertLoginFails(ErrorCodeEnum.USER_ACCOUNT_BANNED);
        user.setStatus(0);
        when(jwtUtils.generateToken(10L, SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn("token");
        assertThat(userService.login(request()).isOk()).isTrue();
    }

    @Test
    void wrongPasswordDoesNotRevealBanStatus() {
        stubUser(1, false);
        assertLoginFails(ErrorCodeEnum.USER_PASSWORD_ERROR);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void missingAccountKeepsExistingError() {
        assertLoginFails(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        verifyNoInteractions(passwordHelper, jwtUtils);
    }

    private UserInfo stubUser(int status, boolean passwordMatches) {
        UserInfo user = new UserInfo();
        user.setId(10L);
        user.setStatus(status);
        user.setPassword("encoded-password");
        when(userInfoMapper.selectOne(any())).thenReturn(user);
        when(passwordHelper.matches("password", "encoded-password")).thenReturn(passwordMatches);
        return user;
    }

    private UserLoginReqDto request() {
        UserLoginReqDto request = new UserLoginReqDto();
        request.setUsername("reader");
        request.setPassword("password");
        return request;
    }

    private void assertLoginFails(ErrorCodeEnum expected) {
        assertThatThrownBy(() -> userService.login(request()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(expected));
    }
}
