package novel.core.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import novel.dao.entity.BookInfo;

import java.io.Serializable;
import java.util.List;

/**
 * Elasticsearch 8 存储小说向量及元数据 DTO
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsVectorBookDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档主键 ID (即书籍 ID)
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 小说名
     */
    private String bookName;

    /**
     * 类别ID
     */
    private Long categoryId;

    /**
     * 类别名
     */
    private String categoryName;

    /**
     * 作家ID
     */
    private Long authorId;

    /**
     * 作家名
     */
    private String authorName;

    /**
     * 书籍描述
     */
    private String bookDesc;

    /**
     * 评分
     */
    private Integer score;

    /**
     * 总字数
     */
    private Integer wordCount;

    /**
     * 点击量
     */
    private Long visitCount;

    /**
     * 封面图片地址
     */
    private String picUrl;

    /**
     * 用于生成向量的语义文本摘要
     */
    private String summaryText;

    /**
     * 稠密向量 (Dense Vector，维度通常为 1536 或 1024)
     */
    private List<Float> embedding;

    /**
     * 相似度得分 (KNN 召回时使用，不存入 ES mapping)
     */
    private Double similarityScore;

    /**
     * 从 BookInfo 实体快速构建基础 DTO
     */
    public static EsVectorBookDto fromBookInfo(BookInfo book) {
        if (book == null) {
            return null;
        }
        String desc = book.getBookDesc() == null ? "" : book.getBookDesc().replaceAll("\\s+", " ").trim();
        String summary = String.format("《%s》 | 分类：%s | 作者：%s | 评分：%d分 | 简介：%s",
                book.getBookName(),
                book.getCategoryName() == null ? "其他" : book.getCategoryName(),
                book.getAuthorName(),
                book.getScore() == null ? 0 : book.getScore(),
                desc);

        return EsVectorBookDto.builder()
                .id(book.getId())
                .bookId(book.getId())
                .bookName(book.getBookName())
                .categoryId(book.getCategoryId())
                .categoryName(book.getCategoryName())
                .authorId(book.getAuthorId())
                .authorName(book.getAuthorName())
                .bookDesc(desc)
                .score(book.getScore())
                .wordCount(book.getWordCount())
                .visitCount(book.getVisitCount())
                .picUrl(book.getPicUrl())
                .summaryText(summary)
                .build();
    }
}
