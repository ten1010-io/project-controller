package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArtifactListOptions extends ListOptions {

  // null 이면 보내지 않아 백엔드 기본값(요약 포함)을 따른다.
  @Nullable
  private Boolean withScanOverview;

}
