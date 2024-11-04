package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.OpenidProviderInfoService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.OpenidProviderInfo;
import io.ten1010.common.apiclient.ApiClient;
import okhttp3.Call;

public class OpenidProviderInfoServiceImpl implements OpenidProviderInfoService {

    private static final TypeToken<OpenidProviderInfo> OPENID_PROVIDER_INFO_TYPE_TOKEN = new TypeToken<>() {
    };

    private final ApiClient aipubBackendClient;
    private final CallHelper callHelper;

    public OpenidProviderInfoServiceImpl(ApiClient aipubBackendClient) {
        this.aipubBackendClient = aipubBackendClient;
        this.callHelper = new CallHelper(aipubBackendClient);
    }

    @Override
    public OpenidProviderInfo getOpenidProviderInfo() {
        Call call = this.aipubBackendClient.buildCall(
                "/openidproviderinfo",
                "GET");
        return this.callHelper.executeCall(call, OPENID_PROVIDER_INFO_TYPE_TOKEN).orElseThrow();
    }

}
