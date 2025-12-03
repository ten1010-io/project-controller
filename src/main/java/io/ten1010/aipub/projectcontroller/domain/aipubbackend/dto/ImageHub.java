package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ImageHub {

  private String id;
  private String name;
  private Long createdTimestamp;
  private Long updatedTimestamp;
  private Integer repoCount;
  @SerializedName("public")
  @JsonProperty("public")
  private Boolean _public;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long quotaLimits;

}
