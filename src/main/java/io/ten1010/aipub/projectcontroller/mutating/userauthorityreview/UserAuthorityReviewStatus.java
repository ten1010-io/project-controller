package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class UserAuthorityReviewStatus {

    @JsonProperty("isClusterAdmin")
    private final boolean clusterAdmin;

    @JsonProperty("aipubRole")
    private final AIPubRole aipubRole;

    private final Map<String, RBACAuthorityStatus> authorities;

}
