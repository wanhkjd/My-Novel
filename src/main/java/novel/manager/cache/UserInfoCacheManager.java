package novel.manager.cache;

import novel.core.constant.CacheConsts;
import novel.dao.entity.UserInfo;
import novel.dao.mapper.UserInfoMapper;
import novel.dto.UserInfoDto;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 用户信息 缓存管理类
 *
 * @author xiongxiaoyang
 * @date 2022/5/12
 */
@Component
@RequiredArgsConstructor
public class UserInfoCacheManager {

    private final UserInfoMapper userInfoMapper;

    /**
     * 查询用户信息，并放入缓存中
     */
    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.USER_INFO_CACHE_NAME, unless = "#result == null")
    public UserInfoDto getUser(Long userId) {
        return getUserForAuth(userId);
    }

    /**
     * 鉴权读取数据库中的当前状态，不使用可能被并发查询回填的旧缓存。
     */
    public UserInfoDto getUserForAuth(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (Objects.isNull(userInfo)) {
            return null;
        }
        return UserInfoDto.builder()
            .id(userInfo.getId())
            .status(userInfo.getStatus()).build();
    }

    /**
     * Redis 缓存管理器启用了 transactionAware，事务成功提交后才执行失效。
     */
    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
        value = CacheConsts.USER_INFO_CACHE_NAME)
    public void evictUserInfoCache(Long userId) {
        // 由 Spring 缓存代理执行失效。
    }

}
