package novel.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminFriendLinkReqDto {

    @NotBlank
    private String linkName;

    @NotBlank
    private String linkUrl;

    private Integer sort;

    private Integer isOpen;

}

