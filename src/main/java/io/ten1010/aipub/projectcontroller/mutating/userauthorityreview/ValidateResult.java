package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class ValidateResult {

    private final boolean allowed;
    @Nullable
    private final Integer statusCode;
    @Nullable
    private final String message;

    private ValidateResult(boolean allowed, @Nullable Integer statusCode, @Nullable String message) {
        this.allowed = allowed;
        this.statusCode = statusCode;
        this.message = message;
    }

    public static ValidateResult skip() {
        return new ValidateResult(true, null, null);
    }

    public static ValidateResult allowed() {
        return new ValidateResult(true, null, null);
    }

    public static ValidateResult rejected(int statusCode, String message) {
        return new ValidateResult(false, statusCode, message);
    }

}
