package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminBookCategoryReqDto {

    @NotNull
    private Integer workDirection;

    @NotBlank
    private String name;

    private Integer sort;

}

