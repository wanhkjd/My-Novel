package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import novel.core.auth.FrontAuthStrategy;
import novel.core.auth.UserHolder;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.constant.CacheConsts;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.JwtUtils;
import novel.dao.entity.UserInfo;
import novel.dao.mapper.UserInfoMapper;
import novel.dto.UserInfoDto;
import novel.dto.req.AdminStatusReqDto;
import novel.manager.cache.UserInfoCacheManager;
import novel.service.AdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real Spring cache/transaction proxies; in-memory substitutes for Redis and the database. */
@ExtendWith(MockitoExtension.class)
class UserStatusCacheTest {
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private JwtUtils jwtUtils;
    @InjectMocks private AdminServiceImpl adminTarget;
    private AnnotationConfigApplicationContext context;
    private UserInfoCacheManager users;
    private AdminService admin;
    private Cache cache;
    private TestTransactionManager transactions;

    @BeforeEach
    void setUp() {
        UserHolder.clear();
        ConcurrentMapCacheManager backing = new ConcurrentMapCacheManager(CacheConsts.USER_INFO_CACHE_NAME);
        cache = backing.getCache(CacheConsts.USER_INFO_CACHE_NAME);
        transactions = new TestTransactionManager(cache);
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean(CacheConsts.REDIS_CACHE_MANAGER, TransactionAwareCacheManagerProxy.class,
            () -> new TransactionAwareCacheManagerProxy(backing));
        context.registerBean("transactionManager", TestTransactionManager.class, () -> transactions);
        context.registerBean(UserInfoCacheManager.class, () -> new UserInfoCacheManager(userInfoMapper));
        context.registerBean(AdminService.class, () -> {
            ReflectionTestUtils.setField(adminTarget, "userInfoCacheManager",
                context.getBean(UserInfoCacheManager.class));
            return adminTarget;
        });
        context.refresh();
        users = context.getBean(UserInfoCacheManager.class);
        admin = context.getBean(AdminService.class);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        context.close();
    }

    @Test
    void banAndUnbanInvalidateWarmCacheAfterCommitAndAffectExistingToken() {
        AtomicInteger storedStatus = new AtomicInteger(0);
        when(userInfoMapper.selectById(10L)).thenAnswer(call -> {
            UserInfo user = new UserInfo();
            user.setId(10L);
            user.setStatus(storedStatus.get());
            return user;
        });
        when(userInfoMapper.updateById(any(UserInfo.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            storedStatus.set(((UserInfo) call.getArgument(0)).getStatus());
            return 1;
        });
        when(jwtUtils.parseToken("existing-token", SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn(10L);
        FrontAuthStrategy front = new FrontAuthStrategy(jwtUtils, users);
        users.getUser(10L); // Warm the general user cache before the status change.
        front.auth("existing-token", "/api/front/user/bookshelf");
        assertThat(cachedStatus()).isZero();
        UserHolder.clear();

        assertThat(admin.updateUserStatus(10L, status(1)).isOk()).isTrue();
        assertThat(transactions.statusAtCommit).isZero(); // Eviction must not precede DB commit.
        assertThat(cache.get(10L)).isNull();
        assertThatThrownBy(() -> front.auth("existing-token", "/api/front/user/bookshelf"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_ACCOUNT_BANNED));
        assertThat(UserHolder.getUserId()).isNull();
        users.getUser(10L); // Cache a banned snapshot to exercise unban invalidation as well.
        assertThat(cachedStatus()).isEqualTo(1);

        assertThat(admin.updateUserStatus(10L, status(0)).isOk()).isTrue();
        assertThat(transactions.statusAtCommit).isEqualTo(1);
        assertThat(cache.get(10L)).isNull();
        front.auth("existing-token", "/api/front/user/bookshelf");
        assertThat(UserHolder.getUserId()).isEqualTo(10L);
        assertThat(users.getUser(10L).getStatus()).isZero();
    }

    @Test
    void staleNormalCacheCannotBypassBan() {
        cache.put(10L, UserInfoDto.builder().id(10L).status(0).build());
        UserInfo banned = new UserInfo();
        banned.setId(10L);
        banned.setStatus(1);
        when(userInfoMapper.selectById(10L)).thenReturn(banned);
        when(jwtUtils.parseToken("existing-token", SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn(10L);

        assertThatThrownBy(() -> new FrontAuthStrategy(jwtUtils, users)
            .auth("existing-token", "/api/front/user/bookshelf"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_ACCOUNT_BANNED));
        assertThat(UserHolder.getUserId()).isNull();
        assertThat(cachedStatus()).isZero(); // Deliberately stale; authentication must ignore it.
    }

    @Test
    void staleBannedCacheCannotBlockUnbannedUser() {
        cache.put(10L, UserInfoDto.builder().id(10L).status(1).build());
        UserInfo normal = new UserInfo();
        normal.setId(10L);
        normal.setStatus(0);
        when(userInfoMapper.selectById(10L)).thenReturn(normal);
        when(jwtUtils.parseToken("existing-token", SystemConfigConsts.NOVEL_FRONT_KEY)).thenReturn(10L);

        new FrontAuthStrategy(jwtUtils, users).auth("existing-token", "/api/front/user/bookshelf");

        assertThat(UserHolder.getUserId()).isEqualTo(10L);
        assertThat(cachedStatus()).isEqualTo(1);
    }

    @Test
    void cacheEvictionIsDiscardedOnRollback() {
        cache.put(10L, UserInfoDto.builder().id(10L).status(0).build());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            users.evictUserInfoCache(10L);
            assertThat(cachedStatus()).isZero();
            tx.setRollbackOnly();
        });
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(cachedStatus()).isZero();
    }

    @Test
    void failedUpdateDoesNotEvictCacheOrReportSuccess() {
        cache.put(10L, UserInfoDto.builder().id(10L).status(0).build());
        when(userInfoMapper.updateById(any(UserInfo.class))).thenThrow(new IllegalStateException("db failure"));
        assertThatThrownBy(() -> admin.updateUserStatus(10L, status(1)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(cachedStatus()).isZero();
    }

    @Test
    void missingUserDoesNotReportSuccessfulBan() {
        assertThatThrownBy(() -> admin.updateUserStatus(999L, status(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 2})
    void invalidUserStatusIsRejected(Integer status) {
        assertThatThrownBy(() -> admin.updateUserStatus(10L, status(status)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCodeEnum()).isEqualTo(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
    }

    @Test
    void nonexistentUserIsNotCachedAsNull() {
        assertThat(users.getUser(999L)).isNull();
        assertThat(cache.get(999L)).isNull();
    }

    private int cachedStatus() {
        return cache.get(10L, UserInfoDto.class).getStatus();
    }

    private AdminStatusReqDto status(Integer value) {
        AdminStatusReqDto dto = new AdminStatusReqDto();
        dto.setStatus(value);
        return dto;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @EnableTransactionManagement
    static class Config { }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final Cache cache;
        private Integer statusAtCommit;
        private int rollbacks;

        TestTransactionManager(Cache cache) {
            this.cache = cache;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            UserInfoDto current = cache.get(10L, UserInfoDto.class);
            statusAtCommit = current == null ? null : current.getStatus();
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
