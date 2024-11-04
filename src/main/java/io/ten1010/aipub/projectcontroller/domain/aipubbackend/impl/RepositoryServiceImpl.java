package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.RepositoryService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.Repository;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.RepositoryListOptions;
import io.ten1010.common.apiclient.ApiClient;
import okhttp3.Call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryServiceImpl implements RepositoryService {

    private static final TypeToken<List<Repository>> REPOSITORY_LIST_TYPE_TOKEN = new TypeToken<>() {
    };

    private final ApiClient aipubBackendClient;
    private final CallHelper callHelper;

    public RepositoryServiceImpl(ApiClient aipubBackendClient) {
        this.aipubBackendClient = aipubBackendClient;
        this.callHelper = new CallHelper(aipubBackendClient);
    }

    @Override
    public List<Repository> listNamespacedRepositories(String namespacedId, RepositoryListOptions options) {
        Map<String, String> queryParams = new HashMap<>();
        ListOptionsUtils.applyRepositoryListOptions(queryParams, options);

        Call call = this.aipubBackendClient.buildCall(
                "/imagenamespaces/" + namespacedId + "/repositories",
                "GET",
                queryParams);
        return this.callHelper.executeCall(call, REPOSITORY_LIST_TYPE_TOKEN).orElseThrow();
    }

}
