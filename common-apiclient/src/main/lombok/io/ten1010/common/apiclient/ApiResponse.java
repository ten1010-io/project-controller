package io.ten1010.common.apiclient;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public class ApiResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte @Nullable [] body;

    public boolean isSuccessful() {
        return (200 <= this.statusCode && this.statusCode <= 299);
    }

    public Optional<String> getBodyAsString() {
        return Optional.ofNullable(this.body)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

}
