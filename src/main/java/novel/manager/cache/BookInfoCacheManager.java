package novel.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import novel.core.constant.CacheConsts;
import novel.core.constant.DatabaseConsts;
import novel.dao.entity.BookChapter;
import novel.dao.entity.BookInfo;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.resp.BookInfoRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小说信息读取入口，保留原类名及缓存淘汰方法以兼容已有调用。
 *
 * <p>可变详情直接查询数据库，不读写详情缓存，避免事务提交前淘汰、旧请求回填及
 * 多实例本地缓存残留导致的长期不一致。以增加数据库查询负载为代价，不再持久化
 * 过期详情；单次读取仍遵循数据库事务可见性。最新更新ID列表继续使用缓存。</p>
 *
 * @author xiongxiaoyang
 * @date 2022/5/12
 */
@Component
@RequiredArgsConstructor
public class BookInfoCacheManager {

    private final BookInfoMapper bookInfoMapper;

    private final BookChapterMapper bookChapterMapper;

    /**
     * 每次从数据库查询小说信息，忽略已有详情缓存且不回填查询结果。
     *
     * @param id 小说ID
     * @return 小说详情，不存在时返回 null
     */
    public BookInfoRespDto getBookInfo(Long id) {
        // 查询基础信息
        BookInfo bookInfo = bookInfoMapper.selectById(id);
        if (bookInfo == null) {
            return null;
        }
        // 查询首章ID
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper
            .eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, id)
            .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter firstBookChapter = bookChapterMapper.selectOne(queryWrapper);
        // 组装响应对象
        return BookInfoRespDto.builder()
            .id(bookInfo.getId())
            .bookName(bookInfo.getBookName())
            .bookDesc(bookInfo.getBookDesc())
            .bookStatus(bookInfo.getBookStatus())
            .authorId(bookInfo.getAuthorId())
            .authorName(bookInfo.getAuthorName())
            .categoryId(bookInfo.getCategoryId())
            .categoryName(bookInfo.getCategoryName())
            .commentCount(bookInfo.getCommentCount())
            .firstChapterId(firstBookChapter != null ? firstBookChapter.getId() : null)
            .lastChapterId(bookInfo.getLastChapterId())
            .picUrl(bookInfo.getPicUrl())
            .visitCount(bookInfo.getVisitCount())
            .wordCount(bookInfo.getWordCount())
            .build();
    }

    /**
     * 保留旧详情缓存淘汰入口以兼容已有调用；详情读取的正确性不依赖此方法。
     *
     * @param bookId 小说ID
     */
    @CacheEvict(cacheManager = CacheConsts.CAFFEINE_CACHE_MANAGER,
        value = CacheConsts.BOOK_INFO_CACHE_NAME)
    public void evictBookInfoCache(Long bookId) {
        // 调用此方法自动清除小说信息的缓存
    }

    /**
     * 查询每个类别下最新更新的 500 个小说ID列表，并放入缓存中 1 个小时
     */
    @Cacheable(cacheManager = CacheConsts.CAFFEINE_CACHE_MANAGER,
        value = CacheConsts.LAST_UPDATE_BOOK_ID_LIST_CACHE_NAME)
    public List<Long> getLastUpdateIdList(Long categoryId) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_CATEGORY_ID, categoryId)
            .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
            .orderByDesc(DatabaseConsts.BookTable.COLUMN_LAST_CHAPTER_UPDATE_TIME)
            .last(DatabaseConsts.SqlEnum.LIMIT_500.getSql());
        return bookInfoMapper.selectList(queryWrapper).stream().map(BookInfo::getId).toList();
    }

}
