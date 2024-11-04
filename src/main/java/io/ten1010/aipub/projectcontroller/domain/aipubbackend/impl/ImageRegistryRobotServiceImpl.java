package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;
import io.ten1010.common.apiclient.ApiClient;
import okhttp3.Call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageRegistryRobotServiceImpl implements ImageRegistryRobotService {

    private static final TypeToken<ImageRegistryRobot> IMAGE_REGISTRY_ROBOT_TYPE_TOKEN = new TypeToken<>() {
    };

    private static final TypeToken<List<ImageRegistryRobot>> IMAGE_REGISTRY_ROBOT_LIST_TYPE_TOKEN = new TypeToken<>() {
    };

    private static final TypeToken<ImageRegistryRobotSecret> IMAGE_REGISTRY_ROBOT_SECRET_TYPE_TOKEN = new TypeToken<>() {
    };

    private final ApiClient aipubBackendClient;
    private final CallHelper callHelper;

    public ImageRegistryRobotServiceImpl(ApiClient aipubBackendClient) {
        this.aipubBackendClient = aipubBackendClient;
        this.callHelper = new CallHelper(aipubBackendClient);
    }

    @Override
    public void createImageRegistryRobot(ImageRegistryRobot imageRegistryRobot) {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots",
                "POST",
                "application/json",
                imageRegistryRobot);
        this.callHelper.executeCall(call);
    }

    @Override
    public List<ImageRegistryRobot> listImageRegistryRobots(ImageRegistryRobotListOptions options) {
        Map<String, String> queryParams = new HashMap<>();
        ListOptionsUtils.applyImageRegistryRobotListOptions(queryParams, options);

        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots",
                "GET",
                queryParams);
        return this.callHelper.executeCall(call, IMAGE_REGISTRY_ROBOT_LIST_TYPE_TOKEN).orElseThrow();
    }

    @Override
    public ImageRegistryRobot getImageRegistryRobot(String id) {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots/" + id,
                "GET");
        return this.callHelper.executeCall(call, IMAGE_REGISTRY_ROBOT_TYPE_TOKEN).orElseThrow();
    }

    @Override
    public void updateImageRegistryRobot(String id, ImageRegistryRobot imageRegistryRobot) {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots/" + id,
                "PUT",
                "application/json",
                imageRegistryRobot);
        this.callHelper.executeCall(call);
    }

    @Override
    public ImageRegistryRobotSecret refreshSecret(String id) {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots/" + id + "/refreshsecret",
                "PUT",
                "application/json",
                new Object());
        return this.callHelper.executeCall(call, IMAGE_REGISTRY_ROBOT_SECRET_TYPE_TOKEN).orElseThrow();
    }

    @Override
    public void deleteImageRegistryRobot(String id) {
        Call call = this.aipubBackendClient.buildCall(
                "/imageregistryrobots/" + id,
                "DELETE");
        this.callHelper.executeCall(call);
    }

}
