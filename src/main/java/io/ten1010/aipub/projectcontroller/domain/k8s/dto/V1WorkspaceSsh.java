package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1WorkspaceSsh {

    @Nullable
    Integer port;
    @Nullable
    String publicKey;

}
