package io.ten1010.common.apiclient;

import lombok.Data;
import okhttp3.Call;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {

    static final Type POST_LIST_TYPE = new ParameterizedType() {

        @Override
        @NotNull
        public Type[] getActualTypeArguments() {
            return new Type[]{Post.class};
        }

        @NotNull
        @Override
        public Type getRawType() {
            return List.class;
        }

        @Override
        @Nullable
        public Type getOwnerType() {
            return null;
        }

    };

    @Data
    static class Post {

        @Nullable
        Integer userId;
        @Nullable
        Integer id;
        @Nullable
        String title;
        @Nullable
        String body;

    }

    @Test
    void buildCall() throws IOException {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("https://jsonplaceholder.typicode.com");

        Call call = apiClient.buildCall("/posts", "GET", Map.of(), Map.of("Accept", "application/json"));
        ApiResponse response = apiClient.execute(call);
        assertTrue(response.isSuccessful());
        assertTrue(response.getBodyAsString().isPresent());

        List<Post> posts = apiClient.getJson().deserialize(response.getBodyAsString().get(), POST_LIST_TYPE);
        assertEquals(100, posts.size());
    }

}