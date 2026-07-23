package io.ten1010.common.jsonpatch;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum OpEnum {

    ADD("add"), COPY("copy"), MOVE("move"), REMOVE("remove"), REPLACE("replace"), TEST("test");

    private static final Map<String, OpEnum> STR_TO_ENUM;

    static {
        STR_TO_ENUM = new HashMap<>();
        for (OpEnum e : OpEnum.values()) {
            STR_TO_ENUM.put(e.getStr(), e);
        }
    }

    public static Optional<OpEnum> getEnum(String str) {
        OpEnum parsed = STR_TO_ENUM.get(str.toLowerCase());
        return Optional.ofNullable(parsed);
    }

    private final String str;

}
