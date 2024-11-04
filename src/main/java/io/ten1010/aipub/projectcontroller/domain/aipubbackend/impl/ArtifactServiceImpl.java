package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ArtifactService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.Artifact;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ArtifactListOptions;
import io.ten1010.common.apiclient.ApiClient;
import okhttp3.Call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactServiceImpl implements ArtifactService {

    private static final TypeToken<List<Artifact>> ARTIFACT_LIST_TYPE_TOKEN = new TypeToken<>() {
    };

    private final ApiClient aipubBackendClient;
    private final CallHelper callHelper;

    public ArtifactServiceImpl(ApiClient aipubBackendClient) {
        this.aipubBackendClient = aipubBackendClient;
        this.callHelper = new CallHelper(aipubBackendClient);
    }

    @Override
    public List<Artifact> listArtifacts(String namespacedId, String repositoryName, ArtifactListOptions options) {
        Map<String, String> queryParams = new HashMap<>();
        ListOptionsUtils.applyArtifactListOptions(queryParams, options);

        Call call = this.aipubBackendClient.buildCall(
                "/imagenamespaces/" + namespacedId + "/repositories/" + repositoryName + "/artifacts",
                "GET",
                queryParams);
        return this.callHelper.executeCall(call, ARTIFACT_LIST_TYPE_TOKEN).orElseThrow();
    }

}
