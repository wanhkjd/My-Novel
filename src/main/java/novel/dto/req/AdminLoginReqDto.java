package novel.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginReqDto {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

}

