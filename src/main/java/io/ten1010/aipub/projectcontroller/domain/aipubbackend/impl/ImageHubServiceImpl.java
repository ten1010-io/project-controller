package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import com.google.gson.reflect.TypeToken;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageHubService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageHub;
import io.ten1010.common.apiclient.ApiClient;
import java.util.Optional;
import okhttp3.Call;

public class ImageHubServiceImpl implements ImageHubService {

  private static final TypeToken<ImageHub> IMAGE_HUB_PROJECT_TYPE_TOKEN = new TypeToken<>() {
  };

  private final ApiClient aipubBackendClient;
  private final CallHelper callHelper;

  public ImageHubServiceImpl(ApiClient aipubBackendClient) {
    this.aipubBackendClient = aipubBackendClient;
    this.callHelper = new CallHelper(aipubBackendClient);
  }

  @Override
  public Optional<ImageHub> getImageHubProject(String hubId) {
    Call call = this.aipubBackendClient.buildCall(
        "/imagehubs/" + hubId,
        "GET");

    return this.callHelper.executeCall(call, IMAGE_HUB_PROJECT_TYPE_TOKEN);
  }

}
