package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryInfoService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryInfo;
import io.ten1010.common.apiclient.ApiClient;
import okhttp3.Call;

public class ImageRegistryInfoServiceImpl implements ImageRegistryInfoService {

    private static final TypeToken<ImageRegistryInfo> IMAGE_REGISTRY_INFO_TYPE_TOKEN = new TypeToken<>() {
    };

    private final ApiClient aipubBackendClient;
    private final CallHelper callHelper;

    public ImageRegistryInfoServiceImpl(ApiClient aipubBackendClient) {
        this.aipubBackendClient = aipubBackendClient;
        this.callHelper = new CallHelper(aipubBackendClient);
    }

    @Override
    public ImageRegistryInfo getImageRegistryInfo() {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryinfo",
                "GET");
        return this.callHelper.executeCall(call, IMAGE_REGISTRY_INFO_TYPE_TOKEN).orElseThrow();
    }

}
