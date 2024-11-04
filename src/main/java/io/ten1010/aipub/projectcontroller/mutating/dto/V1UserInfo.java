package io.ten1010.aipub.projectcontroller.mutating.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1UserInfo {

    @Nullable
    private String username;
    @Nullable
    private List<String> groups;

}
