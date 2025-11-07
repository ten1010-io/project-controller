package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHubStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImageHubReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1alpha1ImageHub> imageHubIndexer;
  private final BoundObjectResolver boundObjectResolver;
  private final StatusPatchHelper<V1alpha1ImageHub> statusPatchHelper;

  public ImageHubReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.keyResolver = new KeyResolver();
    this.reconciliationService = reconciliationService;
    this.imageHubIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1ImageHub.class)
        .getIndexer();
    this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
    this.statusPatchHelper = new StatusPatchHelper<>(
        k8sApiProvider.getApiClient(),
        K8sObjectTypeConstants.IMAGE_HUB_V1ALPHA1,
        ProjectApiConstants.IMAGE_HUB_RESOURCE_PLURAL);
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String imageHubKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<V1alpha1ImageHub> imageHubOpt = Optional.ofNullable(
        this.imageHubIndexer.getByKey(imageHubKey));
    if (imageHubOpt.isEmpty()) {
      return new Result(false);
    }
    V1alpha1ImageHub imageHub = imageHubOpt.get();

    List<V1alpha1Project> boundProjects = this.boundObjectResolver.getAllBoundProjects(imageHub);
    List<V1alpha1AipubUser> boundAipubUsers = this.boundObjectResolver.getAllBoundAipubUsers(
        imageHub);
    V1alpha1ImageHubStatus reconciledStatus = this.reconciliationService.reconcileImageHubStatus(
        imageHub, boundProjects, boundAipubUsers);

    return reconcileExistingImageHub(imageHubOpt.get(), reconciledStatus);
  }

  private Result reconcileExistingImageHub(V1alpha1ImageHub existing,
      V1alpha1ImageHubStatus reconciledStatus) throws ApiException {
    if (Objects.equals(existing.getStatus(), reconciledStatus)) {
      return new Result(false);
    }

    V1alpha1ImageHub edited = new V1alpha1ImageHub();
    edited.setApiVersion(existing.getApiVersion());
    edited.setKind(existing.getKind());
    edited.setMetadata(existing.getMetadata());
    edited.setSpec(existing.getSpec());
    edited.setStatus(reconciledStatus);
    updateStatus(edited);
    return new Result(false);
  }

  private void updateStatus(V1alpha1ImageHub imageHub) throws ApiException {
    Objects.requireNonNull(imageHub.getStatus());
    this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(imageHub),
        imageHub.getStatus());
  }

}
