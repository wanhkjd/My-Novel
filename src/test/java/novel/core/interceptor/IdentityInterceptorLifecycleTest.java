package novel.core.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import novel.core.auth.AuthStrategy;
import novel.core.auth.UserHolder;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Servlet callbacks run on the owning thread; reactive termination may run elsewhere. */
class IdentityInterceptorLifecycleTest {
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/front/ai/companion/chat/stream");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() { UserHolder.clear(); }

    @Test
    void authenticationStartsWithoutPreviousReaderOrAuthor() throws Exception {
        dirtyIdentity();
        AuthInterceptor interceptor = auth((token, uri) -> {
            assertEmpty();
            UserHolder.setUserId(7L);
            UserHolder.setAuthorId(70L);
        });
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserHolder.getUserId()).isEqualTo(7L);
        assertThat(UserHolder.getAuthorId()).isEqualTo(70L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedAuthenticationClearsEvenPartiallyEstablishedIdentity(boolean unexpected) throws Exception {
        AuthInterceptor interceptor = auth((token, uri) -> {
            dirtyIdentity();
            if (unexpected) throw new IllegalStateException("database unavailable");
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        });
        if (unexpected) {
            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(IllegalStateException.class);
        } else {
            assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
            assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("code").asText())
                    .isEqualTo(ErrorCodeEnum.USER_UN_AUTH.getCode());
        }
        // Spring does not call this interceptor's afterCompletion when its preHandle fails.
        assertEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "invalid", "reader", "admin"})
    void optionalTokenParsingNeverInheritsAnOldIdentity(String identity) throws Exception {
        JwtUtils jwt = jwt();
        if (!identity.equals("absent")) {
            String token = switch (identity) {
                case "reader" -> jwt.generateToken(7L, SystemConfigConsts.NOVEL_FRONT_KEY);
                case "admin" -> jwt.generateToken(7L, SystemConfigConsts.NOVEL_ADMIN_KEY);
                default -> "invalid-token";
            };
            request.addHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME, token);
        }
        dirtyIdentity();
        assertThat(new TokenParseInterceptor(jwt).preHandle(request, response, new Object())).isTrue();
        assertThat(UserHolder.getUserId()).isEqualTo(identity.equals("reader") ? 7L : null);
        assertThat(UserHolder.getAuthorId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth", "token"})
    void asyncHandoffClearsTheOriginalServletThread(String kind) throws Exception {
        HandlerInterceptor interceptor = interceptor(kind);
        dirtyIdentity();
        assertThat(interceptor).isInstanceOf(AsyncHandlerInterceptor.class);
        ((AsyncHandlerInterceptor) interceptor).afterConcurrentHandlingStarted(request, response, new Object());
        assertEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth", "token"})
    void normalAndExceptionalCompletionBothClearIdentity(String kind) throws Exception {
        HandlerInterceptor interceptor = interceptor(kind);
        dirtyIdentity();
        interceptor.afterCompletion(request, response, new Object(), null);
        assertEmpty();
        dirtyIdentity();
        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("handler failed"));
        assertEmpty();
        interceptor.afterCompletion(request, response, new Object(), null);
        assertEmpty();
    }

    private HandlerInterceptor interceptor(String kind) {
        return kind.equals("auth") ? auth((token, uri) -> {}) : new TokenParseInterceptor(jwt());
    }

    private AuthInterceptor auth(AuthStrategy strategy) {
        return new AuthInterceptor(Map.of("frontAuthStrategy", strategy), new ObjectMapper());
    }

    private JwtUtils jwt() {
        JwtUtils jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "sse-identity-fixture-only-secret-at-least-32-bytes");
        return jwt;
    }

    private void dirtyIdentity() {
        UserHolder.setUserId(999L);
        UserHolder.setAuthorId(9990L);
    }

    private void assertEmpty() {
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(UserHolder.getAuthorId()).isNull();
    }
}
