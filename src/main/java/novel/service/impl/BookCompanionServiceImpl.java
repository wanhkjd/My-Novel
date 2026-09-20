package novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.ai.config.AiProperties;
import novel.core.ai.dto.ChatHistoryMsgDto;
import novel.core.ai.dto.EsVectorBookDto;
import novel.core.ai.dto.SimilarBookRecommendDto;
import novel.core.ai.tools.BookCompanionTools;
import novel.core.ai.vector.BookVectorSyncService;
import novel.core.ai.vector.EsVectorStoreService;
import novel.core.auth.UserHolder;
import novel.core.common.resp.RestResp;
import novel.dao.entity.BookInfo;
import novel.dao.entity.UserBookshelf;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import novel.service.BookCompanionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智能书童服务实现类
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookCompanionServiceImpl implements BookCompanionService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiProperties aiProperties;
    private final BookCompanionTools bookCompanionTools;
    private final EsVectorStoreService esVectorStoreService;
    private final BookVectorSyncService bookVectorSyncService;
    private final UserBookshelfMapper userBookshelfMapper;
    private final BookInfoMapper bookInfoMapper;
    private final ChatMemory chatMemory;

    /** 记忆窗口条数 */
    static final int MEMORY_WINDOW = 20;
    /** 记忆业务 key 前缀 */
    private static final String MEMORY_KEY_PREFIX = "aiChatMemory::";

    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = chatClientBuilder
                .defaultSystem(aiProperties.getSystemPrompt())
                .defaultFunctions("searchBooksFunction", "getBookRankFunction", "addToBookshelfFunction", "recommendBooksFunction")
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
        log.info("[智能书童] 初始化完成，已加载默认人设 Prompt、4 大业务 Function Tools 与对话记忆 Advisor");
    }

    @Override
    public Flux<String> chatStream(String message, String conversationId) {
        if (message == null || message.isBlank()) {
            return Flux.just("少侠，您有什么想问的？可以让我找书、查榜单或者把心仪小说加入书架哦！");
        }
        String memoryKey = buildMemoryKey(conversationId);
        return Flux.defer(() -> {
            try {
                String fullResponse = chatClient.prompt()
                        .user(message)
                        .advisors(a -> a
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, memoryKey)
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW))
                        .call()
                        .content();
                if (fullResponse == null || fullResponse.isBlank()) {
                    return Flux.just("少侠，小墨未能找到相关内容，请换个问题试试吧。");
                }
                // 按 Unicode 码点（支持 Emoji 4 字节代理对）安全流式分块，彻底避免 Emoji 乱码 ??
                return Flux.create(sink -> {
                    int[] codePoints = fullResponse.codePoints().toArray();
                    int len = codePoints.length;
                    int chunkSize = 2;
                    for (int i = 0; i < len; i += chunkSize) {
                        int end = Math.min(i + chunkSize, len);
                        sink.next(new String(codePoints, i, end - i));
                    }
                    sink.complete();
                });
            } catch (Exception e) {
                String errorDetail = e.getMessage();
                if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException we) {
                    errorDetail = "Status: " + we.getStatusCode() + ", ErrorBody: " + we.getResponseBodyAsString();
                }
                log.error("[智能书童] 流式对话调用大模型异常: {}", errorDetail, e);
                return Flux.just("\n\n*(书童小墨在翻阅书库时偶遇神识波动，请检查大模型配置或稍后再试)*");
            }
        });
    }

    @Override
    public RestResp<String> chat(String message, String conversationId) {
        if (message == null || message.isBlank()) {
            return RestResp.ok("少侠，您好！我是书童小墨，请问今天想看什么类型的小说？");
        }
        String response = chatClient.prompt()
                .user(message)
                .advisors(a -> a
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, buildMemoryKey(conversationId))
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW))
                .call()
                .content();
        return RestResp.ok(response);
    }

    @Override
    public RestResp<Void> clearMemory(String conversationId) {
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            chatMemory.clear(buildMemoryKey(conversationId));
        }
        return RestResp.ok();
    }

    @Override
    public RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return RestResp.ok(Collections.emptyList());
        }
        List<Message> messages = chatMemory.get(buildMemoryKey(conversationId), MEMORY_WINDOW);
        List<ChatHistoryMsgDto> dtos = messages.stream()
                .map(m -> ChatHistoryMsgDto.builder()
                        .role(m.getMessageType() == MessageType.USER ? "user" : "assistant")
                        .content(m.getContent())
                        .build())
                .toList();
        return RestResp.ok(dtos);
    }

    /**
     * 由登录用户 ID 与会话 ID 拼接记忆业务 key，userId 由后端强制注入以隔离用户。
     * 即使前端伪造他人 conversationId，落点仍在本人 userId 命名空间内。
     *
     * @param conversationId 前端会话 ID（可空，为空回退 default）
     * @return 形如 aiChatMemory::{userId}::{conversationId} 的业务 key
     */
    String buildMemoryKey(String conversationId) {
        Long userId = UserHolder.getUserId();
        String convId = (conversationId == null || conversationId.isBlank()) ? "default" : conversationId;
        return MEMORY_KEY_PREFIX + userId + "::" + convId;
    }

    @Override
    public RestResp<List<SimilarBookRecommendDto>> recommendSimilarBooks(Integer count) {
        int safeCount = (count == null || count <= 0) ? 4 : Math.min(count, 10);
        Long userId = UserHolder.getUserId();

        List<EsVectorBookDto> similarDocs = Collections.emptyList();
        List<Long> readBookIds = new ArrayList<>();

        if (userId != null) {
            // 查询用户近期加入书架的书籍
            LambdaQueryWrapper<UserBookshelf> qw = new LambdaQueryWrapper<>();
            qw.eq(UserBookshelf::getUserId, userId)
                    .orderByDesc(UserBookshelf::getUpdateTime)
                    .last("LIMIT 10");
            List<UserBookshelf> shelfList = userBookshelfMapper.selectList(qw);

            if (shelfList != null && !shelfList.isEmpty()) {
                readBookIds = shelfList.stream().map(UserBookshelf::getBookId).toList();
                List<List<Float>> vectors = new ArrayList<>();

                for (Long bookId : readBookIds) {
                    EsVectorBookDto doc = esVectorStoreService.getVectorByBookId(bookId);
                    if (doc != null && doc.getEmbedding() != null && !doc.getEmbedding().isEmpty()) {
                        vectors.add(doc.getEmbedding());
                    }
                }

                if (!vectors.isEmpty()) {
                    int dims = vectors.get(0).size();
                    List<Float> centroid = new ArrayList<>(Collections.nCopies(dims, 0.0f));
                    for (List<Float> v : vectors) {
                        for (int d = 0; d < dims; d++) {
                            centroid.set(d, centroid.get(d) + v.get(d));
                        }
                    }
                    for (int d = 0; d < dims; d++) {
                        centroid.set(d, centroid.get(d) / vectors.size());
                    }

                    // 检索排除已读书目的相似书籍
                    similarDocs = esVectorStoreService.knnSearch(centroid, safeCount, readBookIds);
                }
            }
        }

        // 如果用户未登录或书架为空或无向量，降级查询全站高分点击榜书籍
        if (similarDocs.isEmpty()) {
            LambdaQueryWrapper<BookInfo> qw = new LambdaQueryWrapper<>();
            qw.gt(BookInfo::getWordCount, 0)
                    .orderByDesc(BookInfo::getVisitCount)
                    .last("LIMIT " + safeCount);
            List<BookInfo> hotBooks = bookInfoMapper.selectList(qw);
            List<SimilarBookRecommendDto> respList = hotBooks.stream().map(b -> SimilarBookRecommendDto.builder()
                    .bookId(b.getId())
                    .bookName(b.getBookName())
                    .categoryName(b.getCategoryName())
                    .authorName(b.getAuthorName())
                    .score(b.getScore())
                    .picUrl(b.getPicUrl())
                    .bookDesc(b.getBookDesc())
                    .recommendReason("全站人气热读榜力荐")
                    .similarity(0.95)
                    .build()).toList();
            return RestResp.ok(respList);
        }

        List<SimilarBookRecommendDto> respList = similarDocs.stream().map(d -> SimilarBookRecommendDto.builder()
                .bookId(d.getBookId())
                .bookName(d.getBookName())
                .categoryName(d.getCategoryName())
                .authorName(d.getAuthorName())
                .score(d.getScore())
                .picUrl(d.getPicUrl())
                .bookDesc(d.getBookDesc())
                .recommendReason("与您近期书架阅读偏好契合度高")
                .similarity(d.getSimilarityScore())
                .build()).toList();

        return RestResp.ok(respList);
    }

    @Override
    public RestResp<Integer> syncAllBookVectors() {
        int count = bookVectorSyncService.syncAllBooksToVectorStore();
        return RestResp.ok(count);
    }
}
