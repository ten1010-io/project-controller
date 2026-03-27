package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class MutateResult {

    private final boolean allowed;
    @Nullable
    private final UserAuthorityReviewStatus status;
    @Nullable
    private final Integer statusCode;
    @Nullable
    private final String message;

    private MutateResult(boolean allowed,
                         @Nullable UserAuthorityReviewStatus status,
                         @Nullable Integer statusCode,
                         @Nullable String message) {
        this.allowed = allowed;
        this.status = status;
        this.statusCode = statusCode;
        this.message = message;
    }

    public static MutateResult skip() {
        return new MutateResult(true, null, null, null);
    }

    public static MutateResult allowed(UserAuthorityReviewStatus status) {
        return new MutateResult(true, status, null, null);
    }

    public static MutateResult rejected(int statusCode, String message) {
        return new MutateResult(false, null, statusCode, message);
    }

    public boolean hasStatus() {
        return status != null;
    }

}
