package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AIPubRole {

    @JsonProperty("isAdmin")
    private final boolean admin;

    private final List<AIPubProjectRole> projects;

}
