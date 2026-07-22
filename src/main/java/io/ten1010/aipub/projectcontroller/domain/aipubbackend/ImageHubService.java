package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageHub;
import java.util.Optional;

public interface ImageHubService {

  Optional<ImageHub> getImageHubProject(String imageHubId);

}
