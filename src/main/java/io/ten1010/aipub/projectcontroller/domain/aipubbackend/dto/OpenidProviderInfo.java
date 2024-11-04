package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class OpenidProviderInfo {

    @Nullable
    private String issuerUri;

}
