package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import novel.controller.front.BookController;
import novel.core.auth.UserHolder;
import novel.core.config.JacksonConfig;
import novel.core.config.WebConfig;
import novel.core.interceptor.AuthInterceptor;
import novel.core.interceptor.FileInterceptor;
import novel.core.interceptor.FlowLimitInterceptor;
import novel.core.interceptor.TokenParseInterceptor;
import novel.core.util.JwtUtils;
import novel.dao.entity.BookComment;
import novel.dao.entity.UserInfo;
import novel.dao.mapper.BookCommentMapper;
import novel.manager.dao.UserDaoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/** Actual anonymous MVC route, service and Jackson configuration; fixture-only data. */
@ExtendWith(MockitoExtension.class)
class PublicCommentPrivacyTest {
    private static final String PHONE = "13800000000";
    private static final String URL = "/api/front/book/comment/newest_list";
    private static final LocalDateTime COMMENT_TIME = LocalDateTime.of(2026, 10, 11, 12, 30);
    @Mock private BookCommentMapper comments;
    @Mock private UserDaoManager users;
    @InjectMocks private BookServiceImpl service;
    private MockMvc mvc;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        UserHolder.clear();
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().jackson2ObjectMapperBuilderCustomizer().customize(builder);
        mapper = builder.build();
        FlowLimitInterceptor flow = mock(FlowLimitInterceptor.class);
        when(flow.preHandle(any(), any(), any())).thenReturn(true);
        InterceptorRegistry registry = new InterceptorRegistry();
        new WebConfig(flow, new AuthInterceptor(Map.of(), mapper), mock(FileInterceptor.class),
            new TokenParseInterceptor(mock(JwtUtils.class))).addInterceptors(registry);
        List<HandlerInterceptor> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        mvc = MockMvcBuilders.standaloneSetup(new BookController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .addInterceptors(interceptors.toArray(HandlerInterceptor[]::new)).build();
    }

    @AfterEach
    void clearIdentity() { UserHolder.clear(); }

    @Test
    void anonymousJsonDoesNotExposeFullLoginPhone() throws Exception {
        // Passes even before this fix: the old DTO serializer already masks a normal phone.
        // Keep this baseline so the report does not mistake raw DTO contents for the HTTP response.
        stubSingleUser(PHONE, "雨夜读书人");
        String json = anonymousJson();
        assertThat(json).doesNotContain(PHONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"雨夜读书人", "阿明", "reader", "星河🌙", "  山间客  "})
    void publicDisplayUsesNicknameWithoutSubstringMasking(String nickname) throws Exception {
        stubSingleUser(PHONE, nickname);
        assertPublicName(nickname.strip());
        // Safe at the service boundary too, without relying on any Jackson annotation.
        assertThat(service.listNewestComments(1L).getData().getComments().getFirst().getCommentUser())
            .isEqualTo(nickname.strip()).doesNotContain(PHONE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", PHONE, "  " + PHONE + "  ", "联系" + PHONE, "+86" + PHONE,
        "13900000000"})
    void unsafeOrMissingNicknameUsesNonSensitiveFallback(String nickname) throws Exception {
        stubSingleUser(PHONE, nickname);
        assertPublicName("书友");
        assertThat(service.listNewestComments(1L).getData().getComments().getFirst().getCommentUser())
            .isEqualTo("书友");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "u", "legacy", "旧账号"})
    void shortOrMissingLegacyLoginDoesNotBreakPublicComment(String username) throws Exception {
        stubSingleUser(username, "山间客");
        assertPublicName("山间客");
    }

    @Test
    void legacyNicknameEqualToNonPhoneLoginDoesNotExposeLogin() throws Exception {
        stubSingleUser("legacy-login", "legacy-login");
        assertPublicName("书友");
    }

    @Test
    void missingUserKeepsCommentWithFallbackAndNoAvatar() throws Exception {
        stubComments(List.of(comment(100L, 20L)));
        when(users.listUsers(List.of(20L))).thenReturn(List.of());
        JsonNode body = mapper.readTree(anonymousJson());
        JsonNode item = body.at("/data/comments/0");
        assertThat(item.get("commentUser").asText()).isEqualTo("书友");
        assertThat(item.get("commentUserPhoto").isNull()).isTrue();
        assertThat(item.get("commentContent").asText()).isEqualTo("测试书评");
    }

    @Test
    void preservesPublicFieldsAndLongIdSerialization() throws Exception {
        stubSingleUser(PHONE, "山间客");
        JsonNode item = mapper.readTree(anonymousJson()).at("/data/comments/0");
        assertThat(item.get("id").isTextual()).isTrue();
        assertThat(item.get("id").asText()).isEqualTo("100");
        assertThat(item.get("commentUserId").asText()).isEqualTo("20");
        assertThat(item.get("commentUserPhoto").asText()).isEqualTo("/fixture-avatar.png");
        assertThat(item.get("commentTime").asText()).isEqualTo("2026-10-11 12:30:00");
        assertThat(item.get("commentContent").asText()).isEqualTo("测试书评");
        assertThat(item.has("username")).isFalse();
        assertThat(item.has("password")).isFalse();
    }

    @Test
    void emptyCommentsDoNotQueryUserAccounts() throws Exception {
        when(comments.selectCount(any())).thenReturn(0L);
        JsonNode body = mapper.readTree(anonymousJson());
        assertThat(body.at("/data/commentTotal").asText()).isEqualTo("0");
        assertThat(body.at("/data/comments").isArray()).isTrue();
        assertThat(body.at("/data/comments").size()).isZero();
        verify(comments, never()).selectList(any());
        verifyNoInteractions(users);
    }

    @Test
    void mapsEachCommentToCorrectUserAndRetainsListOrder() throws Exception {
        stubComments(List.of(comment(101L, 30L), comment(100L, 20L)));
        when(users.listUsers(List.of(30L, 20L))).thenReturn(List.of(
            user(20L, PHONE, "山间客"), user(30L, "13900000000", "雨夜读书人")));
        JsonNode list = mapper.readTree(anonymousJson()).at("/data/comments");
        assertThat(list.get(0).get("id").asText()).isEqualTo("101");
        assertThat(list.get(0).get("commentUser").asText()).isEqualTo("雨夜读书人");
        assertThat(list.get(1).get("id").asText()).isEqualTo("100");
        assertThat(list.get(1).get("commentUser").asText()).isEqualTo("山间客");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preservesBookAndAuditFiltersAndNewestFiveOrdering() throws Exception {
        stubSingleUser(PHONE, "山间客");
        anonymousJson();
        ArgumentCaptor<QueryWrapper<BookComment>> countQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<BookComment>> listQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(comments).selectCount(countQuery.capture());
        verify(comments).selectList(listQuery.capture());
        for (QueryWrapper<BookComment> query : List.of(countQuery.getValue(), listQuery.getValue())) {
            assertThat(query.getSqlSegment()).contains("book_id =", "audit_status =");
            assertThat(query.getParamNameValuePairs().values()).contains(1L, 1);
        }
        assertThat(listQuery.getValue().getSqlSegment().toLowerCase()).contains("order by create_time desc", "limit 5");
    }

    private void assertPublicName(String expected) throws Exception {
        String json = anonymousJson();
        assertThat(mapper.readTree(json).at("/data/comments/0/commentUser").asText()).isEqualTo(expected);
        assertThat(json).doesNotContain(PHONE);
    }

    private String anonymousJson() throws Exception {
        String json = mvc.perform(get(URL).param("bookId", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mapper.readTree(json).get("code").asText()).isEqualTo("00000");
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(UserHolder.getAuthorId()).isNull();
        return json;
    }

    private void stubSingleUser(String username, String nickname) {
        stubComments(List.of(comment(100L, 20L)));
        when(users.listUsers(List.of(20L))).thenReturn(List.of(user(20L, username, nickname)));
    }

    private void stubComments(List<BookComment> records) {
        when(comments.selectCount(any())).thenReturn((long) records.size());
        when(comments.selectList(any())).thenReturn(records);
    }

    private UserInfo user(Long id, String username, String nickname) {
        UserInfo user = new UserInfo();
        user.setId(id);
        user.setUsername(username);
        user.setNickName(nickname);
        user.setUserPhoto("/fixture-avatar.png");
        return user;
    }

    private BookComment comment(Long id, Long userId) {
        BookComment comment = new BookComment();
        comment.setId(id);
        comment.setBookId(1L);
        comment.setUserId(userId);
        comment.setCommentContent("测试书评");
        comment.setCreateTime(COMMENT_TIME);
        return comment;
    }
}
