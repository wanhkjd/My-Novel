package novel.core.common.req;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageReqDtoTest {

    @Test
    void normalizesInvalidPagination() {
        PageReqDto dto = new PageReqDto();
        dto.setPageNum(-1);
        dto.setPageSize(0);

        assertThat(dto.getPageNum()).isEqualTo(1);
        assertThat(dto.getPageSize()).isEqualTo(1);
    }

    @Test
    void limitsPageSize() {
        PageReqDto dto = new PageReqDto();
        dto.setPageSize(1000);

        assertThat(dto.getPageSize()).isEqualTo(100);
    }
}
