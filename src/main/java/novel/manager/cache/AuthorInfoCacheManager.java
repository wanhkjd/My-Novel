package novel.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import novel.core.constant.CacheConsts;
import novel.core.constant.DatabaseConsts;
import novel.dao.entity.AuthorInfo;
import novel.dao.mapper.AuthorInfoMapper;
import novel.dto.AuthorInfoDto;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 作家信息 缓存管理类
 *
 * @author xiongxiaoyang
 * @date 2022/5/12
 */
@Component
@RequiredArgsConstructor
public class AuthorInfoCacheManager {

    private final AuthorInfoMapper authorInfoMapper;

    /**
     * 查询作家信息，并放入缓存中
     */
    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.AUTHOR_INFO_CACHE_NAME, unless = "#result == null")
    public AuthorInfoDto getAuthor(Long userId) {
        return getAuthorForAuth(userId);
    }

    /**
     * 鉴权与状态查询直接读取数据库，避免封禁/解封后被并发回填的旧缓存影响。
     */
    public AuthorInfoDto getAuthorForAuth(Long userId) {
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
            .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(authorInfo)) {
            return null;
        }
        return AuthorInfoDto.builder()
            .id(authorInfo.getId())
            .penName(authorInfo.getPenName())
            .status(authorInfo.getStatus()).build();
    }

    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.AUTHOR_INFO_CACHE_NAME, key = "#userId")
    public void evictAuthorCache(Long userId) {
        // 调用此方法自动清除指定作家信息的缓存
    }

    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.AUTHOR_INFO_CACHE_NAME, allEntries = true)
    public void evictAllAuthorCache() {
        // 清除所有作家信息的缓存
    }

}
