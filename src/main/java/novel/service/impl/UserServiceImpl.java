package novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import novel.core.common.constant.CommonConsts;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.DatabaseConsts;
import novel.core.constant.SystemConfigConsts;
import novel.core.util.BCryptPasswordHelper;
import novel.core.util.JwtUtils;
import novel.dao.entity.UserBookshelf;
import novel.dao.entity.UserFeedback;
import novel.dao.entity.UserInfo;
import novel.dao.entity.BookInfo;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import novel.dao.mapper.UserFeedbackMapper;
import novel.dao.mapper.UserInfoMapper;
import novel.dto.req.UserInfoUptReqDto;
import novel.dto.req.UserLoginReqDto;
import novel.dto.req.UserRegisterReqDto;
import novel.dto.resp.UserInfoRespDto;
import novel.dto.resp.UserLoginRespDto;
import novel.dto.resp.UserRegisterRespDto;
import novel.dto.resp.UserFeedbackRespDto;
import novel.dto.resp.BookInfoRespDto;
import novel.manager.redis.VerifyCodeManager;
import novel.service.UserService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

/**
 * 会员模块 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/17
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserInfoMapper userInfoMapper;

    private final VerifyCodeManager verifyCodeManager;

    private final UserFeedbackMapper userFeedbackMapper;

    private final UserBookshelfMapper userBookshelfMapper;

    private final BookInfoMapper bookInfoMapper;

    private final JwtUtils jwtUtils;

    private final BCryptPasswordHelper bCryptPasswordHelper;

    @Override
    public RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto) {

        // 校验图形验证码是否正确
        if (!verifyCodeManager.imgVerifyCodeOk(dto.getSessionId(), dto.getVelCode())) {
            // 图形验证码校验失败
            throw new BusinessException(ErrorCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 校验手机号是否已注册
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserInfoTable.COLUMN_USERNAME, dto.getUsername())
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        if (userInfoMapper.selectCount(queryWrapper) > 0) {
            // 手机号已注册
            throw new BusinessException(ErrorCodeEnum.USER_NAME_EXIST);
        }

        // 注册成功，保存用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(
            //DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8)
            bCryptPasswordHelper.encode((dto.getPassword()))
                );
        userInfo.setUsername(dto.getUsername());
        userInfo.setNickName(dto.getUsername());
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setSalt("");
        userInfoMapper.insert(userInfo);

        // 删除验证码
        verifyCodeManager.removeImgVerifyCode(dto.getSessionId());

        // 生成JWT 并返回
        return RestResp.ok(
            UserRegisterRespDto.builder()
                .token(jwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY))
                .uid(userInfo.getId())
                .build()
        );

    }

    @Override
    public RestResp<UserLoginRespDto> login(UserLoginReqDto dto) {
        // 查询用户信息
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserInfoTable.COLUMN_USERNAME, dto.getUsername())
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        UserInfo userInfo = userInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(userInfo)) {
            // 用户不存在
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }

        // 判断密码是否正确

        // if (!Objects.equals(userInfo.getPassword(),
        //       DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8)))
        //     {
        // 密码错误
        //    throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);

        boolean isPass = bCryptPasswordHelper.matches(dto.getPassword(), userInfo.getPassword());
        if (!isPass) {
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }


        if (Objects.equals(userInfo.getStatus(), DatabaseConsts.UserInfoTable.STATUS_BANNED)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_BANNED);
        }

        // 登录成功，生成JWT并返回
        return RestResp.ok(UserLoginRespDto.builder()
            .token(jwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY))
            .uid(userInfo.getId())
            .nickName(userInfo.getNickName()).build());
    }

    @Override
    public RestResp<Void> saveFeedback(Long userId, String content) {
        UserFeedback userFeedback = new UserFeedback();
        userFeedback.setUserId(userId);
        userFeedback.setContent(content);
        userFeedback.setCreateTime(LocalDateTime.now());
        userFeedback.setUpdateTime(LocalDateTime.now());
        userFeedbackMapper.insert(userFeedback);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<UserFeedbackRespDto>> listFeedbacks(Long userId,
        PageReqDto pageReqDto) {
        Page<UserFeedback> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<UserFeedback> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserFeedBackTable.COLUMN_USER_ID, userId)
            .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName())
            .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        IPage<UserFeedback> feedbackPage = userFeedbackMapper.selectPage(page, queryWrapper);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(),
            feedbackPage.getTotal(), feedbackPage.getRecords().stream()
                .map(feedback -> UserFeedbackRespDto.builder()
                    .id(feedback.getId())
                    .content(feedback.getContent())
                    .createTime(feedback.getCreateTime())
                    .build())
                .toList()));
    }

    @Override
    public RestResp<Void> updateUserInfo(UserInfoUptReqDto dto) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(dto.getUserId());
        userInfo.setNickName(dto.getNickName());
        userInfo.setUserPhoto(dto.getUserPhoto());
        userInfo.setUserSex(dto.getUserSex());
        userInfoMapper.updateById(userInfo);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteFeedback(Long userId, Long id) {
        QueryWrapper<UserFeedback> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), id)
            .eq(DatabaseConsts.UserFeedBackTable.COLUMN_USER_ID, userId);
        userFeedbackMapper.delete(queryWrapper);
        return RestResp.ok();
    }

    @Override
    public RestResp<Integer> getBookshelfStatus(Long userId, String bookId) {
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
            .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        return RestResp.ok(
            userBookshelfMapper.selectCount(queryWrapper) > 0
                ? CommonConsts.YES
                : CommonConsts.NO
        );
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> addBookshelf(Long userId, Long bookId) {
        if (Objects.isNull(bookInfoMapper.selectById(bookId))) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        QueryWrapper<UserBookshelf> shelfQueryWrapper = new QueryWrapper<>();
        shelfQueryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
            .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        if (userBookshelfMapper.selectCount(shelfQueryWrapper) > 0) {
            throw new BusinessException(ErrorCodeEnum.BOOK_ALREADY_IN_SHELF);
        }
        LocalDateTime now = LocalDateTime.now();
        UserBookshelf userBookshelf = new UserBookshelf();
        userBookshelf.setUserId(userId);
        userBookshelf.setBookId(bookId);
        userBookshelf.setCreateTime(now);
        userBookshelf.setUpdateTime(now);
        userBookshelfMapper.insert(userBookshelf);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> listBookshelf(Long userId,
        PageReqDto pageReqDto) {
        Page<UserBookshelf> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
            .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        IPage<UserBookshelf> bookshelfPage = userBookshelfMapper.selectPage(page, queryWrapper);
        List<UserBookshelf> bookshelfList = bookshelfPage.getRecords();
        if (bookshelfList.isEmpty()) {
            return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(),
                0L, List.of()));
        }
        List<Long> bookIds = bookshelfList.stream().map(UserBookshelf::getBookId).toList();
        Map<Long, BookInfo> bookMap = bookInfoMapper.selectBatchIds(bookIds).stream()
            .collect(Collectors.toMap(BookInfo::getId, Function.identity()));
        List<BookInfoRespDto> books = bookshelfList.stream()
            .filter(shelf -> bookMap.containsKey(shelf.getBookId()))
            .map(shelf -> {
                BookInfo book = bookMap.get(shelf.getBookId());
                return BookInfoRespDto.builder()
                    .id(book.getId())
                    .bookName(book.getBookName())
                    .picUrl(book.getPicUrl())
                    .authorName(book.getAuthorName())
                    .categoryName(book.getCategoryName())
                    .wordCount(book.getWordCount())
                    .visitCount(book.getVisitCount())
                    .lastChapterName(book.getLastChapterName())
                    .updateTime(book.getUpdateTime())
                    .build();
            })
            .toList();
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(),
            bookshelfPage.getTotal(), books));
    }

    @Override
    public RestResp<Void> removeBookshelf(Long userId, Long bookId) {
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
            .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        userBookshelfMapper.delete(queryWrapper);
        return RestResp.ok();
    }

    @Override
    public RestResp<UserInfoRespDto> getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        return RestResp.ok(UserInfoRespDto.builder()
            .nickName(userInfo.getNickName())
            .userSex(userInfo.getUserSex())
            .userPhoto(userInfo.getUserPhoto())
            .build());
    }
}
