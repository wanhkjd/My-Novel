package novel.service;

import novel.core.ai.dto.ChatHistoryMsgDto;
import novel.core.ai.dto.SimilarBookRecommendDto;
import novel.core.common.resp.RestResp;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能书童服务接口
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
public interface BookCompanionService {

    /**
     * 智能书童对话 (打字机流式输出 SSE)
     *
     * @param message        读者发送的问题或指令
     * @param conversationId 前端会话 ID（可空，为空回退 default），用于多轮记忆
     * @return 文本流响应
     */
    Flux<String> chatStream(String message, String conversationId);

    /**
     * 智能书童对话 (常规阻塞式交互)
     *
     * @param message        读者发送的问题或指令
     * @param conversationId 前端会话 ID（可空，为空回退 default），用于多轮记忆
     * @return 响应文本
     */
    RestResp<String> chat(String message, String conversationId);

    /**
     * 清空指定会话的对话记忆（用于"新对话"）
     *
     * @param conversationId 前端会话 ID
     * @return 操作结果
     */
    RestResp<Void> clearMemory(String conversationId);

    /**
     * 查询指定会话的历史消息（用于刷新回显）
     *
     * @param conversationId 前端会话 ID
     * @return 历史消息列表（含 role、content）
     */
    RestResp<List<ChatHistoryMsgDto>> getHistory(String conversationId);

    /**
     * 分析读者阅读偏好并推荐相似书籍 (结构化数据接口，方便前端卡片展示)
     *
     * @param count 期望推荐数量
     * @return 相似书籍推荐列表
     */
    RestResp<List<SimilarBookRecommendDto>> recommendSimilarBooks(Integer count);

    /**
     * 手动触发全量 1000 本书向量化同步至 ES8
     *
     * @return 同步成功的数量
     */
    RestResp<Integer> syncAllBookVectors();

}
