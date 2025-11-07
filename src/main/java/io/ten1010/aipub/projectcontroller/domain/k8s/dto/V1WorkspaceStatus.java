package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1WorkspaceStatus {

  @Nullable
  List<V1WorkspaceAddress> addresses;
  @Nullable
  V1WorkspacePodStatus pods;
  @Nullable
  V1WorkspacePodReadyCondition readyCondition;
  @Nullable
  V1WorkspaceSshEndpoint sshEndpoint;

}
