package novel.core.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 相似书籍推荐结果 DTO
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/29
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarBookRecommendDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 小说名
     */
    private String bookName;

    /**
     * 类别名
     */
    private String categoryName;

    /**
     * 作家名
     */
    private String authorName;

    /**
     * 评分
     */
    private Integer score;

    /**
     * 封面图片
     */
    private String picUrl;

    /**
     * 简介
     */
    private String bookDesc;

    /**
     * 推荐理由或匹配度 (如: "92% 契合您的修仙升级偏好")
     */
    private String recommendReason;

    /**
     * 向量相似度分值
     */
    private Double similarity;
}
