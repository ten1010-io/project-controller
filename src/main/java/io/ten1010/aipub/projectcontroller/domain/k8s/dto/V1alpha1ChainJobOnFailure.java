package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum V1alpha1ChainJobOnFailure {

  ABORT("Abort"),
  CONTINUE("Continue");

  private final String value;

  V1alpha1ChainJobOnFailure(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return this.value;
  }

  @JsonCreator
  public static V1alpha1ChainJobOnFailure fromValue(String value) {
    for (V1alpha1ChainJobOnFailure v : values()) {
      if (v.value.equals(value)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown V1alpha1ChainJobOnFailure value: " + value);
  }

}
