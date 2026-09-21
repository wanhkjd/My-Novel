package novel.core.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.ai.dto.EsVectorBookDto;
import novel.core.ai.vector.BookVectorSyncService;
import novel.core.ai.vector.EsVectorStoreService;
import novel.core.auth.UserHolder;
import novel.core.common.resp.RestResp;
import novel.dao.entity.BookInfo;
import novel.dao.entity.UserBookshelf;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import novel.dto.resp.BookRankRespDto;
import novel.service.BookService;
import novel.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能书童业务工具集 (Spring AI Function Calling)
 * 供大模型根据读者意图自主编排与调用
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BookCompanionTools {

    private final BookService bookService;
    private final UserService userService;
    private final BookInfoMapper bookInfoMapper;
    private final UserBookshelfMapper userBookshelfMapper;
    private final EsVectorStoreService esVectorStoreService;
    private final BookVectorSyncService bookVectorSyncService;

    // ==================== 请求入参 Records 定义 ====================

    public record SearchBooksRequest(
            @JsonProperty(required = true, value = "queryPrompt")
            @JsonPropertyDescription("读者的找书描述或意图关键词（例如'想找一本主角杀伐果断、在修真界种田的爽文'）")
            String queryPrompt,

            @JsonProperty(value = "limit")
            @JsonPropertyDescription("期望返回的书籍数量，默认为 3")
            Integer limit
    ) {}

    public record GetBookRankRequest(
            @JsonProperty(required = true, value = "rankType")
            @JsonPropertyDescription("榜单类型: 'visit'(点击榜/人气榜), 'newest'(新书榜), 或 'update'(更新榜)")
            String rankType
    ) {}

    public record AddToBookshelfRequest(
            @JsonProperty(required = true, value = "bookId")
            @JsonPropertyDescription("小说ID (数字)")
            Long bookId
    ) {}

    public record RecommendBooksRequest(
            @JsonProperty(value = "count")
            @JsonPropertyDescription("期望推荐的小说数量，默认为 3")
            Integer count
    ) {}

    // ==================== Function Beans 注册 ====================

    @Bean
    public org.springframework.ai.model.function.FunctionCallback searchBooksFunction() {
        return new SafeFunctionCallback<>(
                "searchBooksFunction",
                "根据读者的自然语言描述、剧情偏好或特定题材意图（例如'想找一本主角杀伐果断、在修真界种田的爽文'），通过 Elasticsearch 8 向量相似度检索最匹配的小说",
                SearchBooksRequest.class,
                req -> searchBooksBySemantics(req == null ? null : req.queryPrompt(), req == null ? null : req.limit())
        );
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback getBookRankFunction() {
        return new SafeFunctionCallback<>(
                "getBookRankFunction",
                "查询平台小说排行榜单。rankType 可选值：'visit'(点击榜/人气榜), 'newest'(新书榜), 'update'(实时更新榜)",
                GetBookRankRequest.class,
                req -> getBookRank(req == null ? null : req.rankType())
        );
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback addToBookshelfFunction() {
        return new SafeFunctionCallback<>(
                "addToBookshelfFunction",
                "将指定小说加入当前登录读者的个人书架。必须提供小说ID (bookId)",
                AddToBookshelfRequest.class,
                req -> addToBookshelf(req == null ? null : req.bookId())
        );
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback recommendBooksFunction() {
        return new SafeFunctionCallback<>(
                "recommendBooksFunction",
                "分析当前登录读者的书架阅读记录，基于已读小说的偏好向量，从千本藏书中推荐最契合的未读相似好书",
                RecommendBooksRequest.class,
                req -> analyzeUserReadingAndRecommend(req == null ? null : req.count())
        );
    }

    // ==================== 业务工具具体逻辑实现 ====================

    /**
     * 工具 1：根据读者自然语言意图进行语义搜书
     */
    public String searchBooksBySemantics(String queryPrompt, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 3 : Math.min(limit, 10);
        String prompt = (queryPrompt == null || queryPrompt.isBlank()) ? "热门高分小说推荐" : queryPrompt;
        log.info("[AI Tool - 语义找书] 收到检索词: {}, 数量: {}", prompt, safeLimit);

        List<Float> queryVector = bookVectorSyncService.embedText(prompt);
        if (queryVector.isEmpty()) {
            return "未能解析语义向量，请尝试更详细的描述。";
        }

        List<EsVectorBookDto> matchedBooks = esVectorStoreService.knnSearch(queryVector, safeLimit, null);
        if (matchedBooks.isEmpty()) {
            return "在书库中暂未找到与该描述高度契合的小说，请尝试换个题材或描述。";
        }

        StringBuilder sb = new StringBuilder("为你找到以下最契合的小说：\n");
        for (int i = 0; i < matchedBooks.size(); i++) {
            EsVectorBookDto b = matchedBooks.get(i);
            sb.append(String.format("%d. 《%s》（ID: %d）- 分类: %s | 作者: %s | 评分: %.1f分\n   看点简介: %s\n",
                    i + 1,
                    b.getBookName(),
                    b.getBookId(),
                    b.getCategoryName(),
                    b.getAuthorName(),
                    b.getScore() == null ? 0.0 : b.getScore() / 10.0,
                    b.getBookDesc()));
        }
        return sb.toString();
    }

    /**
     * 工具 2：查询平台各类排行榜
     */
    public String getBookRank(String rankType) {
        String type = (rankType == null || rankType.isBlank()) ? "visit" : rankType.toLowerCase().trim();
        log.info("[AI Tool - 查榜单] 查询榜单类型: {}", type);

        RestResp<List<BookRankRespDto>> resp = switch (type) {
            case "newest" -> bookService.listNewestRankBooks();
            case "update" -> bookService.listUpdateRankBooks();
            default -> bookService.listVisitRankBooks();
        };

        List<BookRankRespDto> rankList = resp.getData();
        if (rankList == null || rankList.isEmpty()) {
            return "当前榜单暂无数据。";
        }

        String rankTitle = switch (type) {
            case "newest" -> "【最新入库·新书榜 Top 5】";
            case "update" -> "【勤奋更新·更新榜 Top 5】";
            default -> "【全站热度·点击榜 Top 5】";
        };

        StringBuilder sb = new StringBuilder(rankTitle).append("\n");
        int count = Math.min(rankList.size(), 5);
        for (int i = 0; i < count; i++) {
            BookRankRespDto r = rankList.get(i);
            sb.append(String.format("%d. 《%s》（ID: %d）| 分类: %s | 作者: %s | 最新: %s\n",
                    i + 1,
                    r.getBookName(),
                    r.getId(),
                    r.getCategoryName(),
                    r.getAuthorName(),
                    r.getLastChapterName() == null ? "暂无" : r.getLastChapterName()));
        }
        return sb.toString();
    }

    /**
     * 工具 3：自动将小说加入当前读者的书架
     */
    public String addToBookshelf(Long bookId) {
        Long userId = UserHolder.getUserId();
        log.info("[AI Tool - 自动加书架] 操作人 userId: {}, 目标 bookId: {}", userId, bookId);

        if (userId == null) {
            return "【提示】读者当前未登录，无法自动加入书架。请提示读者在右上角登录账号后再试。";
        }

        if (bookId == null) {
            return "未获取到有效的小说 ID，请确认要加入书架的书籍。";
        }

        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            return "书库中未找到 ID 为 " + bookId + " 的小说。";
        }

        try {
            userService.addBookshelf(userId, bookId);
            return "已成功将《" + bookInfo.getBookName() + "》(ID: " + bookId + ") 加入读者的个人书架！";
        } catch (Exception e) {
            return "加入书架失败：" + e.getMessage();
        }
    }

    /**
     * 工具 4：分析读者阅读历史并推荐相似书籍
     */
    public String analyzeUserReadingAndRecommend(Integer count) {
        Long userId = UserHolder.getUserId();
        int safeCount = (count == null || count <= 0) ? 3 : Math.min(count, 5);
        log.info("[AI Tool - 偏好推荐] 正在为用户 userId: {} 分析书架并推荐 {} 本相似书", userId, safeCount);

        if (userId == null) {
            return "【读者未登录】无法读取历史书架，请提示读者登录后可享受定制化相似推荐，并先为其展示全站最火爆的点击榜书籍。";
        }

        // 查询用户书架记录
        LambdaQueryWrapper<UserBookshelf> qw = new LambdaQueryWrapper<>();
        qw.eq(UserBookshelf::getUserId, userId)
                .orderByDesc(UserBookshelf::getUpdateTime)
                .last("LIMIT 10");
        List<UserBookshelf> shelfList = userBookshelfMapper.selectList(qw);

        if (shelfList == null || shelfList.isEmpty()) {
            return "【书架暂无书籍】该读者的书架尚空空如也，请热情地为他推荐平台最受欢迎的热门神作（可调用查榜工具）。";
        }

        List<Long> readBookIds = shelfList.stream().map(UserBookshelf::getBookId).toList();
        List<BookInfo> readBooks = bookInfoMapper.selectBatchIds(readBookIds);

        // 提取用户已读书目清单
        String historyBooks = readBooks.stream()
                .map(b -> "《" + b.getBookName() + "》(" + b.getCategoryName() + ")")
                .collect(Collectors.joining("、"));

        // 提取这些书籍在 ES8 中的向量，计算加权偏好中心向量
        List<List<Float>> vectors = new ArrayList<>();
        for (Long bookId : readBookIds) {
            EsVectorBookDto doc = esVectorStoreService.getVectorByBookId(bookId);
            if (doc != null && doc.getEmbedding() != null && !doc.getEmbedding().isEmpty()) {
                vectors.add(doc.getEmbedding());
            }
        }

        List<EsVectorBookDto> similarBooks;
        if (!vectors.isEmpty()) {
            // 计算平均偏好向量
            int dims = vectors.get(0).size();
            List<Float> centroidVector = new ArrayList<>(Collections.nCopies(dims, 0.0f));
            for (List<Float> v : vectors) {
                for (int d = 0; d < dims; d++) {
                    centroidVector.set(d, centroidVector.get(d) + v.get(d));
                }
            }
            for (int d = 0; d < dims; d++) {
                centroidVector.set(d, centroidVector.get(d) / vectors.size());
            }

            // 在 ES8 中排查已读书籍并检索相似书籍
            similarBooks = esVectorStoreService.knnSearch(centroidVector, safeCount, readBookIds);
        } else {
            // 降级：如果向量暂未同步，通过第一个分类检索
            String category = readBooks.get(0).getCategoryName();
            return String.format("读者近期喜爱阅读：%s。其题材偏向【%s】，请结合该风格为其推荐相关神作。", historyBooks, category);
        }

        if (similarBooks.isEmpty()) {
            return "读者已读过较多同类书籍，暂时没有更多未读相似作品可推荐。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【读者阅读画像分析】\n读者近期书架已收藏：").append(historyBooks).append("\n\n");
        sb.append("【基于 AI 向量偏好相似度深度推荐】\n");
        for (int i = 0; i < similarBooks.size(); i++) {
            EsVectorBookDto b = similarBooks.get(i);
            sb.append(String.format("%d. 《%s》（ID: %d）\n   - 分类: %s | 作者: %s | 评分: %.1f分\n   - 简介看点: %s\n",
                    i + 1,
                    b.getBookName(),
                    b.getBookId(),
                    b.getCategoryName(),
                    b.getAuthorName(),
                    b.getScore() == null ? 0.0 : b.getScore() / 10.0,
                    b.getBookDesc()));
        }
        return sb.toString();
    }
}
