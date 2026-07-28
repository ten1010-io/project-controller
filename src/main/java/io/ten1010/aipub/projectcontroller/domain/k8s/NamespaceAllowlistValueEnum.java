package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NamespaceAllowlistValueEnum {

  TRUE("true"), FALSE("false");

  private static final Map<String, NamespaceAllowlistValueEnum> STR_TO_ENUM;

  static {
    STR_TO_ENUM = new HashMap<>();
    for (NamespaceAllowlistValueEnum e : NamespaceAllowlistValueEnum.values()) {
      STR_TO_ENUM.put(e.getStr(), e);
    }
  }

  private final String str;

  public static Optional<NamespaceAllowlistValueEnum> getEnum(String str) {
    NamespaceAllowlistValueEnum parsed = STR_TO_ENUM.get(str.toLowerCase());
    return Optional.ofNullable(parsed);
  }

}
