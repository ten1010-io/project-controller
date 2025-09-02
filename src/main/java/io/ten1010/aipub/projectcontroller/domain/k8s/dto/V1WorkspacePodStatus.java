package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1WorkspacePodStatus {
    @Nullable
    List<V1WorkspacePodState> pending;
    @Nullable
    List<V1WorkspacePodState> progressing;
    @Nullable
    List<V1WorkspacePodState> ready;

}
