package novel.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import novel.controller.author.AuthorController;
import novel.core.common.resp.RestResp;
import novel.core.config.WebConfig;
import novel.core.constant.CacheConsts;
import novel.core.constant.SystemConfigConsts;
import novel.core.interceptor.AuthInterceptor;
import novel.core.interceptor.FileInterceptor;
import novel.core.interceptor.FlowLimitInterceptor;
import novel.core.interceptor.TokenParseInterceptor;
import novel.core.util.JwtUtils;
import novel.dao.entity.AuthorInfo;
import novel.dao.mapper.AuthorInfoMapper;
import novel.dto.AuthorInfoDto;
import novel.dto.UserInfoDto;
import novel.dto.req.AdminStatusReqDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.UserInfoCacheManager;
import novel.service.BookService;
import novel.service.impl.AdminServiceImpl;
import novel.service.impl.AuthorServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/** Real MVC routes, JWT, auth, author service and Spring cache proxy; no external writes. */
@ExtendWith(MockitoExtension.class)
class AuthorBanAuthTest {
    private static final String HEADER = SystemConfigConsts.HTTP_AUTH_HEADER_NAME;
    private static final String REGISTER_BODY = """
        {"userId":999,"penName":"fixture-author","telPhone":"13800000000",
         "chatAccount":"fixture","email":"fixture@example.invalid","workDirection":0}
        """;

    @Mock private UserInfoCacheManager users;
    @Mock private AuthorInfoMapper authorMapper;
    @Mock private BookService books;
    @InjectMocks private AdminServiceImpl admin;
    private AnnotationConfigApplicationContext context;
    private AuthorInfoCacheManager authors;
    private Cache cache;
    private AuthorInfo storedAuthor;
    private MockMvc mvc;
    private String token;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        UserHolder.clear();
        ConcurrentMapCacheManager backing = new ConcurrentMapCacheManager(CacheConsts.AUTHOR_INFO_CACHE_NAME);
        cache = backing.getCache(CacheConsts.AUTHOR_INFO_CACHE_NAME);
        context = new AnnotationConfigApplicationContext();
        context.register(CacheConfig.class);
        context.registerBean(CacheConsts.REDIS_CACHE_MANAGER, ConcurrentMapCacheManager.class, () -> backing);
        context.registerBean(AuthorInfoCacheManager.class, () -> new AuthorInfoCacheManager(authorMapper));
        context.refresh();
        authors = context.getBean(AuthorInfoCacheManager.class);
        ReflectionTestUtils.setField(admin, "authorInfoCacheManager", authors);

        JwtUtils jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "author-ban-test-fixture-secret-at-least-32-bytes");
        token = jwt.generateToken(10L, SystemConfigConsts.NOVEL_FRONT_KEY);
        adminToken = jwt.generateToken(10L, SystemConfigConsts.NOVEL_ADMIN_KEY);
        AuthInterceptor auth = new AuthInterceptor(Map.of(
            "authorAuthStrategy", new AuthorAuthStrategy(jwt, users, authors)), new ObjectMapper());
        FlowLimitInterceptor flow = mock(FlowLimitInterceptor.class);
        when(flow.preHandle(any(), any(), any())).thenReturn(true);
        InterceptorRegistry registry = new InterceptorRegistry();
        new WebConfig(flow, auth, mock(FileInterceptor.class), new TokenParseInterceptor(jwt))
            .addInterceptors(registry);
        List<HandlerInterceptor> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        mvc = MockMvcBuilders.standaloneSetup(
                new AuthorController(new AuthorServiceImpl(authors, authorMapper), books))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .addInterceptors(interceptors.toArray(HandlerInterceptor[]::new)).build();
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-jwt"})
    void missingOrInvalidTokenIsRejected(String badToken) throws Exception {
        reject(get("/api/author/books").header(HEADER, badToken), "A0230");
        verifyNoInteractions(users, authorMapper, books);
    }

    @Test
    void adminTokenCannotActAsAuthorEvenWithSameNumericId() throws Exception {
        reject(get("/api/author/books").header(HEADER, adminToken), "A0230");
        verifyNoInteractions(users, authorMapper, books);
    }

    @Test
    void normalAuthorGetsBothIdentitiesOnlyDuringBusinessCall() throws Exception {
        reader(0);
        author(0);
        expectListWithIdentity();
        listSuccessfully();
        verify(books).listAuthorBooks(any());
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/author/books", "GET,/api/author/info", "POST,/api/author/book",
        "POST,/api/author/book/chapter/30", "GET,/api/author/book/chapter/30",
        "PUT,/api/author/book/chapter/30", "DELETE,/api/author/book/chapter/30",
        "GET,/api/author/book/chapters/30"})
    void bannedAuthorCannotReachProtectedReadOrWriteRoutes(String method, String uri) throws Exception {
        reader(0);
        author(1);
        // Reused thread identity must be cleared even when author auth fails after reader auth.
        UserHolder.setUserId(999L);
        UserHolder.setAuthorId(888L);
        reject(request(HttpMethod.valueOf(method), uri).header(HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content("{}"), "A0302");
        verifyNoInteractions(books);
        verify(authorMapper, never()).selectById(any());
        verify(authorMapper, never()).insert(any(AuthorInfo.class));
    }

    @Test
    void missingAuthorIsUnauthorized() throws Exception {
        reader(0);
        reject(get("/api/author/books").header(HEADER, token), "A0301");
        verifyNoInteractions(books);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 2})
    void unknownStatusFailsClosed(Integer invalidStatus) throws Exception {
        reader(0);
        author(invalidStatus);
        reject(get("/api/author/books").header(HEADER, token), "A0301");
        verifyNoInteractions(books);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/author/status", "POST,/api/author/register", "GET,/api/author/books"})
    void bannedReaderCannotUseAuthorExclusions(String method, String uri) throws Exception {
        reader(1);
        reject(request(HttpMethod.valueOf(method), uri).header(HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY), "A0202");
        verifyNoInteractions(authorMapper, books);
    }

    @Test
    void unregisteredReaderCanQueryStatus() throws Exception {
        reader(0);
        mvc.perform(get("/api/author/status").header(HEADER, token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
            .andExpect(jsonPath("$.data").doesNotExist());
        assertIdentityCleared();
    }

    @Test
    void unregisteredReaderCanRegisterUsingAuthenticatedUserId() throws Exception {
        reader(0);
        when(authorMapper.insert(any(AuthorInfo.class))).thenAnswer(call -> {
            AuthorInfo inserted = call.getArgument(0);
            assertThat(inserted.getUserId()).isEqualTo(10L);
            assertThat(UserHolder.getAuthorId()).isNull();
            return 1;
        });
        mvc.perform(post("/api/author/register").header(HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        verify(authorMapper).insert(any(AuthorInfo.class));
        assertIdentityCleared();
    }

    @Test
    void bannedAuthorCanQueryCurrentStatusDespiteStaleNormalCache() throws Exception {
        reader(0);
        author(1);
        cache.put(10L, cachedAuthor(0));
        mvc.perform(get("/api/author/status").header(HEADER, token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
            .andExpect(jsonPath("$.data").value(1));
        assertIdentityCleared();
    }

    @Test
    void reregisteringExistingBannedAuthorDoesNotRestorePrivileges() throws Exception {
        reader(0);
        author(1);
        mvc.perform(post("/api/author/register").header(HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        verify(authorMapper, never()).insert(any(AuthorInfo.class));
        verify(authorMapper, never()).updateById(any(AuthorInfo.class));
        reject(get("/api/author/books").header(HEADER, token), "A0302");
    }

    @Test
    void banAndUnbanAffectExistingTokenEvenWhenOldDisplayCacheIsRefilled() throws Exception {
        reader(0);
        author(0);
        expectListWithIdentity();
        when(authorMapper.updateById(any(AuthorInfo.class))).thenAnswer(call -> {
            AuthorInfo update = call.getArgument(0);
            assertThat(update.getId()).isEqualTo(20L);
            storedAuthor.setStatus(update.getStatus());
            return 1;
        });
        AuthorInfoDto normalCache = authors.getAuthor(10L);
        listSuccessfully();

        changeAuthorStatus(1);
        assertThat(cache.get(10L)).isNull();
        // Simulate a concurrent pre-ban read refilling the cache after eviction.
        cache.put(10L, normalCache);
        reject(get("/api/author/books").header(HEADER, token), "A0302");
        assertThat(authors.getAuthor(10L).getStatus()).isZero();

        changeAuthorStatus(0);
        assertThat(cache.get(10L)).isNull();
        cache.put(10L, cachedAuthor(1));
        listSuccessfully();
        assertThat(authors.getAuthor(10L).getStatus()).isEqualTo(1);
        verify(books, times(2)).listAuthorBooks(any());
    }

    @Test
    void deletedAuthorCannotUseWarmCache() throws Exception {
        reader(0);
        author(0);
        authors.getAuthor(10L);
        storedAuthor = null;
        reject(get("/api/author/books").header(HEADER, token), "A0301");
        verifyNoInteractions(books);
    }

    private void reader(int status) {
        when(users.getUserForAuth(10L)).thenReturn(UserInfoDto.builder().id(10L).status(status).build());
    }

    private void author(Integer status) {
        storedAuthor = new AuthorInfo();
        storedAuthor.setId(20L);
        storedAuthor.setUserId(10L);
        storedAuthor.setPenName("fixture-author");
        storedAuthor.setStatus(status);
        when(authorMapper.selectOne(any())).thenAnswer(call -> storedAuthor);
    }

    private AuthorInfoDto cachedAuthor(int status) {
        return AuthorInfoDto.builder().id(20L).penName("fixture-author").status(status).build();
    }

    private void changeAuthorStatus(int status) {
        AdminStatusReqDto dto = new AdminStatusReqDto();
        dto.setStatus(status);
        admin.updateAuthorStatus(20L, dto);
    }

    private void expectListWithIdentity() {
        when(books.listAuthorBooks(any())).thenAnswer(call -> {
            assertThat(UserHolder.getUserId()).isEqualTo(10L);
            assertThat(UserHolder.getAuthorId()).isEqualTo(20L);
            return RestResp.ok();
        });
    }

    private void listSuccessfully() throws Exception {
        mvc.perform(get("/api/author/books").header(HEADER, token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        assertIdentityCleared();
    }

    private void reject(MockHttpServletRequestBuilder request, String code) throws Exception {
        // Application contract: HTTP 200 with RestResp.code, not HTTP 401/403.
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        assertIdentityCleared();
    }

    private void assertIdentityCleared() {
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(UserHolder.getAuthorId()).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheConfig { }
}
