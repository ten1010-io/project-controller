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
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ImageHubUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ImageRegistryRobotReconciler extends AbstractReconciler {

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
    ImageRegistryAccess access = new ImageRegistryAccess();
    access.setResource("repository");
    access.setAction("pull");

    ImageRegistryRobotPermission permission = new ImageRegistryRobotPermission();
    permission.setImageHubId(ImageHubUtils.getSpecId(imageHub));
    permission.setAccesses(List.of(access));

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

    List<ImageRegistryRobotPermission> reconciledPermissions = createPermissions(projectOpt.get());

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
      this.robotService.updateImageRegistryRobot(robotOpt.get().getId(), existing);
    }

    if (!reconciledPermissions.isEmpty()) {
      ImageRegistryRobot newRobot = new ImageRegistryRobot();
      newRobot.setUsername(username);
      newRobot.setPermissions(reconciledPermissions);
      this.robotService.createImageRegistryRobot(newRobot);
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

  private List<ImageRegistryRobotPermission> createPermissions(V1alpha1Project project) {
    return ProjectUtils.getSpecBindingImageHubs(project).stream()
        .map(e -> this.imageHubIndexer.getByKey(this.keyResolver.resolveKey(e)))
        .filter(Objects::nonNull)
        .map(ImageRegistryRobotReconciler::createPermission)
        .toList();
  }

}
