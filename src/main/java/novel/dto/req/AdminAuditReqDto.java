package novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAuditReqDto {

    @NotNull
    private Integer auditStatus;

}

