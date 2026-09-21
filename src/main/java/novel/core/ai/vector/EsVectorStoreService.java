package novel.core.ai.vector;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import novel.core.ai.config.AiProperties;
import novel.core.ai.dto.EsVectorBookDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Elasticsearch 8.2.0 向量库底层服务
 * 封装索引管理、向量批量入库与 KNN 相似度检索
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "enabled", havingValue = "true")
public class EsVectorStoreService {

    private final ElasticsearchClient esClient;
    private final AiProperties aiProperties;

    /**
     * 自动初始化 ES8 向量索引（若不存在则根据 HNSW dense_vector 结构自动创建）
     */
    public boolean initVectorIndexIfNotExists() {
        String indexName = aiProperties.getVectorIndexName();
        int dims = aiProperties.getEmbeddingDims();

        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                log.info("[ES8 向量库] 索引 {} 已存在，跳过初始化", indexName);
                return true;
            }

            log.info("[ES8 向量库] 正在创建向量索引: {}, 向量维度: {}", indexName, dims);
            var createResponse = esClient.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("bookId", p -> p.long_(l -> l))
                            .properties("bookName", p -> p.text(t -> t.fields("keyword", k -> k.keyword(kw -> kw))))
                            .properties("categoryId", p -> p.long_(l -> l))
                            .properties("categoryName", p -> p.keyword(k -> k))
                            .properties("authorId", p -> p.long_(l -> l))
                            .properties("authorName", p -> p.keyword(k -> k))
                            .properties("bookDesc", p -> p.text(t -> t))
                            .properties("score", p -> p.integer(i -> i))
                            .properties("wordCount", p -> p.integer(i -> i))
                            .properties("visitCount", p -> p.long_(l -> l))
                            .properties("picUrl", p -> p.keyword(k -> k.index(false)))
                            .properties("summaryText", p -> p.text(t -> t))
                            .properties("embedding", p -> p.denseVector(d -> d
                                    .dims(dims)
                                    .index(true)
                                    .similarity("cosine")
                                    .indexOptions(io -> io
                                            .type("hnsw")
                                            .m(16)
                                            .efConstruction(100)
                                    )
                            ))
                    )
            );

            log.info("[ES8 向量库] 索引 {} 创建成功: acknowledged={}", indexName, createResponse.acknowledged());
            return createResponse.acknowledged();
        } catch (Exception e) {
            log.error("[ES8 向量库] 初始化索引 {} 异常: {}", indexName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量将书籍数据和向量存入 ES8
     */
    public boolean saveOrUpdateVectors(List<EsVectorBookDto> bookList) {
        if (bookList == null || bookList.isEmpty()) {
            return true;
        }

        String indexName = aiProperties.getVectorIndexName();
        initVectorIndexIfNotExists();

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (EsVectorBookDto dto : bookList) {
                if (dto.getId() == null || dto.getEmbedding() == null) {
                    continue;
                }
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(dto.getId().toString())
                                .document(dto)
                        )
                ).timeout(Time.of(t -> t.time("30s")));
            }

            BulkResponse bulkResponse = esClient.bulk(bulkBuilder.build());
            if (bulkResponse.errors()) {
                log.error("[ES8 向量库] 批量写入存在失败项: {}", bulkResponse.items());
                return false;
            }
            log.info("[ES8 向量库] 成功批量写入 {} 本书籍向量至索引 {}", bookList.size(), indexName);
            return true;
        } catch (Exception e) {
            log.error("[ES8 向量库] 批量写入向量异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行 KNN 稠密向量相似度检索 (Cosine Similarity)
     *
     * @param queryVector   查询向量
     * @param topK          返回最多数量
     * @param excludeBookIds 排除的书籍ID列表（如用户已加入书架的书）
     * @return 召回的书籍列表 (包含相似度评分)
     */
    public List<EsVectorBookDto> knnSearch(List<Float> queryVector, int topK, List<Long> excludeBookIds) {
        if (queryVector == null || queryVector.isEmpty()) {
            return Collections.emptyList();
        }

        String indexName = aiProperties.getVectorIndexName();
        int safeTopK = Math.max(1, Math.min(topK, 50));
        int numCandidates = Math.max(safeTopK * 10, 50);

        try {
            // 构造过滤条件（如排除已读或已有书籍）
            Query filterQuery = null;
            if (excludeBookIds != null && !excludeBookIds.isEmpty()) {
                List<FieldValue> excludeValues = excludeBookIds.stream()
                        .map(FieldValue::of)
                        .toList();
                filterQuery = Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.terms(t -> t.field("bookId").terms(tv -> tv.value(excludeValues))))));
            }

            Query finalFilterQuery = filterQuery;
            SearchResponse<EsVectorBookDto> response = esClient.search(s -> s
                    .index(indexName)
                    .knn(k -> {
                        k.field("embedding")
                                .queryVector(queryVector)
                                .k((long) safeTopK)
                                .numCandidates((long) numCandidates);
                        if (finalFilterQuery != null) {
                            k.filter(finalFilterQuery);
                        }
                        return k;
                    })
                    .size(safeTopK),
                    EsVectorBookDto.class
            );

            List<EsVectorBookDto> result = new ArrayList<>();
            for (Hit<EsVectorBookDto> hit : response.hits().hits()) {
                EsVectorBookDto source = hit.source();
                if (source != null) {
                    source.setSimilarityScore(hit.score());
                    result.add(source);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[ES8 向量库] KNN 向量搜索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据书籍 ID 获取已存储的向量与元数据
     */
    public EsVectorBookDto getVectorByBookId(Long bookId) {
        if (bookId == null) {
            return null;
        }
        try {
            GetResponse<EsVectorBookDto> response = esClient.get(g -> g
                    .index(aiProperties.getVectorIndexName())
                    .id(bookId.toString()),
                    EsVectorBookDto.class
            );
            if (response.found()) {
                return response.source();
            }
        } catch (Exception e) {
            log.warn("[ES8 向量库] 获取书籍 [ID: {}] 向量失败: {}", bookId, e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前向量索引内文档总数
     */
    public long countVectors() {
        try {
            return esClient.count(c -> c.index(aiProperties.getVectorIndexName())).count();
        } catch (Exception e) {
            return 0L;
        }
    }
}
