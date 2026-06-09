package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceList implements KubernetesListObject {

  @Nullable
  private String apiVersion;
  @Nullable
  private String kind;
  @Nullable
  private V1ListMeta metadata;
  @Nullable
  private List<V1beta1Workspace> items;

}
