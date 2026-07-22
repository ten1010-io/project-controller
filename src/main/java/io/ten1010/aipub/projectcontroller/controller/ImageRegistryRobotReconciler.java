package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotUsernameResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryAccess;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotPermission;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.AipubBackendResponseException;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ImageHubUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.common.apiclient.ApiResponse;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageRegistryRobotReconciler extends AbstractReconciler {

  private static final Logger log = LoggerFactory.getLogger(ImageRegistryRobotReconciler.class);

  // aipub 백엔드가 반환하는 IMAGEHUB_NOT_FOUND 응답 body 의 message 에서 ImageHub id 를 추출하기 위한 패턴.
  // 예) {"type":"IMAGEHUB_NOT_FOUND","message":"ImageHub '32' not found"}
  private static final Pattern IMAGE_HUB_NOT_FOUND_ID_PATTERN =
      Pattern.compile("ImageHub '([^']+)' not found");

  private final ImageRegistryRobotService robotService;
  private final ImageRegistryRobotUsernameResolver usernameResolver;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final Indexer<V1alpha1ImageHub> imageHubIndexer;
  private final KeyResolver keyResolver;

  public ImageRegistryRobotReconciler(
      ImageRegistryRobotService robotService, ImageRegistryRobotUsernameResolver usernameResolver,
      SharedInformerFactory sharedInformerFactory) {
    this.robotService = robotService;
    this.usernameResolver = usernameResolver;
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.imageHubIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1ImageHub.class)
        .getIndexer();
    this.keyResolver = new KeyResolver();
  }

  private static ImageRegistryRobotPermission createPermission(V1alpha1ImageHub imageHub) {
    ImageRegistryAccess pullAccess = new ImageRegistryAccess();
    pullAccess.setResource("repository");
    pullAccess.setAction("pull");
    ImageRegistryAccess pushAccess = new ImageRegistryAccess();
    pushAccess.setResource("repository");
    pushAccess.setAction("push");

    ImageRegistryRobotPermission permission = new ImageRegistryRobotPermission();
    permission.setImageHubId(ImageHubUtils.getSpecId(imageHub));
    permission.setAccesses(List.of(pullAccess, pushAccess));

    return permission;
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String username = this.usernameResolver.resolve(request.getName());
    Optional<ImageRegistryRobot> robotOpt = findByUsername(username);

    String projKey = this.keyResolver.resolveKey(request.getName());
    Optional<V1alpha1Project> projectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(projKey));

    if (projectOpt.isEmpty()) {
      if (robotOpt.isPresent()) {
        Objects.requireNonNull(robotOpt.get().getId());
        this.robotService.deleteImageRegistryRobot(robotOpt.get().getId());
        return new Result(false);
      }
      return new Result(false);
    }

    List<V1alpha1ImageHub> boundImageHubs = resolveBoundImageHubs(projectOpt.get());
    List<ImageRegistryRobotPermission> reconciledPermissions = boundImageHubs.stream()
        .map(ImageRegistryRobotReconciler::createPermission)
        .toList();

    if (robotOpt.isPresent()) {
      Objects.requireNonNull(robotOpt.get().getId());
      Objects.requireNonNull(robotOpt.get().getPermissions());
      if (Set.copyOf(robotOpt.get().getPermissions()).equals(Set.copyOf(reconciledPermissions))) {
        return new Result(false);
      }

      ImageRegistryRobot existing = new ImageRegistryRobot();
      existing.setId(robotOpt.get().getId());
      existing.setCreatedTimestamp(robotOpt.get().getCreatedTimestamp());
      existing.setUsername(robotOpt.get().getUsername());
      existing.setSecret(robotOpt.get().getSecret());
      existing.setPermissions(reconciledPermissions);
      try {
        this.robotService.updateImageRegistryRobot(robotOpt.get().getId(), existing);
      } catch (AipubBackendResponseException e) {
        if (isImageHubNotFound(e)) {
          return logImageHubNotFoundAndRequeue(e, request.getName(), boundImageHubs);
        }
        throw e;
      }
    }

    if (!reconciledPermissions.isEmpty()) {
      ImageRegistryRobot newRobot = new ImageRegistryRobot();
      newRobot.setUsername(username);
      newRobot.setPermissions(reconciledPermissions);
      try {
        this.robotService.createImageRegistryRobot(newRobot);
      } catch (AipubBackendResponseException e) {
        if (isImageHubNotFound(e)) {
          return logImageHubNotFoundAndRequeue(e, request.getName(), boundImageHubs);
        }
        throw e;
      }
      return new Result(false);
    }

    return new Result(false);
  }

  private Optional<ImageRegistryRobot> findByUsername(String username) {
    ImageRegistryRobotListOptions options = new ImageRegistryRobotListOptions();
    options.setPageOffset(0);
    options.setPageSize(100);
    List<ImageRegistryRobot> robots = this.robotService.listImageRegistryRobots(options);
    return robots.stream()
        .filter(e -> Objects.nonNull(e.getUsername()))
        .filter(e -> e.getUsername().equals(username))
        .findFirst();
  }

  private List<V1alpha1ImageHub> resolveBoundImageHubs(V1alpha1Project project) {
    return ProjectUtils.getSpecBindingImageHubs(project).stream()
        .map(e -> this.imageHubIndexer.getByKey(this.keyResolver.resolveKey(e)))
        .filter(Objects::nonNull)
        .filter(ImageRegistryRobotReconciler::hasSpecId)
        .toList();
  }

  private static boolean hasSpecId(V1alpha1ImageHub imageHub) {
    return imageHub.getSpec() != null
        && imageHub.getSpec().getId() != null
        && !imageHub.getSpec().getId().isBlank();
  }

  private Result logImageHubNotFoundAndRequeue(
      AipubBackendResponseException e, String projName, List<V1alpha1ImageHub> boundImageHubs) {
    String missingId = extractMissingImageHubId(e).orElse(null);
    log.warn(
        "ImageHub CR {} 를 AIPub Backend 에서 찾을 수 없어 image registry robot reconcile 을 건너뜀 "
            + "[project={}]. AIPub Backend 에 존재하지 않는 ImageHub 를 참조하고 있는지 확인 필요. "
            + "backendMessage={}",
        describeImageHub(missingId, boundImageHubs),
        projName,
        e.getResponse().getBodyAsString().orElse(null));
    return new Result(true, getGeneralFailRequeueDuration());
  }

  /**
   * IMAGEHUB_NOT_FOUND 로 지목된 ImageHub 를 사람이 읽을 수 있는 {@code [id=.., name=..]} 형태로 기술한다.
   *
   * <p>백엔드 message 에서 추출한 id 로 bound ImageHub CR 을 역추적해 CR 이름까지 함께 남긴다.
   * id 를 특정하지 못하면 project 가 바인딩한 전체 ImageHub CR 목록을 남긴다.
   */
  private static String describeImageHub(
      @Nullable String missingId, List<V1alpha1ImageHub> boundImageHubs) {
    if (missingId != null) {
      String crName = boundImageHubs.stream()
          .filter(h -> missingId.equals(ImageHubUtils.getSpecId(h)))
          .map(K8sObjectUtils::getName)
          .findFirst()
          .orElse("<unknown>");
      return String.format("[id=%s, name=%s]", missingId, crName);
    }
    if (boundImageHubs.isEmpty()) {
      return "[]";
    }
    return boundImageHubs.stream()
        .map(h -> String.format("[id=%s, name=%s]", ImageHubUtils.getSpecId(h),
            K8sObjectUtils.getName(h)))
        .collect(Collectors.joining(", "));
  }

  private static boolean isImageHubNotFound(AipubBackendResponseException e) {
    ApiResponse response = e.getResponse();
    if (response.getStatusCode() != HttpURLConnection.HTTP_NOT_FOUND) {
      return false;
    }
    return response.getBodyAsString()
        .filter(body -> body.contains("IMAGEHUB_NOT_FOUND"))
        .isPresent();
  }

  private static Optional<String> extractMissingImageHubId(AipubBackendResponseException e) {
    return e.getResponse().getBodyAsString().flatMap(body -> {
      Matcher matcher = IMAGE_HUB_NOT_FOUND_ID_PATTERN.matcher(body);
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    });
  }

}
