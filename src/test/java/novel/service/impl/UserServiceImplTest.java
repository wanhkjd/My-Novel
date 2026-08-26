package novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import novel.core.common.exception.BusinessException;
import novel.core.common.req.PageReqDto;
import novel.dao.entity.BookInfo;
import novel.dao.entity.UserBookshelf;
import novel.dao.mapper.BookInfoMapper;
import novel.dao.mapper.UserBookshelfMapper;
import novel.dao.mapper.UserFeedbackMapper;
import novel.dao.mapper.UserInfoMapper;
import novel.core.util.JwtUtils;
import novel.dto.resp.BookInfoRespDto;
import novel.manager.redis.VerifyCodeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private VerifyCodeManager verifyCodeManager;

    @Mock
    private UserFeedbackMapper userFeedbackMapper;

    @Mock
    private UserBookshelfMapper userBookshelfMapper;

    @Mock
    private BookInfoMapper bookInfoMapper;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void addBookshelfRejectsDuplicateBook() {
        when(bookInfoMapper.selectById(20L)).thenReturn(new BookInfo());
        when(userBookshelfMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> userService.addBookshelf(10L, 20L))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCodeEnum().getCode()).isEqualTo("A2003"));
        verify(userBookshelfMapper).selectCount(any());
        org.mockito.Mockito.verifyNoMoreInteractions(userBookshelfMapper);
    }

    @Test
    void listBookshelfKeepsShelfOrderAndMapsBooks() {
        PageReqDto pageReq = new PageReqDto();
        UserBookshelf first = new UserBookshelf();
        first.setBookId(21L);
        UserBookshelf second = new UserBookshelf();
        second.setBookId(20L);
        Page<UserBookshelf> page = new Page<>(1, 10);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(userBookshelfMapper.selectPage(any(), any())).thenReturn(page);

        BookInfo book21 = book(21L, "Book 21");
        BookInfo book20 = book(20L, "Book 20");
        when(bookInfoMapper.selectBatchIds(List.of(21L, 20L)))
            .thenReturn(List.of(book20, book21));

        var resp = userService.listBookshelf(10L, pageReq);

        assertThat(resp.isOk()).isTrue();
        assertThat(resp.getData().getTotal()).isEqualTo(2);
        assertThat(resp.getData().getList())
            .extracting(BookInfoRespDto::getBookName)
            .containsExactly("Book 21", "Book 20");
    }

    private BookInfo book(Long id, String name) {
        BookInfo book = new BookInfo();
        book.setId(id);
        book.setBookName(name);
        return book;
    }
}
