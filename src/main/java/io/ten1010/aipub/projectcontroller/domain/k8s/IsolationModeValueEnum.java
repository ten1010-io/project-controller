package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum IsolationModeValueEnum {

  STRICT("strict"), LENIENT("lenient");

  private static final Map<String, IsolationModeValueEnum> STR_TO_ENUM;

  static {
    STR_TO_ENUM = new HashMap<>();
    for (IsolationModeValueEnum e : IsolationModeValueEnum.values()) {
      STR_TO_ENUM.put(e.getStr(), e);
    }
  }

  private final String str;

  public static Optional<IsolationModeValueEnum> getEnum(String str) {
    IsolationModeValueEnum parsed = STR_TO_ENUM.get(str.toLowerCase());
    return Optional.ofNullable(parsed);
  }

}
