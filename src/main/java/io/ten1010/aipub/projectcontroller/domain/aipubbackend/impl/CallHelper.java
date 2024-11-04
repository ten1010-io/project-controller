package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.common.apiclient.ApiClient;
import io.ten1010.common.apiclient.ApiResponse;
import io.ten1010.common.apiclient.json.Json;
import okhttp3.Call;

import java.io.IOException;
import java.util.Optional;

public class CallHelper {

    private final ApiClient apiClient;
    private final Json json;

    public CallHelper(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.json = this.apiClient.getJson();
    }

    public void executeCall(Call call) {
        try {
            ApiResponse response = this.apiClient.execute(call);
            if (!response.isSuccessful()) {
                throw new AipubBackendResponseException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> Optional<T> executeCall(Call call, TypeToken<T> typeToken) {
        try {
            ApiResponse response = this.apiClient.execute(call);
            if (!response.isSuccessful()) {
                throw new AipubBackendResponseException(response);
            }
            if (response.getBodyAsString().isEmpty()) {
                return Optional.empty();
            }
            T body = this.json.deserialize(response.getBodyAsString().get(), typeToken.getType());
            return Optional.of(body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
