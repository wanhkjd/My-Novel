package novel.core.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import novel.controller.front.AiBookCompanionController;
import novel.controller.front.BookController;
import novel.core.auth.FrontAuthStrategy;
import novel.core.auth.UserHolder;
import novel.core.common.resp.RestResp;
import novel.core.config.WebConfig;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.JwtUtils;
import novel.dto.UserInfoDto;
import novel.manager.cache.UserInfoCacheManager;
import novel.service.BookCompanionService;
import novel.service.BookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC mappings, auth and async callbacks. No live accounts or business writes. */
class SseIdentityIsolationTest {
    private final BookCompanionService companion = mock(BookCompanionService.class);
    private final BookService books = mock(BookService.class);
    private final UserInfoCacheManager users = mock(UserInfoCacheManager.class);
    private final List<Sinks.Many<String>> streams = new ArrayList<>();
    private MockMvc mvc;
    private JwtUtils jwt;

    @BeforeEach
    void setup() throws Exception {
        UserHolder.clear();
        jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "sse-identity-fixture-only-secret-at-least-32-bytes");
        AuthInterceptor auth = new AuthInterceptor(Map.of("frontAuthStrategy", new FrontAuthStrategy(jwt, users)), new ObjectMapper());
        FlowLimitInterceptor flow = mock(FlowLimitInterceptor.class);
        when(flow.preHandle(any(), any(), any())).thenReturn(true);
        InterceptorRegistry registry = new InterceptorRegistry();
        new WebConfig(flow, auth, mock(FileInterceptor.class), new TokenParseInterceptor(jwt)).addInterceptors(registry);
        List<HandlerInterceptor> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        mvc = MockMvcBuilders.standaloneSetup(new AiBookCompanionController(companion), new BookController(books))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8), new MappingJackson2HttpMessageConverter())
                .addInterceptors(interceptors.toArray(HandlerInterceptor[]::new))
                .setAsyncRequestTimeout(5000).build();
    }

    @AfterEach
    void cleanup() {
        streams.forEach(sink -> sink.tryEmitComplete());
        UserHolder.clear();
    }

    @Test
    void sseHandoffClearsIdentityBeforeAnonymousChapterReusesThread() throws Exception {
        Sinks.Many<String> sink = stream("A", 7L, new CountDownLatch(1));
        MvcResult pending = begin("A", 7L);
        assertEmpty(); // Must be empty before completion, not merely after the next request.
        anonymousChapter();
        finishOnWorker(sink, "reply-A");
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
        assertEmpty();
    }

    @Test
    void twoReadersCompleteOnAnotherThreadWithoutLeakingIntoEachOther() throws Exception {
        Sinks.Many<String> a = stream("A", 7L, new CountDownLatch(1));
        Sinks.Many<String> b = stream("B", 8L, new CountDownLatch(1));
        MvcResult pendingA = begin("A", 7L);
        assertEmpty();
        MvcResult pendingB = begin("B", 8L);
        assertEmpty();
        // Reverse completion order while a different reader owns the worker thread.
        finishOnWorker(b, "reply-B");
        mvc.perform(asyncDispatch(pendingB)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("8:reply-B")));
        assertEmpty();
        finishOnWorker(a, "reply-A");
        mvc.perform(asyncDispatch(pendingA)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("7:reply-A")));
        assertEmpty();
        anonymousChapter();
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout", "disconnect"})
    void timedOutOrDisconnectedStreamDoesNotRetainServletIdentity(String termination) throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        Sinks.Many<String> sink = stream("A", 7L, cancelled);
        MvcResult pending = begin("A", 7L);
        assertEmpty();
        MockAsyncContext context = (MockAsyncContext) pending.getRequest().getAsyncContext();
        AsyncEvent event = new AsyncEvent(context, pending.getRequest(), pending.getResponse(),
                termination.equals("disconnect") ? new IOException("client disconnected") : null);
        for (AsyncListener listener : List.copyOf(context.getListeners())) {
            if (termination.equals("timeout")) listener.onTimeout(event); else listener.onError(event);
        }
        // Spring MVC 6.1 cancels on a failed send after the disconnect notification.
        if (termination.equals("disconnect")) sink.tryEmitNext("after-disconnect");
        assertThat(cancelled.await(3, TimeUnit.SECONDS)).isTrue();
        assertEmpty();
        anonymousChapter();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "invalid", "admin"})
    void anonymousChapterCannotInheritReaderOrAuthorEvenIfThreadWasDirty(String identity) throws Exception {
        UserHolder.setUserId(999L);
        UserHolder.setAuthorId(9990L);
        when(books.getBookContentAbout(42L)).thenAnswer(call -> {
            assertEmpty();
            return RestResp.ok();
        });
        var request = get("/api/front/book/content/42");
        if (!identity.equals("absent")) request.header(SystemConfigConsts.HTTP_AUTH_HEADER_NAME,
                identity.equals("admin") ? jwt.generateToken(7L, SystemConfigConsts.NOVEL_ADMIN_KEY) : "invalid-token");
        mvc.perform(request).andExpect(status().isOk());
        verify(books).getBookContentAbout(42L);
        assertEmpty();
    }

    @Test
    void synchronousHandlerFailureStillClearsBothIdentityFields() {
        stubReader(7L);
        when(companion.getHistory(null)).thenAnswer(call -> {
            assertThat(UserHolder.getUserId()).isEqualTo(7L);
            UserHolder.setAuthorId(70L);
            throw new IllegalStateException("handler failed");
        });
        assertThatThrownBy(() -> mvc.perform(get("/api/front/ai/companion/chat/history")
                .header(SystemConfigConsts.HTTP_AUTH_HEADER_NAME, token(7L))))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertEmpty();
    }

    private Sinks.Many<String> stream(String message, long id, CountDownLatch cancelled) {
        stubReader(id);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        streams.add(sink);
        when(companion.chatStream(message, null)).thenAnswer(call -> {
            Long captured = UserHolder.getUserId();
            assertThat(captured).isEqualTo(id);
            return sink.asFlux().map(text -> captured + ":" + text).doOnCancel(cancelled::countDown);
        });
        return sink;
    }

    private MvcResult begin(String message, long id) throws Exception {
        return mvc.perform(get("/api/front/ai/companion/chat/stream").param("message", message)
                .header(SystemConfigConsts.HTTP_AUTH_HEADER_NAME, token(id)).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
    }

    private void anonymousChapter() throws Exception {
        when(books.getBookContentAbout(42L)).thenAnswer(call -> {
            assertEmpty();
            return RestResp.ok();
        });
        mvc.perform(get("/api/front/book/content/42")).andExpect(status().isOk());
        assertEmpty();
    }

    private void finishOnWorker(Sinks.Many<String> sink, String text) throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                UserHolder.setUserId(999L);
                UserHolder.setAuthorId(9990L);
                try {
                    assertThat(sink.tryEmitNext(text)).isEqualTo(Sinks.EmitResult.OK);
                    assertThat(sink.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
                    assertThat(UserHolder.getUserId()).isEqualTo(999L);
                    assertThat(UserHolder.getAuthorId()).isEqualTo(9990L);
                } finally { UserHolder.clear(); }
            }).get(3, TimeUnit.SECONDS);
            worker.submit(this::assertEmpty).get(3, TimeUnit.SECONDS);
        }
    }

    private String token(long id) { return jwt.generateToken(id, SystemConfigConsts.NOVEL_FRONT_KEY); }

    private void stubReader(long id) {
        when(users.getUserForAuth(id)).thenReturn(UserInfoDto.builder().id(id).status(0).build());
    }

    private void assertEmpty() {
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(UserHolder.getAuthorId()).isNull();
    }
}
