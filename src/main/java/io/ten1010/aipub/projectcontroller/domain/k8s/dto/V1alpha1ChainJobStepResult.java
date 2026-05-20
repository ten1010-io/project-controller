package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum V1alpha1ChainJobStepResult {

  SUCCEEDED("Succeeded"),
  FAILED("Failed");

  private final String value;

  V1alpha1ChainJobStepResult(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return this.value;
  }

  @JsonCreator
  public static V1alpha1ChainJobStepResult fromValue(String value) {
    for (V1alpha1ChainJobStepResult v : values()) {
      if (v.value.equals(value)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown V1alpha1ChainJobStepResult value: " + value);
  }

}
