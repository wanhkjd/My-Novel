package novel.controller.front;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.ai.dto.ChatHistoryMsgDto;
import novel.core.ai.dto.SimilarBookRecommendDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.service.BookCompanionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 前台门户 - 智能书童 Controller
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Tag(name = "AiBookCompanionController", description = "前台门户-AI智能书童模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_AI_URL_PREFIX + "/companion")
@RequiredArgsConstructor
@Slf4j
public class AiBookCompanionController {

    private final BookCompanionService bookCompanionService;

    /**
     * 智能书童流式对话接口 (SSE 打字机效果)
     * 支持找书、查榜单、自动加书架、分析偏好推荐
     */
    @Operation(summary = "智能书童流式对话接口 (SSE)")
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(
            @Parameter(description = "读者发送的自然语言消息") @RequestParam("message") String message,
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.chatStream(message, conversationId);
    }

    /**
     * 智能书童常规对话接口 (阻塞式返回)
     */
    @Operation(summary = "智能书童常规对话接口")
    @GetMapping("/chat")
    public RestResp<String> chat(
            @Parameter(description = "读者发送的自然语言消息") @RequestParam("message") String message,
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.chat(message, conversationId);
    }

    /**
     * 清空指定会话的对话记忆 (用于"新对话")
     */
    @Operation(summary = "清空指定会话的对话记忆")
    @DeleteMapping("/chat/memory")
    public RestResp<Void> clearMemory(
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.clearMemory(conversationId);
    }

    /**
     * 查询指定会话的历史消息 (用于刷新回显)
     */
    @Operation(summary = "查询指定会话的历史消息")
    @GetMapping("/chat/history")
    public RestResp<List<ChatHistoryMsgDto>> getHistory(
            @Parameter(description = "会话ID") @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        return bookCompanionService.getHistory(conversationId);
    }

    /**
     * 读者个性化阅读偏好相似推荐
     */
    @Operation(summary = "读者个性化阅读偏好相似推荐")
    @GetMapping("/recommend")
    public RestResp<List<SimilarBookRecommendDto>> recommendSimilarBooks(
            @Parameter(description = "推荐数量，默认 4 本") @RequestParam(value = "count", defaultValue = "4") Integer count
    ) {
        return bookCompanionService.recommendSimilarBooks(count);
    }

}
