package io.ten1010.aipub.projectcontroller.mutating.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1Status {

    @Nullable
    private Integer code;
    @Nullable
    private String message;

}
