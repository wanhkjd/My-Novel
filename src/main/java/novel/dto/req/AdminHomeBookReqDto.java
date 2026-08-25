package novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminHomeBookReqDto {

    @NotNull
    private Integer type;

    private Integer sort;

    @NotNull
    private Long bookId;

}

