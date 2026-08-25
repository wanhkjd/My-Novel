package novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminStatusReqDto {

    @NotNull
    private Integer status;

}

