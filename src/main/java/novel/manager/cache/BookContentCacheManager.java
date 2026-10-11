package novel.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import novel.core.constant.CacheConsts;
import novel.core.constant.DatabaseConsts;
import novel.dao.entity.BookContent;
import novel.dao.mapper.BookContentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * 小说内容读取入口，保留原类名及缓存淘汰方法以兼容已有调用。
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
public class BookContentCacheManager {

    private final BookContentMapper bookContentMapper;

    /**
     * 每次从数据库查询小说内容，忽略已有详情缓存且不回填查询结果。
     *
     * @param chapterId 章节ID
     * @return 章节正文，不存在时返回 null
     */
    public String getBookContent(Long chapterId) {
        QueryWrapper<BookContent> contentQueryWrapper = new QueryWrapper<>();
        contentQueryWrapper.eq(DatabaseConsts.BookContentTable.COLUMN_CHAPTER_ID, chapterId)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookContent bookContent = bookContentMapper.selectOne(contentQueryWrapper);
        return bookContent != null ? bookContent.getContent() : null;
    }

    /**
     * 保留旧详情缓存淘汰入口以兼容已有调用；详情读取的正确性不依赖此方法。
     *
     * @param chapterId 章节ID
     */
    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.BOOK_CONTENT_CACHE_NAME)
    public void evictBookContentCache(Long chapterId) {
        // 调用此方法自动清除小说内容信息的缓存
    }


}
