package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminNewsReqDto {

    private Long categoryId;

    private String categoryName;

    private String sourceName;

    @NotBlank
    private String title;

    private String content;

}

