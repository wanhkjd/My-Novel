package novel.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginRespDto {

    private String token;

    private Long uid;

    private String username;

    private String nickName;

}

