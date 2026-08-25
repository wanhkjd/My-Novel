package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.req.PageReqDto;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.SystemConfigConsts;
import io.github.xxyopen.novel.core.util.JwtUtils;
import io.github.xxyopen.novel.dao.entity.AuthorInfo;
import io.github.xxyopen.novel.dao.entity.BookCategory;
import io.github.xxyopen.novel.dao.entity.BookComment;
import io.github.xxyopen.novel.dao.entity.BookInfo;
import io.github.xxyopen.novel.dao.entity.HomeBook;
import io.github.xxyopen.novel.dao.entity.HomeFriendLink;
import io.github.xxyopen.novel.dao.entity.NewsContent;
import io.github.xxyopen.novel.dao.entity.NewsInfo;
import io.github.xxyopen.novel.dao.entity.SysMenu;
import io.github.xxyopen.novel.dao.entity.SysUser;
import io.github.xxyopen.novel.dao.entity.UserInfo;
import io.github.xxyopen.novel.dao.mapper.AuthorInfoMapper;
import io.github.xxyopen.novel.dao.mapper.BookCategoryMapper;
import io.github.xxyopen.novel.dao.mapper.BookCommentMapper;
import io.github.xxyopen.novel.dao.mapper.BookInfoMapper;
import io.github.xxyopen.novel.dao.mapper.HomeBookMapper;
import io.github.xxyopen.novel.dao.mapper.HomeFriendLinkMapper;
import io.github.xxyopen.novel.dao.mapper.NewsContentMapper;
import io.github.xxyopen.novel.dao.mapper.NewsInfoMapper;
import io.github.xxyopen.novel.dao.mapper.SysMenuMapper;
import io.github.xxyopen.novel.dao.mapper.SysUserMapper;
import io.github.xxyopen.novel.dao.mapper.UserInfoMapper;
import io.github.xxyopen.novel.dto.req.AdminAuditReqDto;
import io.github.xxyopen.novel.dto.req.AdminBookCategoryReqDto;
import io.github.xxyopen.novel.dto.req.AdminFriendLinkReqDto;
import io.github.xxyopen.novel.dto.req.AdminHomeBookReqDto;
import io.github.xxyopen.novel.dto.req.AdminLoginReqDto;
import io.github.xxyopen.novel.dto.req.AdminNewsReqDto;
import io.github.xxyopen.novel.dto.req.AdminStatusReqDto;
import io.github.xxyopen.novel.dto.resp.AdminLoginRespDto;
import io.github.xxyopen.novel.service.AdminService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SysUserMapper sysUserMapper;

    private final SysMenuMapper sysMenuMapper;

    private final UserInfoMapper userInfoMapper;

    private final BookInfoMapper bookInfoMapper;

    private final BookCategoryMapper bookCategoryMapper;

    private final BookCommentMapper bookCommentMapper;

    private final HomeBookMapper homeBookMapper;

    private final HomeFriendLinkMapper homeFriendLinkMapper;

    private final AuthorInfoMapper authorInfoMapper;

    private final NewsInfoMapper newsInfoMapper;

    private final NewsContentMapper newsContentMapper;

    private final JwtUtils jwtUtils;

    @Override
    public RestResp<AdminLoginRespDto> login(AdminLoginReqDto dto) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername()).last("limit 1");
        SysUser sysUser = sysUserMapper.selectOne(queryWrapper);
        if (Objects.isNull(sysUser)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
        String password = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!Objects.equals(sysUser.getPassword(), password)) {
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }
        if (Objects.equals(sysUser.getStatus(), 0)) {
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }
        return RestResp.ok(AdminLoginRespDto.builder()
            .token(jwtUtils.generateToken(sysUser.getId(), SystemConfigConsts.NOVEL_ADMIN_KEY))
            .uid(sysUser.getId())
            .username(sysUser.getUsername())
            .nickName(StringUtils.hasText(sysUser.getName()) ? sysUser.getName() : sysUser.getUsername())
            .build());
    }

    @Override
    public RestResp<Map<String, Object>> info(Long adminId) {
        SysUser sysUser = sysUserMapper.selectById(adminId);
        if (Objects.isNull(sysUser)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
        Map<String, Object> data = row();
        data.put("id", sysUser.getId());
        data.put("username", sysUser.getUsername());
        data.put("nickName", StringUtils.hasText(sysUser.getName()) ? sysUser.getName() : sysUser.getUsername());
        data.put("name", sysUser.getName());
        data.put("email", sysUser.getEmail());
        data.put("mobile", sysUser.getMobile());
        data.put("status", sysUser.getStatus());
        return RestResp.ok(data);
    }

    @Override
    public RestResp<List<Map<String, Object>>> menus() {
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        List<SysMenu> menus = sysMenuMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(menus)) {
            return RestResp.ok(defaultMenus());
        }
        Map<Long, List<SysMenu>> childrenMap = menus.stream()
            .collect(Collectors.groupingBy(SysMenu::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = childrenMap.getOrDefault(0L, Collections.emptyList()).stream()
            .map(menu -> toMenu(menu, childrenMap))
            .toList();
        return RestResp.ok(result);
    }

    @Override
    public RestResp<Map<String, Object>> dashboard() {
        Map<String, Object> stats = row();
        stats.put("bookCount", bookInfoMapper.selectCount(new QueryWrapper<>()));
        stats.put("authorCount", authorInfoMapper.selectCount(new QueryWrapper<>()));
        stats.put("userCount", userInfoMapper.selectCount(new QueryWrapper<>()));
        stats.put("commentCount", bookCommentMapper.selectCount(new QueryWrapper<>()));
        stats.put("categoryCount", bookCategoryMapper.selectCount(new QueryWrapper<>()));

        Page<BookInfo> page = new Page<>(1, 5);
        QueryWrapper<BookInfo> bookQuery = new QueryWrapper<>();
        bookQuery.orderByDesc("update_time");
        List<Map<String, Object>> recentBooks = bookInfoMapper.selectPage(page, bookQuery).getRecords().stream()
            .map(this::toBookRow)
            .toList();

        Map<String, Object> todos = row();
        QueryWrapper<BookComment> pendingCommentQuery = new QueryWrapper<>();
        pendingCommentQuery.eq("audit_status", 0);
        todos.put("pendingBooks", 0);
        todos.put("pendingAuthors", 0);
        todos.put("reportedComments", bookCommentMapper.selectCount(pendingCommentQuery));

        Map<String, Object> data = row();
        data.put("stats", stats);
        data.put("recentBooks", recentBooks);
        data.put("todos", todos);
        return RestResp.ok(data);
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listUsers(PageReqDto pageReqDto, String keyword, Integer status) {
        Page<UserInfo> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<UserInfo> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like("username", keyword).or().like("nick_name", keyword));
        }
        if (Objects.nonNull(status)) {
            query.eq("status", status);
        }
        query.orderByDesc("create_time");
        IPage<UserInfo> userPage = userInfoMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), userPage.getTotal(),
            userPage.getRecords().stream().map(this::toUserRow).toList()));
    }

    @Override
    public RestResp<Map<String, Object>> getUser(Long id) {
        UserInfo userInfo = userInfoMapper.selectById(id);
        if (Objects.isNull(userInfo)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
        return RestResp.ok(toUserRow(userInfo));
    }

    @Override
    public RestResp<Void> updateUserStatus(Long id, AdminStatusReqDto dto) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setStatus(dto.getStatus());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateById(userInfo);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listBooks(PageReqDto pageReqDto, String keyword, Integer status) {
        Page<BookInfo> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<BookInfo> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like("book_name", keyword).or().like("author_name", keyword));
        }
        if (Objects.nonNull(status)) {
            query.eq("book_status", status);
        }
        query.orderByDesc("update_time");
        IPage<BookInfo> bookPage = bookInfoMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), bookPage.getTotal(),
            bookPage.getRecords().stream().map(this::toBookRow).toList()));
    }

    @Override
    public RestResp<Void> updateBookStatus(Long id, AdminStatusReqDto dto) {
        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(id);
        bookInfo.setBookStatus(dto.getStatus());
        bookInfo.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(bookInfo);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listCategories(PageReqDto pageReqDto, String keyword) {
        Page<BookCategory> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<BookCategory> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like("name", keyword);
        }
        query.orderByAsc("sort").orderByDesc("create_time");
        IPage<BookCategory> categoryPage = bookCategoryMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), categoryPage.getTotal(),
            categoryPage.getRecords().stream().map(this::toCategoryRow).toList()));
    }

    @Override
    public RestResp<Void> saveCategory(AdminBookCategoryReqDto dto) {
        BookCategory category = new BookCategory();
        fillCategory(category, dto);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        bookCategoryMapper.insert(category);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateCategory(Long id, AdminBookCategoryReqDto dto) {
        BookCategory category = new BookCategory();
        category.setId(id);
        fillCategory(category, dto);
        category.setUpdateTime(LocalDateTime.now());
        bookCategoryMapper.updateById(category);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteCategory(Long id) {
        bookCategoryMapper.deleteById(id);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listComments(PageReqDto pageReqDto, String keyword,
        Integer auditStatus) {
        Page<BookComment> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<BookComment> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like("comment_content", keyword);
        }
        if (Objects.nonNull(auditStatus)) {
            query.eq("audit_status", auditStatus);
        }
        query.orderByDesc("create_time");
        IPage<BookComment> commentPage = bookCommentMapper.selectPage(page, query);
        List<BookComment> comments = commentPage.getRecords();
        Map<Long, BookInfo> bookMap = Collections.emptyMap();
        Map<Long, UserInfo> userMap = Collections.emptyMap();
        if (!CollectionUtils.isEmpty(comments)) {
            List<Long> bookIds = comments.stream().map(BookComment::getBookId).distinct().toList();
            List<Long> userIds = comments.stream().map(BookComment::getUserId).distinct().toList();
            bookMap = bookInfoMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(BookInfo::getId, Function.identity()));
            userMap = userInfoMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        }
        Map<Long, BookInfo> finalBookMap = bookMap;
        Map<Long, UserInfo> finalUserMap = userMap;
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), commentPage.getTotal(),
            comments.stream().map(comment -> toCommentRow(comment, finalBookMap, finalUserMap)).toList()));
    }

    @Override
    public RestResp<Void> auditComment(Long id, AdminAuditReqDto dto) {
        BookComment comment = new BookComment();
        comment.setId(id);
        comment.setAuditStatus(dto.getAuditStatus());
        comment.setUpdateTime(LocalDateTime.now());
        bookCommentMapper.updateById(comment);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteComment(Long id) {
        bookCommentMapper.deleteById(id);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listHomeBooks(PageReqDto pageReqDto) {
        Page<HomeBook> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<HomeBook> query = new QueryWrapper<>();
        query.orderByAsc("sort").orderByDesc("create_time");
        IPage<HomeBook> homeBookPage = homeBookMapper.selectPage(page, query);
        List<HomeBook> homeBooks = homeBookPage.getRecords();
        Map<Long, BookInfo> bookMap = Collections.emptyMap();
        if (!CollectionUtils.isEmpty(homeBooks)) {
            List<Long> bookIds = homeBooks.stream().map(HomeBook::getBookId).distinct().toList();
            bookMap = bookInfoMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(BookInfo::getId, Function.identity()));
        }
        Map<Long, BookInfo> finalBookMap = bookMap;
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), homeBookPage.getTotal(),
            homeBooks.stream().map(homeBook -> toHomeBookRow(homeBook, finalBookMap)).toList()));
    }

    @Override
    public RestResp<Void> saveHomeBook(AdminHomeBookReqDto dto) {
        HomeBook homeBook = new HomeBook();
        fillHomeBook(homeBook, dto);
        homeBook.setCreateTime(LocalDateTime.now());
        homeBook.setUpdateTime(LocalDateTime.now());
        homeBookMapper.insert(homeBook);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateHomeBook(Long id, AdminHomeBookReqDto dto) {
        HomeBook homeBook = new HomeBook();
        homeBook.setId(id);
        fillHomeBook(homeBook, dto);
        homeBook.setUpdateTime(LocalDateTime.now());
        homeBookMapper.updateById(homeBook);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteHomeBook(Long id) {
        homeBookMapper.deleteById(id);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listAuthors(PageReqDto pageReqDto, String keyword,
        Integer status) {
        Page<AuthorInfo> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<AuthorInfo> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like("pen_name", keyword).or().like("tel_phone", keyword));
        }
        if (Objects.nonNull(status)) {
            query.eq("status", status);
        }
        query.orderByDesc("create_time");
        IPage<AuthorInfo> authorPage = authorInfoMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), authorPage.getTotal(),
            authorPage.getRecords().stream().map(this::toAuthorRow).toList()));
    }

    @Override
    public RestResp<Map<String, Object>> getAuthor(Long id) {
        AuthorInfo author = authorInfoMapper.selectById(id);
        if (Objects.isNull(author)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
        return RestResp.ok(toAuthorRow(author));
    }

    @Override
    public RestResp<Void> updateAuthorStatus(Long id, AdminStatusReqDto dto) {
        AuthorInfo author = new AuthorInfo();
        author.setId(id);
        author.setStatus(dto.getStatus());
        author.setUpdateTime(LocalDateTime.now());
        authorInfoMapper.updateById(author);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listNews(PageReqDto pageReqDto, String keyword) {
        Page<NewsInfo> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<NewsInfo> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like("title", keyword).or().like("source_name", keyword));
        }
        query.orderByDesc("update_time");
        IPage<NewsInfo> newsPage = newsInfoMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), newsPage.getTotal(),
            newsPage.getRecords().stream().map(this::toNewsRow).toList()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> saveNews(AdminNewsReqDto dto) {
        NewsInfo newsInfo = new NewsInfo();
        fillNews(newsInfo, dto);
        newsInfo.setCreateTime(LocalDateTime.now());
        newsInfo.setUpdateTime(LocalDateTime.now());
        newsInfoMapper.insert(newsInfo);
        NewsContent content = new NewsContent();
        content.setNewsId(newsInfo.getId());
        content.setContent(dto.getContent());
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());
        newsContentMapper.insert(content);
        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> updateNews(Long id, AdminNewsReqDto dto) {
        NewsInfo newsInfo = new NewsInfo();
        newsInfo.setId(id);
        fillNews(newsInfo, dto);
        newsInfo.setUpdateTime(LocalDateTime.now());
        newsInfoMapper.updateById(newsInfo);
        QueryWrapper<NewsContent> query = new QueryWrapper<>();
        query.eq("news_id", id).last("limit 1");
        NewsContent content = newsContentMapper.selectOne(query);
        if (Objects.isNull(content)) {
            content = new NewsContent();
            content.setNewsId(id);
            content.setContent(dto.getContent());
            content.setCreateTime(LocalDateTime.now());
            content.setUpdateTime(LocalDateTime.now());
            newsContentMapper.insert(content);
        } else {
            NewsContent update = new NewsContent();
            update.setId(content.getId());
            update.setContent(dto.getContent());
            update.setUpdateTime(LocalDateTime.now());
            newsContentMapper.updateById(update);
        }
        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> deleteNews(Long id) {
        newsInfoMapper.deleteById(id);
        QueryWrapper<NewsContent> query = new QueryWrapper<>();
        query.eq("news_id", id);
        newsContentMapper.delete(query);
        return RestResp.ok();
    }

    @Override
    public RestResp<PageRespDto<Map<String, Object>>> listFriendLinks(PageReqDto pageReqDto, String keyword) {
        Page<HomeFriendLink> page = new Page<>(pageReqDto.getPageNum(), pageReqDto.getPageSize());
        QueryWrapper<HomeFriendLink> query = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like("link_name", keyword);
        }
        query.orderByAsc("sort").orderByDesc("create_time");
        IPage<HomeFriendLink> linkPage = homeFriendLinkMapper.selectPage(page, query);
        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), linkPage.getTotal(),
            linkPage.getRecords().stream().map(this::toFriendLinkRow).toList()));
    }

    @Override
    public RestResp<Void> saveFriendLink(AdminFriendLinkReqDto dto) {
        HomeFriendLink link = new HomeFriendLink();
        fillFriendLink(link, dto);
        link.setCreateTime(LocalDateTime.now());
        link.setUpdateTime(LocalDateTime.now());
        homeFriendLinkMapper.insert(link);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateFriendLink(Long id, AdminFriendLinkReqDto dto) {
        HomeFriendLink link = new HomeFriendLink();
        link.setId(id);
        fillFriendLink(link, dto);
        link.setUpdateTime(LocalDateTime.now());
        homeFriendLinkMapper.updateById(link);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteFriendLink(Long id) {
        homeFriendLinkMapper.deleteById(id);
        return RestResp.ok();
    }

    private Map<String, Object> toUserRow(UserInfo userInfo) {
        Map<String, Object> data = row();
        data.put("id", userInfo.getId());
        data.put("username", userInfo.getUsername());
        data.put("nickName", userInfo.getNickName());
        data.put("userPhoto", userInfo.getUserPhoto());
        data.put("userSex", userInfo.getUserSex());
        data.put("accountBalance", userInfo.getAccountBalance());
        data.put("status", userInfo.getStatus());
        data.put("createTime", userInfo.getCreateTime());
        data.put("updateTime", userInfo.getUpdateTime());
        return data;
    }

    private Map<String, Object> toBookRow(BookInfo bookInfo) {
        Map<String, Object> data = row();
        data.put("id", bookInfo.getId());
        data.put("bookName", bookInfo.getBookName());
        data.put("authorId", bookInfo.getAuthorId());
        data.put("authorName", bookInfo.getAuthorName());
        data.put("categoryId", bookInfo.getCategoryId());
        data.put("categoryName", bookInfo.getCategoryName());
        data.put("picUrl", bookInfo.getPicUrl());
        data.put("bookDesc", bookInfo.getBookDesc());
        data.put("wordCount", bookInfo.getWordCount());
        data.put("visitCount", bookInfo.getVisitCount());
        data.put("commentCount", bookInfo.getCommentCount());
        data.put("status", bookInfo.getBookStatus());
        data.put("bookStatus", bookInfo.getBookStatus());
        data.put("statusText", Objects.equals(bookInfo.getBookStatus(), 1) ? "已完结" : "连载中");
        data.put("updateTime", bookInfo.getUpdateTime());
        return data;
    }

    private Map<String, Object> toCategoryRow(BookCategory category) {
        Map<String, Object> data = row();
        data.put("id", category.getId());
        data.put("workDirection", category.getWorkDirection());
        data.put("workDirectionText", Objects.equals(category.getWorkDirection(), 1) ? "女频" : "男频");
        data.put("name", category.getName());
        data.put("sort", category.getSort());
        data.put("status", 1);
        data.put("createTime", category.getCreateTime());
        data.put("updateTime", category.getUpdateTime());
        return data;
    }

    private Map<String, Object> toCommentRow(BookComment comment, Map<Long, BookInfo> bookMap,
        Map<Long, UserInfo> userMap) {
        BookInfo book = bookMap.get(comment.getBookId());
        UserInfo user = userMap.get(comment.getUserId());
        Map<String, Object> data = row();
        data.put("id", comment.getId());
        data.put("bookId", comment.getBookId());
        data.put("commentBook", Objects.nonNull(book) ? book.getBookName() : null);
        data.put("userId", comment.getUserId());
        data.put("commentUser", Objects.nonNull(user) ? user.getNickName() : null);
        data.put("commentContent", comment.getCommentContent());
        data.put("replyCount", comment.getReplyCount());
        data.put("auditStatus", comment.getAuditStatus());
        data.put("status", comment.getAuditStatus());
        data.put("commentTime", comment.getCreateTime());
        return data;
    }

    private Map<String, Object> toHomeBookRow(HomeBook homeBook, Map<Long, BookInfo> bookMap) {
        BookInfo book = bookMap.get(homeBook.getBookId());
        Map<String, Object> data = row();
        data.put("id", homeBook.getId());
        data.put("type", homeBook.getType());
        data.put("sort", homeBook.getSort());
        data.put("bookId", homeBook.getBookId());
        data.put("bookName", Objects.nonNull(book) ? book.getBookName() : null);
        data.put("authorName", Objects.nonNull(book) ? book.getAuthorName() : null);
        data.put("createTime", homeBook.getCreateTime());
        data.put("updateTime", homeBook.getUpdateTime());
        return data;
    }

    private Map<String, Object> toAuthorRow(AuthorInfo author) {
        Map<String, Object> data = row();
        data.put("id", author.getId());
        data.put("userId", author.getUserId());
        data.put("inviteCode", author.getInviteCode());
        data.put("penName", author.getPenName());
        data.put("telPhone", author.getTelPhone());
        data.put("chatAccount", author.getChatAccount());
        data.put("email", author.getEmail());
        data.put("workDirection", author.getWorkDirection());
        data.put("status", author.getStatus());
        data.put("createTime", author.getCreateTime());
        data.put("updateTime", author.getUpdateTime());
        return data;
    }

    private Map<String, Object> toNewsRow(NewsInfo newsInfo) {
        Map<String, Object> data = row();
        data.put("id", newsInfo.getId());
        data.put("categoryId", newsInfo.getCategoryId());
        data.put("categoryName", newsInfo.getCategoryName());
        data.put("sourceName", newsInfo.getSourceName());
        data.put("title", newsInfo.getTitle());
        data.put("status", 1);
        data.put("createTime", newsInfo.getCreateTime());
        data.put("updateTime", newsInfo.getUpdateTime());
        return data;
    }

    private Map<String, Object> toFriendLinkRow(HomeFriendLink link) {
        Map<String, Object> data = row();
        data.put("id", link.getId());
        data.put("linkName", link.getLinkName());
        data.put("linkUrl", link.getLinkUrl());
        data.put("sort", link.getSort());
        data.put("isOpen", link.getIsOpen());
        data.put("status", link.getIsOpen());
        data.put("createTime", link.getCreateTime());
        data.put("updateTime", link.getUpdateTime());
        return data;
    }

    private void fillCategory(BookCategory category, AdminBookCategoryReqDto dto) {
        category.setWorkDirection(dto.getWorkDirection());
        category.setName(dto.getName());
        category.setSort(Objects.nonNull(dto.getSort()) ? dto.getSort() : 0);
    }

    private void fillHomeBook(HomeBook homeBook, AdminHomeBookReqDto dto) {
        homeBook.setType(dto.getType());
        homeBook.setSort(Objects.nonNull(dto.getSort()) ? dto.getSort() : 0);
        homeBook.setBookId(dto.getBookId());
    }

    private void fillNews(NewsInfo newsInfo, AdminNewsReqDto dto) {
        newsInfo.setCategoryId(dto.getCategoryId());
        newsInfo.setCategoryName(dto.getCategoryName());
        newsInfo.setSourceName(dto.getSourceName());
        newsInfo.setTitle(dto.getTitle());
    }

    private void fillFriendLink(HomeFriendLink link, AdminFriendLinkReqDto dto) {
        link.setLinkName(dto.getLinkName());
        link.setLinkUrl(dto.getLinkUrl());
        link.setSort(Objects.nonNull(dto.getSort()) ? dto.getSort() : 0);
        link.setIsOpen(Objects.nonNull(dto.getIsOpen()) ? dto.getIsOpen() : 1);
    }

    private Map<String, Object> toMenu(SysMenu menu, Map<Long, List<SysMenu>> childrenMap) {
        Map<String, Object> data = row();
        data.put("id", menu.getId());
        data.put("parentId", menu.getParentId());
        data.put("name", menu.getName());
        data.put("url", menu.getUrl());
        data.put("type", menu.getType());
        data.put("icon", menu.getIcon());
        data.put("sort", menu.getSort());
        data.put("children", childrenMap.getOrDefault(menu.getId(), Collections.emptyList()).stream()
            .map(child -> toMenu(child, childrenMap))
            .toList());
        return data;
    }

    private List<Map<String, Object>> defaultMenus() {
        List<Map<String, Object>> menus = new ArrayList<>();
        menus.add(defaultMenu("数据总览", "/admin/dashboard", "DataAnalysis"));
        menus.add(defaultMenu("小说管理", "/admin/books", "Reading"));
        menus.add(defaultMenu("分类管理", "/admin/categories", "Collection"));
        menus.add(defaultMenu("作家管理", "/admin/authors", "UserFilled"));
        menus.add(defaultMenu("用户管理", "/admin/users", "User"));
        menus.add(defaultMenu("评论管理", "/admin/comments", "ChatDotRound"));
        menus.add(defaultMenu("首页推荐", "/admin/home-books", "House"));
        menus.add(defaultMenu("新闻管理", "/admin/news", "Document"));
        menus.add(defaultMenu("友情链接", "/admin/friend-links", "Link"));
        return menus;
    }

    private Map<String, Object> defaultMenu(String name, String url, String icon) {
        Map<String, Object> data = row();
        data.put("name", name);
        data.put("url", url);
        data.put("icon", icon);
        data.put("children", Collections.emptyList());
        return data;
    }

    private Map<String, Object> row() {
        return new LinkedHashMap<>();
    }

}

