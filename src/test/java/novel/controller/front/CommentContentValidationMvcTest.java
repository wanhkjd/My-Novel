package novel.controller.front;

import com.fasterxml.jackson.databind.ObjectMapper;
import novel.core.auth.UserHolder;
import novel.core.common.exception.CommonExceptionHandler;
import novel.core.common.resp.RestResp;
import novel.dto.req.UserCommentReqDto;
import novel.service.BookService;
import novel.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.FormContentFilter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Real MVC binding/Bean Validation/error mapping; no server, model, Redis or business DB. */
class CommentContentValidationMvcTest {
    private static final String CREATE = "/api/front/user/comment";
    private static final String EDIT = CREATE + "/101";
    private final ObjectMapper json = new ObjectMapper();
    private final BookService books = mock(BookService.class);
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserHolder.setUserId(7L);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new UserController(mock(UserService.class), books))
            .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
            .alwaysExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .setValidator(validator)
            .setControllerAdvice(new CommonExceptionHandler())
            .addFilters(new FormContentFilter())
            .build();
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        validator.close();
    }

    static Stream<String> invalidContent() {
        return Stream.of(null, "", " ".repeat(10), "\t\n".repeat(5), "　".repeat(10),
            "文", "文".repeat(9), "文".repeat(513), "📚".repeat(4), "📚".repeat(257));
    }

    static Stream<String> validContent() {
        return Stream.of("文".repeat(10), "文".repeat(512), "         文", "  保留前后所有空格  ",
            "📚".repeat(5), "📚".repeat(256), "支持符号 + & = % / 和换行\n");
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    void createRejectsInvalidContentBeforeCallingService(String content) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookId", 20L);
        payload.put("commentContent", content);
        mvc.perform(post(CREATE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    void editRejectsMissingOrInvalidFormContentBeforeCallingService(String content) throws Exception {
        String form = content == null ? "" : "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
        mvc.perform(put(EDIT).contentType(MediaType.APPLICATION_FORM_URLENCODED).characterEncoding("UTF-8").content(form))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }

    @ParameterizedTest
    @MethodSource("validContent")
    void createAcceptsValidContentUnchangedAndUsesAuthenticatedUser(String content) throws Exception {
        when(books.saveComment(any())).thenReturn(RestResp.ok());
        mvc.perform(post(CREATE).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("bookId", 20, "userId", 999, "commentContent", content))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        ArgumentCaptor<UserCommentReqDto> dto = ArgumentCaptor.forClass(UserCommentReqDto.class);
        verify(books).saveComment(dto.capture());
        assertThat(dto.getValue().getBookId()).isEqualTo(20L);
        assertThat(dto.getValue().getUserId()).isEqualTo(7L);
        assertThat(dto.getValue().getCommentContent()).isEqualTo(content);
    }

    @ParameterizedTest
    @MethodSource("validContent")
    void editAcceptsExistingUrlEncodedProtocolWithoutTrimming(String content) throws Exception {
        when(books.updateComment(anyLong(), anyLong(), anyString())).thenReturn(RestResp.ok());
        String form = "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8)
            + "&userId=999&auditStatus=1&bookId=999";
        mvc.perform(put(EDIT).contentType(MediaType.APPLICATION_FORM_URLENCODED).characterEncoding("UTF-8").content(form))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        verify(books).updateComment(7L, 101L, content);
        verifyNoMoreInteractions(books);
    }

    @Test
    void createRequiresContentEvenWhenFieldIsMissing() throws Exception {
        mvc.perform(post(CREATE).contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":20}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }

    @Test
    void createStillRequiresBookId() throws Exception {
        mvc.perform(post(CREATE).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("commentContent", "文".repeat(10)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }

    @Test
    void queryParameterCompatibilityIsPreserved() throws Exception {
        String content = "查询参数也保留原样提交";
        when(books.updateComment(7L, 101L, content)).thenReturn(RestResp.ok());
        mvc.perform(put(EDIT).queryParam("content", content))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        verify(books).updateComment(7L, 101L, content);
    }

    @Test
    void wrongFieldCannotSilentlyResetAuditStatus() throws Exception {
        mvc.perform(put(EDIT).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("commentContent=" + URLEncoder.encode("文".repeat(10), StandardCharsets.UTF_8)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }

    @Test
    void jsonEditBodyDoesNotMasqueradeAsTheRequiredFormField() throws Exception {
        mvc.perform(put(EDIT).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("content", "文".repeat(10)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0400"));
        verifyNoInteractions(books);
    }
}
