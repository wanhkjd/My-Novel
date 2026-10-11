package novel.service.impl;

import novel.core.ai.config.AiProperties;
import novel.core.ai.memory.InMemoryConversationMemory;
import novel.core.ai.tools.BookCompanionTools;
import novel.core.ai.vector.BookVectorSyncService;
import novel.core.ai.vector.EsVectorStoreService;
import novel.core.auth.UserHolder;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookCompanionClearRaceTest {
    private static final String KEY = "aiChatMemory::7::default";
    private final ChatModel model = mock(ChatModel.class);
    private final InMemoryConversationMemory memory = spy(new InMemoryConversationMemory());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch finish = new CountDownLatch(1);
    private BookCompanionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookCompanionServiceImpl(ChatClient.builder(model), new AiProperties(),
                mock(BookCompanionTools.class), mock(EsVectorStoreService.class), mock(BookVectorSyncService.class),
                mock(UserBookshelfMapper.class), mock(BookInfoMapper.class), memory);
        service.init();
        UserHolder.setUserId(7L);
    }

    @AfterEach
    void tearDown() throws Exception {
        finish.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        UserHolder.clear();
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(text)));
    }

    private void delayedModel() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release model");
            return response("旧回答📚");
        });
    }

    private Future<String> oldRequest(boolean stream) {
        return executor.submit(() -> {
            UserHolder.setUserId(7L);
            try {
                return stream ? String.join("", service.chatStream("旧问题", "tab-A").collectList().block(Duration.ofSeconds(5)))
                        : service.chat("旧问题", "tab-A").getData();
            } finally { UserHolder.clear(); }
        });
    }

    @Test
    void syncResponseCannotRestoreHistoryClearedInAnotherTab() throws Exception {
        delayedModel();
        Future<String> old = oldRequest(false);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        service.clearMemory("tab-B");
        finish.countDown();
        assertThat(old.get(5, TimeUnit.SECONDS)).isEqualTo("旧回答📚");
        assertThat(service.getHistory(null).getData()).isEmpty();
        verify(memory, times(1)).begin(KEY);
    }

    @Test
    void streamingResponseCannotRestoreHistoryClearedInAnotherTab() throws Exception {
        delayedModel();
        Future<String> old = oldRequest(true);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        service.clearMemory("tab-B");
        finish.countDown();
        assertThat(old.get(5, TimeUnit.SECONDS)).isEqualTo("旧回答📚");
        assertThat(service.getHistory(null).getData()).isEmpty();
        verify(memory, times(1)).begin(KEY);
    }

    @Test
    void oldCompletionCannotOverwriteNewTurnAfterClear() throws Exception {
        delayedModel();
        Future<String> old = oldRequest(false);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        service.clearMemory("tab-B");
        doReturn(response("新回答")).when(model).call(any(Prompt.class));
        assertThat(service.chat("新问题", "tab-C").getData()).isEqualTo("新回答");
        finish.countDown();
        old.get(5, TimeUnit.SECONDS);
        assertThat(memory.get(KEY, 20)).extracting(Message::getContent).containsExactly("新问题", "新回答");
    }

    @Test
    void completedTurnIsRemovedWhenClearWinsAfterCommit() {
        when(model.call(any(Prompt.class))).thenReturn(response("回答"));
        service.chat("问题", null);
        assertThat(memory.get(KEY, 20)).hasSize(2);
        service.clearMemory(null);
        assertThat(memory.get(KEY, 20)).isEmpty();
    }

    @Test
    void cancellingChunkDeliveryDoesNotPersistTheTurn() {
        when(model.call(any(Prompt.class))).thenReturn(response("很多内容尚未全部送出📚"));
        assertThat(service.chatStream("问题", null).take(1).collectList().block()).hasSize(1);
        assertThat(memory.get(KEY, 20)).isEmpty();
        verify(memory, never()).appendTurn(any(), any(), any());
    }

    @Test
    void failedModelDoesNotPersistAnOrphanQuestion() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("failed"));
        assertThatThrownBy(() -> service.chat("问题", null)).isInstanceOf(IllegalStateException.class);
        assertThat(memory.get(KEY, 20)).isEmpty();
        verify(memory, never()).appendTurn(any(), any(), any());
    }

    @Test
    void emptyResponseDoesNotPersistAnOrphanQuestion() {
        when(model.call(any(Prompt.class))).thenReturn(response(""));
        service.chat("问题", null);
        service.chatStream("问题", null).collectList().block();
        assertThat(memory.get(KEY, 20)).isEmpty();
    }

    @Test
    void promptUsesSnapshotHistoryButNewGenerationDoesNotSeeClearedMessages() {
        memory.appendTurn(memory.begin(KEY), "以前的问题", "以前的回答");
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).extracting(Message::getContent).contains("以前的问题", "以前的回答", "现在的问题");
            return response("现在的回答");
        });
        service.chat("现在的问题", null);
        service.clearMemory(null);
        doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).extracting(Message::getContent).doesNotContain("以前的问题", "以前的回答", "现在的问题", "现在的回答").contains("清空后的问题");
            return response("清空后的回答");
        }).when(model).call(any(Prompt.class));
        service.chat("清空后的问题", null);
    }

    @Test
    void streamingCapturesUserBeforeSubscriptionAndOtherUserClearDoesNotInvalidateIt() {
        when(model.call(any(Prompt.class))).thenReturn(response("回答"));
        Flux<String> pending = service.chatStream("问题", null);
        UserHolder.setUserId(8L);
        service.clearMemory(null);
        pending.collectList().block();
        assertThat(memory.get(KEY, 20)).extracting(Message::getContent).containsExactly("问题", "回答");
        assertThat(memory.get("aiChatMemory::8::default", 20)).isEmpty();
    }

    @Test
    void blankQuestionDoesNotStartMemoryOrModel() {
        service.chat(" ", null);
        service.chatStream(" ", null).collectList().block();
        verify(model, never()).call(any(Prompt.class));
        verify(memory, never()).begin(any());
    }
}
