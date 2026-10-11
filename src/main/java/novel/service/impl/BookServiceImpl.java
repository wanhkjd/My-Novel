package novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import novel.core.annotation.Key;
import novel.core.annotation.Lock;
import novel.core.auth.UserHolder;
import novel.core.common.constant.CommonConsts;
import novel.core.common.constant.ErrorCodeEnum;
import novel.core.common.exception.BusinessException;
import novel.core.common.req.PageReqDto;
import novel.core.common.resp.PageRespDto;
import novel.core.common.resp.RestResp;
import novel.core.constant.DatabaseConsts;
import novel.dao.entity.*;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookCommentMapper;
import novel.dao.mapper.BookContentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.AuthorInfoDto;
import novel.dto.req.BookAddReqDto;
import novel.dto.req.ChapterAddReqDto;
import novel.dto.req.ChapterUpdateReqDto;
import novel.dto.req.UserCommentReqDto;
import novel.dto.resp.*;
import novel.manager.cache.*;
import novel.manager.dao.UserDaoManager;
import novel.manager.mq.AmqpMsgManager;
import novel.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 小说模块 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {

    private final BookCategoryCacheManager bookCategoryCacheManager;

    private final BookRankCacheManager bookRankCacheManager;

    private final BookInfoCacheManager bookInfoCacheManager;

    private final BookChapterCacheManager bookChapterCacheManager;

    private final BookContentCacheManager bookContentCacheManager;

    private final AuthorInfoCacheManager authorInfoCacheManager;

    private final BookInfoMapper bookInfoMapper;

    private final BookChapterMapper bookChapterMapper;

    private final BookContentMapper bookContentMapper;

    private final BookCommentMapper bookCommentMapper;

    private final UserDaoManager userDaoManager;

    private final AmqpMsgManager amqpMsgManager;

    private static final Integer REC_BOOK_COUNT = 4;

    private static final Pattern COMMENT_PHONE_PATTERN = Pattern.compile("1[3-9][0-9]{9}");

    @Override
    public RestResp<List<BookRankRespDto>> listVisitRankBooks() {
        return RestResp.ok(bookRankCacheManager.listVisitRankBooks());
    }

    @Override
    public RestResp<List<BookRankRespDto>> listNewestRankBooks() {
        return RestResp.ok(bookRankCacheManager.listNewestRankBooks());
    }

    @Override
    public RestResp<List<BookRankRespDto>> listUpdateRankBooks() {
        return RestResp.ok(bookRankCacheManager.listUpdateRankBooks());
    }

    @Override
    public RestResp<BookInfoRespDto> getBookById(Long bookId) {
        return RestResp.ok(bookInfoCacheManager.getBookInfo(bookId));
    }

    @Override
    public RestResp<BookChapterAboutRespDto> getLastChapterAbout(Long bookId) {
        // 查询小说信息
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookId);

        // 查询最新章节信息
        BookChapterRespDto bookChapter = bookChapterCacheManager.getChapter(
            bookInfo.getLastChapterId());

        // 查询章节内容
        String content = bookContentCacheManager.getBookContent(bookInfo.getLastChapterId());

        // 查询章节总数
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId);
        Long chapterTotal = bookChapterMapper.selectCount(chapterQueryWrapper);

        // 组装数据并返回
        return RestResp.ok(BookChapterAboutRespDto.builder()
            .chapterInfo(bookChapter)
            .chapterTotal(chapterTotal)
            .contentSummary(content != null ? (content.length() <= 30 ? content : content.substring(0, 30)) : "")
            .build());
    }

    @Override
    public RestResp<List<BookInfoRespDto>> listRecBooks(Long bookId)
        throws NoSuchAlgorithmException {
        Long categoryId = bookInfoCacheManager.getBookInfo(bookId).getCategoryId();
        List<Long> lastUpdateIdList = bookInfoCacheManager.getLastUpdateIdList(categoryId);

        // 检查列表是否为空或不足
        if (CollectionUtils.isEmpty(lastUpdateIdList)) {
            return RestResp.ok(Collections.emptyList());
        }

        // 排除当前书籍，同时确保有足够的推荐书籍用于展示
        List<Long> candidateIdList = lastUpdateIdList.stream()
                .filter(id -> !Objects.equals(id, bookId))
                .toList();

        if (candidateIdList.isEmpty()) {
            return RestResp.ok(Collections.emptyList());
        }

        // 确定实际推荐的书籍数量
        int actualRecCount = Math.min(REC_BOOK_COUNT, candidateIdList.size());

        List<BookInfoRespDto> respDtoList = new ArrayList<>();
        Set<Integer> recIdIndexSet = new HashSet<>();
        Random rand = SecureRandom.getInstanceStrong();

        // 使用 Set 提高查找效率，同时修复bug防止无限循环
        while (respDtoList.size() < actualRecCount && recIdIndexSet.size() < candidateIdList.size()) {
            int recIdIndex = rand.nextInt(candidateIdList.size());
            if (!recIdIndexSet.contains(recIdIndex)) {
                recIdIndexSet.add(recIdIndex);
                Long recBookId = candidateIdList.get(recIdIndex);
                BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(recBookId);
                respDtoList.add(bookInfo);
            }
        }

        return RestResp.ok(respDtoList);
    }

    @Override
    public RestResp<Void> addVisitCount(Long bookId) {
        bookInfoMapper.addVisitCount(bookId);
        return RestResp.ok();
    }

    @Override
    public RestResp<Long> getPreChapterId(Long chapterId) {
        // 查询小说ID 和 章节号
        BookChapterRespDto chapter = bookChapterCacheManager.getChapter(chapterId);
        Long bookId = chapter.getBookId();
        Integer chapterNum = chapter.getChapterNum();

        // 查询上一章信息并返回章节ID
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
            .lt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
            .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
            Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                .map(BookChapter::getId)
                .orElse(null)
        );
    }

    @Override
    public RestResp<Long> getNextChapterId(Long chapterId) {
        // 查询小说ID 和 章节号
        BookChapterRespDto chapter = bookChapterCacheManager.getChapter(chapterId);
        Long bookId = chapter.getBookId();
        Integer chapterNum = chapter.getChapterNum();

        // 查询下一章信息并返回章节ID
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
            .gt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
            .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
            Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                .map(BookChapter::getId)
                .orElse(null)
        );
    }

    @Override
    public RestResp<List<BookChapterRespDto>> listChapters(Long bookId) {
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
            .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM);
        return RestResp.ok(bookChapterMapper.selectList(queryWrapper).stream()
            .map(v -> BookChapterRespDto.builder()
                .id(v.getId())
                .chapterName(v.getChapterName())
                .isVip(v.getIsVip())
                .build()).toList());
    }

    @Override
    public RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection) {
        return RestResp.ok(bookCategoryCacheManager.listCategory(workDirection));
    }

    @Lock(prefix = "userComment")
    @Override
    public RestResp<Void> saveComment(
        @Key(expr = "#{userId + '::' + bookId}") UserCommentReqDto dto) {
        // 校验书籍是否存在
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
            return RestResp.fail(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        // 校验用户是否已发表评论
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, dto.getUserId())
            .eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, dto.getBookId());
        if (bookCommentMapper.selectCount(queryWrapper) > 0) {
            // 用户已发表评论
            return RestResp.fail(ErrorCodeEnum.USER_COMMENTED);
        }
        BookComment bookComment = new BookComment();
        bookComment.setBookId(dto.getBookId());
        bookComment.setUserId(dto.getUserId());
        bookComment.setCommentContent(dto.getCommentContent());
        bookComment.setCreateTime(LocalDateTime.now());
        bookComment.setUpdateTime(LocalDateTime.now());
        bookCommentMapper.insert(bookComment);
        return RestResp.ok();
    }

    @Override
    public RestResp<BookCommentRespDto> listNewestComments(Long bookId) {
        // 查询评论总数
        QueryWrapper<BookComment> commentCountQueryWrapper = new QueryWrapper<>();
        commentCountQueryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, bookId);
        commentCountQueryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_AUDIT_STATUS,
            CommonConsts.YES);
        Long commentTotal = bookCommentMapper.selectCount(commentCountQueryWrapper);
        BookCommentRespDto bookCommentRespDto = BookCommentRespDto.builder()
            .commentTotal(commentTotal).build();
        if (commentTotal > 0) {

            // 查询最新的评论列表
            QueryWrapper<BookComment> commentQueryWrapper = new QueryWrapper<>();
            commentQueryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.BookCommentTable.COLUMN_AUDIT_STATUS, CommonConsts.YES)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName())
                .last(DatabaseConsts.SqlEnum.LIMIT_5.getSql());
            List<BookComment> bookComments = bookCommentMapper.selectList(commentQueryWrapper);

            // 组装安全的公开身份，不把登录账号交给响应 DTO。
            List<Long> userIds = bookComments.stream().map(BookComment::getUserId).toList();
            List<UserInfo> userInfos = userDaoManager.listUsers(userIds);
            Map<Long, UserInfo> userInfoMap = userInfos.stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
            List<BookCommentRespDto.CommentInfo> commentInfos = bookComments.stream()
                .map(v -> {
                    UserInfo user = userInfoMap.get(v.getUserId());
                    return BookCommentRespDto.CommentInfo.builder()
                        .id(v.getId())
                        .commentUserId(v.getUserId())
                        .commentUser(getPublicCommentName(user))
                        .commentUserPhoto(Objects.isNull(user) ? null : user.getUserPhoto())
                        .commentContent(v.getCommentContent())
                        .commentTime(v.getCreateTime()).build();
                }).toList();
            bookCommentRespDto.setComments(commentInfos);
        } else {
            bookCommentRespDto.setComments(Collections.emptyList());
        }
        return RestResp.ok(bookCommentRespDto);
    }

    /**
     * 评论只展示昵称；注册时默认复制的账号及历史手机号昵称使用非敏感占位值。
     */
    private String getPublicCommentName(UserInfo user) {
        if (Objects.isNull(user) || !StringUtils.hasText(user.getNickName())) {
            return "书友";
        }
        String nickname = user.getNickName().strip();
        String username = user.getUsername();
        if ((StringUtils.hasText(username) && nickname.contains(username.strip()))
            || COMMENT_PHONE_PATTERN.matcher(nickname).find()) {
            return "书友";
        }
        return nickname;
    }

    @Override
    public RestResp<Void> deleteComment(Long userId, Long commentId) {
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), commentId)
            .eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, userId);
        bookCommentMapper.delete(queryWrapper);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateComment(Long userId, Long id, String content) {
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), id)
            .eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, userId);
        BookComment bookComment = new BookComment();
        bookComment.setCommentContent(content);
        bookComment.setAuditStatus(CommonConsts.NO);
        bookCommentMapper.update(bookComment, queryWrapper);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> saveBook(BookAddReqDto dto) {
        // 校验小说名是否已存在
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_BOOK_NAME, dto.getBookName());
        if (bookInfoMapper.selectCount(queryWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.AUTHOR_BOOK_NAME_EXIST);
        }
        boolean categoryValid = bookCategoryCacheManager.listCategory(dto.getWorkDirection())
            .stream()
            .anyMatch(category -> Objects.equals(category.getId(), dto.getCategoryId())
                && Objects.equals(category.getName(), dto.getCategoryName()));
        if (!categoryValid) {
            return RestResp.fail(ErrorCodeEnum.BOOK_CATEGORY_INVALID);
        }
        BookInfo bookInfo = new BookInfo();
        // 设置作家信息
        AuthorInfoDto author = authorInfoCacheManager.getAuthor(UserHolder.getUserId());
        bookInfo.setAuthorId(author.getId());
        bookInfo.setAuthorName(author.getPenName());
        // 设置其他信息
        bookInfo.setWorkDirection(dto.getWorkDirection());
        bookInfo.setCategoryId(dto.getCategoryId());
        bookInfo.setCategoryName(dto.getCategoryName());
        bookInfo.setBookName(dto.getBookName());
        bookInfo.setPicUrl(dto.getPicUrl());
        bookInfo.setBookDesc(dto.getBookDesc());
        bookInfo.setIsVip(dto.getIsVip());
        bookInfo.setScore(0);
        bookInfo.setCreateTime(LocalDateTime.now());
        bookInfo.setUpdateTime(LocalDateTime.now());
        // 保存小说信息
        bookInfoMapper.insert(bookInfo);
        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> saveBookChapter(ChapterAddReqDto dto) {
        // 所有章节写入先锁定同一本书，持锁到事务提交/回滚。
        BookInfo bookInfo = lockBookForChapterWrite(dto.getBookId());
        // 1) 保存章节相关信息到小说章节表
        //  a) 查询最新章节号
        int chapterNum = 0;
        BookChapter bookChapter = bookChapterMapper.selectLastByBookIdForUpdate(dto.getBookId());
        if (Objects.nonNull(bookChapter)) {
            chapterNum = bookChapter.getChapterNum() + 1;
        }
        LocalDateTime now = LocalDateTime.now();
        //  b) 设置章节相关信息并保存
        BookChapter newBookChapter = new BookChapter();
        newBookChapter.setBookId(dto.getBookId());
        newBookChapter.setChapterName(dto.getChapterName());
        newBookChapter.setChapterNum(chapterNum);
        newBookChapter.setWordCount(dto.getChapterContent().length());
        newBookChapter.setIsVip(dto.getIsVip());
        newBookChapter.setCreateTime(now);
        newBookChapter.setUpdateTime(now);
        bookChapterMapper.insert(newBookChapter);

        // 2) 保存章节内容到小说内容表
        BookContent bookContent = new BookContent();
        bookContent.setContent(dto.getChapterContent());
        bookContent.setChapterId(newBookChapter.getId());
        bookContent.setCreateTime(now);
        bookContent.setUpdateTime(now);
        bookContentMapper.insert(bookContent);

        // 3) 更新小说表最新章节信息和小说总字数信息
        //  a) 更新小说表关于最新章节的信息
        BookInfo newBookInfo = new BookInfo();
        newBookInfo.setId(dto.getBookId());
        newBookInfo.setLastChapterId(newBookChapter.getId());
        newBookInfo.setLastChapterName(newBookChapter.getChapterName());
        newBookInfo.setLastChapterUpdateTime(now);
        newBookInfo.setWordCount(bookInfo.getWordCount() + newBookChapter.getWordCount());
        newBookInfo.setUpdateTime(now);
        bookInfoMapper.updateById(newBookInfo);
        // 详情直接读取数据库；MQ 管理器在事务成功提交后发送通知。
        amqpMsgManager.sendBookChangeMsg(dto.getBookId());
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> listAuthorBooks(PageReqDto dto) {
        IPage<BookInfo> page = new Page<>();
        page.setCurrent(dto.getPageNum());
        page.setSize(dto.getPageSize());
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.AUTHOR_ID, UserHolder.getAuthorId())
            .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        IPage<BookInfo> bookInfoPage = bookInfoMapper.selectPage(page, queryWrapper);
        return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), page.getTotal(),
            bookInfoPage.getRecords().stream().map(v -> BookInfoRespDto.builder()
                .id(v.getId())
                .bookName(v.getBookName())
                .picUrl(v.getPicUrl())
                .categoryName(v.getCategoryName())
                .wordCount(v.getWordCount())
                .visitCount(v.getVisitCount())
                .updateTime(v.getUpdateTime())
                .build()).toList()));
    }

    @Override
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(Long bookId, PageReqDto dto) {
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (Objects.isNull(bookInfo)
            || !Objects.equals(bookInfo.getAuthorId(), UserHolder.getAuthorId())) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        IPage<BookChapter> page = new Page<>();
        page.setCurrent(dto.getPageNum());
        page.setSize(dto.getPageSize());
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
            .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM);
        IPage<BookChapter> bookChapterPage = bookChapterMapper.selectPage(page, queryWrapper);
        return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), page.getTotal(),
            bookChapterPage.getRecords().stream().map(v -> BookChapterRespDto.builder()
                .id(v.getId())
                .chapterName(v.getChapterName())
                .chapterUpdateTime(v.getUpdateTime())
                .isVip(v.getIsVip())
                .build()).toList()));
    }

    @Override
    public RestResp<PageRespDto<UserCommentRespDto>> listComments(Long userId, PageReqDto pageReqDto) {
        IPage<BookComment> page = new Page<>();
        page.setCurrent(pageReqDto.getPageNum());
        page.setSize(pageReqDto.getPageSize());
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, userId)
            .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        IPage<BookComment> bookCommentPage = bookCommentMapper.selectPage(page, queryWrapper);
        List<BookComment> comments = bookCommentPage.getRecords();
        
        List<UserCommentRespDto> commentRespDtoList = Collections.emptyList();
        if (!CollectionUtils.isEmpty(comments)) {
            List<Long> bookIds = comments.stream().map(BookComment::getBookId).toList();
            QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
            bookInfoQueryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
            Map<Long, BookInfo> bookInfoMap = bookInfoMapper.selectList(bookInfoQueryWrapper).stream()
                .collect(Collectors.toMap(BookInfo::getId, Function.identity()));
            
            commentRespDtoList = comments.stream().map(v -> {
                BookInfo bookInfo = bookInfoMap.get(v.getBookId());
                return UserCommentRespDto.builder()
                    .id(v.getId())
                    .commentContent(v.getCommentContent())
                    .commentBook(bookInfo != null ? bookInfo.getBookName() : null)
                    .commentBookPic(bookInfo != null ? bookInfo.getPicUrl() : null)
                    .commentTime(v.getCreateTime())
                    .auditStatus(v.getAuditStatus())
                    .build();
            }).toList();
        }
        
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), 
            page.getTotal(), commentRespDtoList));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> deleteBookChapter(Long chapterId) {
        ChapterWriteState state = lockChapterForWrite(chapterId);
        BookChapter chapter = state.chapter();
        BookInfo bookInfo = state.book();
        bookChapterMapper.deleteById(chapterId);
        QueryWrapper<BookContent> contentQuery = new QueryWrapper<>();
        contentQuery.eq(DatabaseConsts.BookContentTable.COLUMN_CHAPTER_ID, chapterId);
        bookContentMapper.delete(contentQuery);

        // Wrapper 显式 SET NULL，避免 updateById 默认跳过空值而保留旧的末章时间。
        LambdaUpdateWrapper<BookInfo> update = new LambdaUpdateWrapper<BookInfo>()
            .eq(BookInfo::getId, bookInfo.getId())
            .set(BookInfo::getWordCount, bookInfo.getWordCount() - chapter.getWordCount())
            .set(BookInfo::getUpdateTime, LocalDateTime.now());
        if (Objects.equals(bookInfo.getLastChapterId(), chapterId)) {
            BookChapter lastChapter = bookChapterMapper.selectLastByBookIdForUpdate(bookInfo.getId());
            update.set(BookInfo::getLastChapterId, lastChapter == null ? 0L : lastChapter.getId())
                .set(BookInfo::getLastChapterName, lastChapter == null ? "" : lastChapter.getChapterName())
                .set(BookInfo::getLastChapterUpdateTime,
                    lastChapter == null ? null : lastChapter.getUpdateTime());
        }
        bookInfoMapper.update(null, update);
        amqpMsgManager.sendBookChangeMsg(bookInfo.getId());
        return RestResp.ok();
    }

    @Override
    public RestResp<ChapterContentRespDto> getBookChapter(Long chapterId) {
        BookChapterRespDto chapter = bookChapterCacheManager.getChapter(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(chapter.getBookId());
        if (bookInfo == null) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        if (UserHolder.getAuthorId() == null
            || !Objects.equals(bookInfo.getAuthorId(), UserHolder.getAuthorId())) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        String bookContent = bookContentCacheManager.getBookContent(chapterId);
        return RestResp.ok(
            ChapterContentRespDto.builder()
                .chapterName(chapter.getChapterName())
                .chapterContent(bookContent)
                .isVip(chapter.getIsVip())
                .build());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> updateBookChapter(Long chapterId, ChapterUpdateReqDto dto) {
        ChapterWriteState state = lockChapterForWrite(chapterId);
        BookChapter chapter = state.chapter();
        BookInfo bookInfo = state.book();
        LocalDateTime now = LocalDateTime.now();
        BookChapter newChapter = new BookChapter();
        newChapter.setId(chapterId);
        newChapter.setChapterName(dto.getChapterName());
        newChapter.setWordCount(dto.getChapterContent().length());
        newChapter.setIsVip(dto.getIsVip());
        newChapter.setUpdateTime(now);
        bookChapterMapper.updateById(newChapter);

        BookContent newContent = new BookContent();
        newContent.setContent(dto.getChapterContent());
        newContent.setUpdateTime(now);
        QueryWrapper<BookContent> contentQuery = new QueryWrapper<>();
        contentQuery.eq(DatabaseConsts.BookContentTable.COLUMN_CHAPTER_ID, chapterId);
        if (bookContentMapper.update(newContent, contentQuery) != 1) {
            // 正文缺失时回滚刚才的章节更新，不留下字数与正文不一致的半次修改。
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }

        BookInfo newBookInfo = new BookInfo();
        newBookInfo.setId(bookInfo.getId());
        newBookInfo.setUpdateTime(now);
        newBookInfo.setWordCount(
            bookInfo.getWordCount() - chapter.getWordCount() + dto.getChapterContent().length());
        if (Objects.equals(bookInfo.getLastChapterId(), chapterId)) {
            newBookInfo.setLastChapterName(dto.getChapterName());
            newBookInfo.setLastChapterUpdateTime(now);
        }
        bookInfoMapper.updateById(newBookInfo);
        amqpMsgManager.sendBookChangeMsg(bookInfo.getId());
        return RestResp.ok();
    }

    /**
     * 以书籍行为跨实例的写入互斥点。禁止用缓存或普通快照读计算章节派生字段。
     */
    private BookInfo lockBookForChapterWrite(Long bookId) {
        if (UserHolder.getAuthorId() == null) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        BookInfo book = bookInfoMapper.selectByIdForUpdate(bookId);
        if (book == null) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        if (!Objects.equals(book.getAuthorId(), UserHolder.getAuthorId())) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        return book;
    }

    private ChapterWriteState lockChapterForWrite(Long chapterId) {
        // 第一次普通读仅定位不变的 bookId，不以此快照计算字数，也不先锁章节。
        BookChapter locator = bookChapterMapper.selectById(chapterId);
        if (locator == null) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        BookInfo book = lockBookForChapterWrite(locator.getBookId());
        // 等待书锁期间章节可能已被修改/删除；FOR UPDATE 在 MySQL RR 下仍是当前读。
        BookChapter chapter = bookChapterMapper.selectByIdForUpdate(chapterId);
        if (chapter == null || !Objects.equals(chapter.getBookId(), book.getId())) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }
        return new ChapterWriteState(book, chapter);
    }

    private record ChapterWriteState(BookInfo book, BookChapter chapter) {
    }

    @Override
    public RestResp<BookContentAboutRespDto> getBookContentAbout(Long chapterId) {
        Long userId = UserHolder.getUserId();
        log.debug("userId:{}", userId);
        // 查询章节信息
        BookChapterRespDto bookChapter = bookChapterCacheManager.getChapter(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.BOOK_NOT_FOUND);
        }

        // 查询章节内容
        String content = bookContentCacheManager.getBookContent(chapterId);

        // VIP 章节鉴权：若为付费章节且未登录，限制仅可查看试读内容
        if (Objects.equals(bookChapter.getIsVip(), 1) && Objects.isNull(userId)) {
            if (content != null) {
                int previewLen = Math.min(200, content.length());
                content = content.substring(0, previewLen) + "\n\n【该章节为 VIP 付费内容，请登录并购买后阅读完整内容】";
            }
        }

        // 查询小说信息
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        // 组装数据并返回
        return RestResp.ok(BookContentAboutRespDto.builder()
            .bookInfo(bookInfo)
            .chapterInfo(bookChapter)
            .bookContent(content)
            .build());
    }
}
