package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import novel.dao.entity.BookInfo;
import novel.dao.entity.BookChapter;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookCommentMapper;
import novel.dao.mapper.BookContentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.req.ChapterUpdateReqDto;
import novel.dto.resp.BookInfoRespDto;
import novel.dto.resp.BookChapterRespDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.BookCategoryCacheManager;
import novel.manager.cache.BookChapterCacheManager;
import novel.manager.cache.BookContentCacheManager;
import novel.manager.cache.BookInfoCacheManager;
import novel.manager.cache.BookRankCacheManager;
import novel.manager.dao.UserDaoManager;
import novel.manager.mq.AmqpMsgManager;
import novel.core.auth.UserHolder;
import novel.core.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookServiceImplOwnershipTest {

    private static final Long OWNER_AUTHOR_ID = 10L;
    private static final Long OTHER_AUTHOR_ID = 99L;

    @Mock
    private BookCategoryCacheManager bookCategoryCacheManager;

    @Mock
    private BookRankCacheManager bookRankCacheManager;

    @Mock
    private BookInfoCacheManager bookInfoCacheManager;

    @Mock
    private BookChapterCacheManager bookChapterCacheManager;

    @Mock
    private BookContentCacheManager bookContentCacheManager;

    @Mock
    private AuthorInfoCacheManager authorInfoCacheManager;

    @Mock
    private BookInfoMapper bookInfoMapper;

    @Mock
    private BookChapterMapper bookChapterMapper;

    @Mock
    private BookContentMapper bookContentMapper;

    @Mock
    private BookCommentMapper bookCommentMapper;

    @Mock
    private UserDaoManager userDaoManager;

    @Mock
    private AmqpMsgManager amqpMsgManager;

    @InjectMocks
    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        UserHolder.setAuthorId(OTHER_AUTHOR_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void deleteRejectsChapterOwnedByAnotherAuthor() {
        prepareOwnedBookForWrite();

        assertThatThrownBy(() -> bookService.deleteBookChapter(30L))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCodeEnum().getCode()).isEqualTo("A0301"));
        verify(bookChapterMapper).selectById(30L);
        verifyNoMoreInteractions(bookChapterMapper);
        verify(bookInfoMapper).selectByIdForUpdate(20L);
        verifyNoMoreInteractions(bookInfoMapper);
        verifyNoInteractions(bookContentMapper, amqpMsgManager);
    }

    @Test
    void updateRejectsChapterOwnedByAnotherAuthor() {
        prepareOwnedBookForWrite();

        assertThatThrownBy(() -> bookService.updateBookChapter(30L, new ChapterUpdateReqDto()))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCodeEnum().getCode()).isEqualTo("A0301"));
        verify(bookChapterMapper).selectById(30L);
        verifyNoMoreInteractions(bookChapterMapper);
        verify(bookInfoMapper).selectByIdForUpdate(20L);
        verifyNoMoreInteractions(bookInfoMapper);
        verifyNoInteractions(bookContentMapper, amqpMsgManager);
    }

    @Test
    void detailRejectsChapterOwnedByAnotherAuthor() {
        prepareOwnedBook();

        assertThatThrownBy(() -> bookService.getBookChapter(30L))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCodeEnum().getCode()).isEqualTo("A0301"));
        verifyNoInteractions(bookContentMapper);
    }

    @Test
    void chapterListRejectsBookOwnedByAnotherAuthor() {
        BookInfo book = new BookInfo();
        book.setId(20L);
        book.setAuthorId(OWNER_AUTHOR_ID);
        when(bookInfoMapper.selectById(20L)).thenReturn(book);

        assertThatThrownBy(() -> bookService.listBookChapters(20L,
            new novel.core.common.req.PageReqDto())
            )
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCodeEnum().getCode()).isEqualTo("A0301"));
        verifyNoInteractions(bookChapterMapper);
    }

    private void prepareOwnedBookForWrite() {
        BookChapter chapter = new BookChapter();
        chapter.setBookId(20L);
        when(bookChapterMapper.selectById(30L)).thenReturn(chapter);
        BookInfo book = new BookInfo();
        book.setId(20L);
        book.setAuthorId(OWNER_AUTHOR_ID);
        when(bookInfoMapper.selectByIdForUpdate(20L)).thenReturn(book);
    }

    private void prepareOwnedBook() {
        BookChapterRespDto chapter = new BookChapterRespDto();
        chapter.setBookId(20L);
        when(bookChapterCacheManager.getChapter(30L)).thenReturn(chapter);
        BookInfoRespDto book = BookInfoRespDto.builder()
            .id(20L)
            .authorId(OWNER_AUTHOR_ID)
            .build();
        when(bookInfoCacheManager.getBookInfo(20L)).thenReturn(book);
    }
}
