package novel.core.ai.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.ai.dto.EsVectorBookDto;
import novel.dao.entity.BookInfo;
import novel.dao.mapper.BookInfoMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 小说书籍向量化与数据同步服务
 * 负责从 MySQL 读取 1000 本书，生成语义文本并调用 Spring AI EmbeddingModel 写入 ES8
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "enabled", havingValue = "true")
public class BookVectorSyncService {

    private final BookInfoMapper bookInfoMapper;
    private final EmbeddingModel embeddingModel;
    private final EsVectorStoreService esVectorStoreService;

    /**
     * 全量同步 MySQL 中约 1000 本小说数据至 Elasticsearch 8 向量库
     *
     * @return 成功同步的书籍数量
     */
    public int syncAllBooksToVectorStore() {
        log.info("[AI 书籍向量同步] 开始从 MySQL 读取书籍并向量化存入 ES8...");
        boolean initOk = esVectorStoreService.initVectorIndexIfNotExists();
        if (!initOk) {
            log.error("[AI 书籍向量同步] ES8 向量索引初始化失败，终止同步！（请检查 192.168.100.129:9200 Elasticsearch 服务是否已启动）");
            return 0;
        }

        // 查询有效小说 (有字数且信息完整的作品)
        LambdaQueryWrapper<BookInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(BookInfo::getWordCount, 0)
                .orderByDesc(BookInfo::getVisitCount);

        List<BookInfo> bookList = bookInfoMapper.selectList(queryWrapper);
        if (bookList == null || bookList.isEmpty()) {
            log.warn("[AI 书籍向量同步] MySQL 中暂无可同步的书籍数据");
            return 0;
        }

        int totalBooks = bookList.size();
        log.info("[AI 书籍向量同步] 共扫描到 {} 本小说，准备分批生成 Embedding 并写入 ES8", totalBooks);

        int batchSize = 20; // 每批 20 本，兼顾网络与大模型 Embedding 接口稳定性
        int successCount = 0;

        List<EsVectorBookDto> batchDtos = new ArrayList<>(batchSize);
        for (int i = 0; i < totalBooks; i++) {
            BookInfo book = bookList.get(i);
            try {
                EsVectorBookDto dto = EsVectorBookDto.fromBookInfo(book);
                if (dto == null) {
                    continue;
                }

                // 调用 Spring AI EmbeddingModel 生成向量
                List<Float> vector = embedText(dto.getSummaryText());
                dto.setEmbedding(vector);
                batchDtos.add(dto);

                // 达到一批或到达列表末尾，触发写入
                if (batchDtos.size() >= batchSize || i == totalBooks - 1) {
                    boolean ok = esVectorStoreService.saveOrUpdateVectors(batchDtos);
                    if (ok) {
                        successCount += batchDtos.size();
                        log.info("[AI 书籍向量同步] 进度: {}/{} 本小说向量已入库", successCount, totalBooks);
                    }
                    batchDtos.clear();
                }
            } catch (Exception e) {
                log.error("[AI 书籍向量同步] 处理书籍 [ID: {}, 名: {}] 向量化失败: {}",
                        book.getId(), book.getBookName(), e.getMessage(), e);
            }
        }

        log.info("[AI 书籍向量同步] 全部同步完成！成功同步 {}/{} 本小说向量", successCount, totalBooks);
        return successCount;
    }

    /**
     * 单本书籍向量同步 (用于新增/更新小说时实时同步)
     */
    public boolean syncSingleBook(Long bookId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            return false;
        }
        try {
            EsVectorBookDto dto = EsVectorBookDto.fromBookInfo(book);
            if (dto != null) {
                List<Float> vector = embedText(dto.getSummaryText());
                dto.setEmbedding(vector);
                return esVectorStoreService.saveOrUpdateVectors(List.of(dto));
            }
        } catch (Exception e) {
            log.error("[AI 书籍向量同步] 同步单本书籍 [ID: {}] 失败: {}", bookId, e.getMessage());
        }
        return false;
    }

    /**
     * 将输入文本转换为 Float 向量列表
     */
    public List<Float> embedText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Double> embeddings = embeddingModel.embed(text);
            if (embeddings == null || embeddings.isEmpty()) {
                return Collections.emptyList();
            }
            return embeddings.stream().map(Double::floatValue).toList();
        } catch (Exception e) {
            log.error("[AI Embedding] 生成文本向量失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
