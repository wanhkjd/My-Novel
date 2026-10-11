package novel.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import novel.controller.admin.AdminAiController;
import novel.controller.front.AiBookCompanionController;
import novel.controller.front.ResourceController;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.resp.RestResp;
import novel.core.config.WebConfig;
import novel.core.constant.SystemConfigConsts;
import novel.core.interceptor.AuthInterceptor;
import novel.core.interceptor.FileInterceptor;
import novel.core.interceptor.FlowLimitInterceptor;
import novel.core.interceptor.TokenParseInterceptor;
import novel.core.util.JwtUtils;
import novel.dao.entity.SysUser;
import novel.dao.mapper.SysUserMapper;
import novel.dto.UserInfoDto;
import novel.dto.resp.ImgVerifyCodeRespDto;
import novel.manager.cache.UserInfoCacheManager;
import novel.service.BookCompanionService;
import novel.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/** Real routing, JWT parsing and auth strategies; no database, model, Redis or disk writes. */
class SensitiveWriteAuthorizationTest {
    private static final String SYNC = "/api/admin/ai/sync-vectors";
    private static final String LEGACY_SYNC = "/api/front/ai/companion/sync-vectors";
    private static final String UPLOAD = "/api/front/resource/image";
    private static final String CAPTCHA = "/api/front/resource/img_verify_code";
    private static final String HEADER = SystemConfigConsts.HTTP_AUTH_HEADER_NAME;

    private final BookCompanionService companion = mock(BookCompanionService.class);
    private final ResourceService resources = mock(ResourceService.class);
    private final UserInfoCacheManager users = mock(UserInfoCacheManager.class);
    private final SysUserMapper admins = mock(SysUserMapper.class);
    private MockMvc mvc;
    private String readerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        UserHolder.clear();
        JwtUtils jwt = new JwtUtils();
        // Fixture-only secret: no application config or live credentials are read.
        ReflectionTestUtils.setField(jwt, "secret", "sensitive-write-auth-test-secret-at-least-32-bytes");
        // Identical numeric IDs deliberately verify that token namespaces cannot be confused.
        readerToken = jwt.generateToken(7L, SystemConfigConsts.NOVEL_FRONT_KEY);
        adminToken = jwt.generateToken(7L, SystemConfigConsts.NOVEL_ADMIN_KEY);
        AuthInterceptor auth = new AuthInterceptor(Map.of(
            "frontAuthStrategy", new FrontAuthStrategy(jwt, users),
            "adminAuthStrategy", new AdminAuthStrategy(jwt, admins)), new ObjectMapper());
        FlowLimitInterceptor flow = mock(FlowLimitInterceptor.class);
        when(flow.preHandle(any(), any(), any())).thenReturn(true);
        InterceptorRegistry registry = new InterceptorRegistry();
        new WebConfig(flow, auth, mock(FileInterceptor.class), new TokenParseInterceptor(jwt))
            .addInterceptors(registry);
        // Use production include/exclude paths and ordering, not hand-copied test mappings.
        List<HandlerInterceptor> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        mvc = MockMvcBuilders.standaloneSetup(new AdminAiController(companion),
                new AiBookCompanionController(companion), new ResourceController(resources))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .addInterceptors(interceptors.toArray(HandlerInterceptor[]::new)).build();
    }

    @AfterEach
    void clearIdentity() { UserHolder.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-jwt"})
    void syncRejectsAnonymousAndInvalidTokens(String token) throws Exception {
        expectRejected(post(SYNC).header(HEADER, token), ErrorCodeEnum.USER_LOGIN_EXPIRED);
        verifyNoInteractions(companion, resources, users, admins);
    }

    @Test
    void readerOrAuthorTokenCannotTriggerAdminSyncEvenWithSameNumericId() throws Exception {
        // Authors use the same front token namespace as readers.
        expectRejected(post(SYNC).header(HEADER, readerToken), ErrorCodeEnum.USER_LOGIN_EXPIRED);
        verifyNoInteractions(companion, resources, users, admins);
    }

    @Test
    void disabledAdminCannotTriggerSync() throws Exception {
        stubAdmin(0);
        expectRejected(post(SYNC).header(HEADER, adminToken), ErrorCodeEnum.USER_UN_AUTH);
        verifyNoInteractions(companion, resources, users);
    }

    @Test
    void deletedAdminCannotTriggerSync() throws Exception {
        expectRejected(post(SYNC).header(HEADER, adminToken), ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        verifyNoInteractions(companion, resources, users);
    }

    @Test
    void enabledAdminCanTriggerSyncExactlyOnce() throws Exception {
        stubAdmin(1);
        when(companion.syncAllBookVectors()).thenAnswer(call -> {
            assertThat(UserHolder.getUserId()).isEqualTo(7L);
            return RestResp.ok(12);
        });
        mvc.perform(post(SYNC).header(HEADER, adminToken).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
            .andExpect(jsonPath("$.data").value(12));
        verify(companion, times(1)).syncAllBookVectors();
        verifyNoInteractions(users, resources);
        assertThat(UserHolder.getUserId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "reader", "admin"})
    void legacySyncRouteHasNoHandler(String identity) throws Exception {
        String token = switch (identity) { case "reader" -> readerToken; case "admin" -> adminToken; default -> ""; };
        mvc.perform(post(LEGACY_SYNC).header(HEADER, token)).andExpect(status().isNotFound());
        verifyNoInteractions(companion, resources, users, admins);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-jwt"})
    void uploadRejectsAnonymousAndInvalidTokens(String token) throws Exception {
        expectRejected(upload().header(HEADER, token), ErrorCodeEnum.USER_LOGIN_EXPIRED);
        verifyNoInteractions(companion, resources, users, admins);
    }

    @Test
    void uploadRejectsBannedReader() throws Exception {
        stubReader(1);
        expectRejected(upload().header(HEADER, readerToken), ErrorCodeEnum.USER_ACCOUNT_BANNED);
        verifyNoInteractions(companion, resources, admins);
    }

    @Test
    void uploadRejectsDeletedReader() throws Exception {
        expectRejected(upload().header(HEADER, readerToken), ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        verifyNoInteractions(companion, resources, admins);
    }

    @Test
    void uploadDoesNotAcceptAdminTokenAsReaderToken() throws Exception {
        expectRejected(upload().header(HEADER, adminToken), ErrorCodeEnum.USER_LOGIN_EXPIRED);
        verifyNoInteractions(companion, resources, users, admins);
    }

    @Test
    void normalReaderOrAuthorCanUploadExactlyOnce() throws Exception {
        stubReader(0);
        when(resources.uploadImage(any())).thenAnswer(call -> {
            assertThat(UserHolder.getUserId()).isEqualTo(7L);
            return RestResp.ok("/image/test.png");
        });
        mvc.perform(upload().header(HEADER, readerToken).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
            .andExpect(jsonPath("$.data").value("/image/test.png"));
        verify(resources, times(1)).uploadImage(any());
        verifyNoInteractions(companion, admins);
        assertThat(UserHolder.getUserId()).isNull();
    }

    @Test
    void captchaRemainsAnonymous() throws Exception {
        when(resources.getImgVerifyCode()).thenReturn(RestResp.ok(ImgVerifyCodeRespDto.builder()
            .sessionId("test-session").img("test-image").build()));
        mvc.perform(get(CAPTCHA).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
            .andExpect(jsonPath("$.data.sessionId").value("test-session"));
        verify(resources).getImgVerifyCode();
        verifyNoInteractions(companion, users, admins);
    }

    @Test
    void ordinaryAiRequestsStillUseReaderAuthentication() throws Exception {
        stubReader(0);
        when(companion.getHistory(null)).thenReturn(RestResp.ok(List.of()));
        mvc.perform(get("/api/front/ai/companion/chat/history").header(HEADER, readerToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        verify(companion).getHistory(null);
        verifyNoInteractions(admins, resources);
    }

    private MockHttpServletRequestBuilder upload() {
        // Only routing is under test; the mocked operation never writes these bytes to disk.
        return multipart(UPLOAD).file(new MockMultipartFile("file", "test.png", "image/png", new byte[] {1}));
    }

    private void expectRejected(MockHttpServletRequestBuilder request, ErrorCodeEnum code) throws Exception {
        // This project represents authentication errors in RestResp, not HTTP 401/403.
        mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    private void stubReader(int status) {
        when(users.getUserForAuth(7L)).thenReturn(UserInfoDto.builder().id(7L).status(status).build());
    }

    private void stubAdmin(int status) {
        SysUser admin = new SysUser();
        admin.setId(7L);
        admin.setStatus(status);
        when(admins.selectById(7L)).thenReturn(admin);
    }
}
