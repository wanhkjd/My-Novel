package novel.manager.cache;

import novel.core.constant.CacheConsts;
import novel.dao.entity.BookChapter;
import novel.dao.mapper.BookChapterMapper;
import novel.dto.resp.BookChapterRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * 小说章节读取入口，保留原类名及缓存淘汰方法以兼容已有调用。
 *
 * <p>可变详情直接查询数据库，不读写详情缓存，避免事务提交前淘汰、旧请求回填及
 * 多实例本地缓存残留导致的长期不一致。以增加数据库查询负载为代价，不再持久化
 * 过期详情；单次读取仍遵循数据库事务可见性。</p>
 *
 * @author xiongxiaoyang
 * @date 2022/5/12
 */
@Component
@RequiredArgsConstructor
public class BookChapterCacheManager {

    private final BookChapterMapper bookChapterMapper;

    /**
     * 每次从数据库查询小说章节，忽略已有详情缓存且不回填查询结果。
     *
     * @param chapterId 章节ID
     * @return 章节详情，不存在时返回 null
     */
    public BookChapterRespDto getChapter(Long chapterId) {
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            return null;
        }
        return BookChapterRespDto.builder()
            .id(chapterId)
            .bookId(bookChapter.getBookId())
            .chapterNum(bookChapter.getChapterNum())
            .chapterName(bookChapter.getChapterName())
            .chapterWordCount(bookChapter.getWordCount())
            .chapterUpdateTime(bookChapter.getUpdateTime())
            .isVip(bookChapter.getIsVip())
            .build();
    }

    /**
     * 保留旧详情缓存淘汰入口以兼容已有调用；详情读取的正确性不依赖此方法。
     *
     * @param chapterId 章节ID
     */
    @CacheEvict(cacheManager = CacheConsts.CAFFEINE_CACHE_MANAGER,
        value = CacheConsts.BOOK_CHAPTER_CACHE_NAME)
    public void evictBookChapterCache(Long chapterId) {
        // 调用此方法自动清除小说章节信息的缓存
    }

}
