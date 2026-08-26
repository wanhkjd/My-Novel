package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import novel.core.auth.UserHolder;
import novel.dao.mapper.BookChapterMapper;
import novel.dao.mapper.BookCommentMapper;
import novel.dao.mapper.BookContentMapper;
import novel.dao.mapper.BookInfoMapper;
import novel.dto.req.BookAddReqDto;
import novel.dto.resp.BookCategoryRespDto;
import novel.manager.cache.AuthorInfoCacheManager;
import novel.manager.cache.BookCategoryCacheManager;
import novel.manager.cache.BookChapterCacheManager;
import novel.manager.cache.BookContentCacheManager;
import novel.manager.cache.BookInfoCacheManager;
import novel.manager.cache.BookRankCacheManager;
import novel.manager.dao.UserDaoManager;
import novel.manager.mq.AmqpMsgManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookServiceImplValidationTest {

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
        UserHolder.setUserId(10L);
        UserHolder.setAuthorId(20L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void publishRejectsCategoryThatDoesNotMatchWorkDirection() {
        when(bookInfoMapper.selectCount(any())).thenReturn(0L);
        when(bookCategoryCacheManager.listCategory(0)).thenReturn(List.of(
            BookCategoryRespDto.builder().id(2L).name("都市").build()
        ));

        var resp = bookService.saveBook(bookAddReqDto());

        assertThat(resp.isOk()).isFalse();
        assertThat(resp.getCode()).isEqualTo("A3002");
        verifyNoMoreInteractions(bookInfoMapper);
        verifyNoInteractions(bookChapterMapper, bookContentMapper, amqpMsgManager);
    }

    private BookAddReqDto bookAddReqDto() {
        BookAddReqDto dto = new BookAddReqDto();
        dto.setWorkDirection(0);
        dto.setCategoryId(1L);
        dto.setCategoryName("玄幻");
        dto.setBookName("测试作品");
        dto.setPicUrl("/pic.jpg");
        dto.setBookDesc("简介");
        dto.setIsVip(0);
        return dto;
    }
}
