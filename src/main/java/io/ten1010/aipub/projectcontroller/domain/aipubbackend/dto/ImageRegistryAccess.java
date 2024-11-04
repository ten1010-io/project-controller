package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class ImageRegistryAccess {

    @Nullable
    private String resource;
    @Nullable
    private String action;

}
