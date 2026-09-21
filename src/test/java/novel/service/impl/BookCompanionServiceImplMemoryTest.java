package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import novel.core.ai.config.AiProperties;
import novel.core.ai.dto.ChatHistoryMsgDto;
import novel.core.ai.tools.BookCompanionTools;
import novel.core.ai.vector.BookVectorSyncService;
import novel.core.ai.vector.EsVectorStoreService;
import novel.core.auth.UserHolder;
import novel.core.common.resp.RestResp;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class BookCompanionServiceImplMemoryTest {

    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private AiProperties aiProperties;
    @Mock private BookCompanionTools bookCompanionTools;
    @Mock private EsVectorStoreService esVectorStoreService;
    @Mock private BookVectorSyncService bookVectorSyncService;
    @Mock private UserBookshelfMapper userBookshelfMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private ChatMemory chatMemory;

    @InjectMocks private BookCompanionServiceImpl service;

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void buildMemoryKey_keysByUserIdOnly_ignoringClientConversationId() {
        UserHolder.setUserId(1001L);
        // 跨端共享：无论前端传什么 conversationId，都落到同一份按账号命名的 key
        assertThat(service.buildMemoryKey("conv1")).isEqualTo("aiChatMemory::1001::default");
        assertThat(service.buildMemoryKey("another-uuid")).isEqualTo("aiChatMemory::1001::default");
        assertThat(service.buildMemoryKey(null)).isEqualTo("aiChatMemory::1001::default");
    }

    @Test
    void clearMemory_clearsUserNamespacedKey() {
        UserHolder.setUserId(1001L);
        RestResp<Void> resp = service.clearMemory("conv1");
        assertThat(resp.getCode()).isEqualTo("00000");
        verify(chatMemory).clear("aiChatMemory::1001::default");
    }

    @Test
    void clearMemory_doesNothing_whenNotLoggedIn() {
        service.clearMemory("conv1");
        verify(chatMemory, never()).clear(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getHistory_mapsMessagesToDto() {
        UserHolder.setUserId(1001L);
        List<Message> stored = List.of(new UserMessage("你好"), new AssistantMessage("少侠好"));
        when(chatMemory.get("aiChatMemory::1001::default", 20)).thenReturn(stored);

        RestResp<List<ChatHistoryMsgDto>> resp = service.getHistory("conv1");

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData().get(0).getRole()).isEqualTo("user");
        assertThat(resp.getData().get(0).getContent()).isEqualTo("你好");
        assertThat(resp.getData().get(1).getRole()).isEqualTo("assistant");
        assertThat(resp.getData().get(1).getContent()).isEqualTo("少侠好");
    }

    @Test
    void getHistory_returnsEmpty_whenNotLoggedIn() {
        RestResp<List<ChatHistoryMsgDto>> resp = service.getHistory("conv1");
        assertThat(resp.getData()).isEmpty();
    }
}
