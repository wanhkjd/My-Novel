package novel.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.constant.SystemConfigConsts;
import novel.core.interceptor.AuthInterceptor;
import novel.core.util.JwtUtils;
import novel.dao.entity.SysUser;
import novel.dao.mapper.SysUserMapper;
import novel.dto.AuthorInfoDto;
import novel.dto.UserInfoDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.UserInfoCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class UserBanAuthTest {
    @Mock private JwtUtils jwtUtils;
    @Mock private UserInfoCacheManager users;
    @Mock private AuthorInfoCacheManager authors;
    @Mock private SysUserMapper admins;
    private FrontAuthStrategy front;
    private AuthorAuthStrategy author;

    @BeforeEach
    void setUp() {
        UserHolder.clear();
        front = new FrontAuthStrategy(jwtUtils, users);
        author = new AuthorAuthStrategy(jwtUtils, users, authors);
    }

    @AfterEach
    void clearIdentity() {
        UserHolder.clear();
    }

    @Test
    void normalReaderIsAuthenticated() {
        stubReader(0);
        front.auth("existing-token", "/api/front/user/bookshelf");
        assertThat(UserHolder.getUserId()).isEqualTo(10L);
    }

    @Test
    void normalAuthorStillReceivesAuthorIdentity() {
        stubReader(0);
        when(authors.getAuthor(10L)).thenReturn(AuthorInfoDto.builder().id(20L).build());
        author.auth("existing-token", "/api/author/book/list");
        assertThat(UserHolder.getUserId()).isEqualTo(10L);
        assertThat(UserHolder.getAuthorId()).isEqualTo(20L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/front/user/bookshelf", "/api/author/book/list",
        "/api/author/register", "/api/author/status"})
    void bannedReaderCannotReuseTokenIncludingAuthorExclusions(String uri) {
        stubReader(1);
        AuthStrategy strategy = uri.startsWith("/api/front/") ? front : author;
        assertThatThrownBy(() -> strategy.auth("existing-token", uri))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_ACCOUNT_BANNED));
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(UserHolder.getAuthorId()).isNull();
        verifyNoInteractions(authors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/front/user/bookshelf", "/api/author/book/list"})
    void interceptorRejectsWithBusinessErrorInsteadOfSuccess(String uri) throws Exception {
        stubReader(1);
        ObjectMapper mapper = new ObjectMapper();
        AuthInterceptor interceptor = new AuthInterceptor(
            Map.of("frontAuthStrategy", front, "authorAuthStrategy", author), mapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME, "existing-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("A0202");
        assertThat(body.get("message").asText()).isEqualTo("用户账号已被封禁");
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    void invalidTokenKeepsExistingError() {
        when(jwtUtils.parseToken("invalid", SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn(null);
        assertThatThrownBy(() -> front.auth("invalid", "/api/front/user/bookshelf"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_LOGIN_EXPIRED));
        verifyNoInteractions(users);
    }

    @Test
    void enabledAdminUsesItsOwnStatusSemantics() {
        when(jwtUtils.parseToken("admin-token", SystemConfigConsts.NOVEL_ADMIN_KEY)).thenReturn(30L);
        SysUser admin = new SysUser();
        admin.setId(30L);
        admin.setStatus(1);
        when(admins.selectById(30L)).thenReturn(admin);
        new AdminAuthStrategy(jwtUtils, admins).auth("admin-token", "/api/admin/users");
        assertThat(UserHolder.getUserId()).isEqualTo(30L);
        verifyNoInteractions(users);
    }

    private void stubReader(int status) {
        when(jwtUtils.parseToken(anyString(), eq(SystemConfigConsts.NOVEL_FRONT_KEY))).thenReturn(10L);
        when(users.getUserForAuth(10L)).thenReturn(UserInfoDto.builder().id(10L).status(status).build());
    }
}
