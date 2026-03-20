package io.ten1010.aipub.projectcontroller.controller.transfer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.domain.k8s.AnnotationConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferReconciler<T extends KubernetesObject> extends AbstractReconciler {

  private static final Logger logger = LoggerFactory.getLogger(TransferReconciler.class);

  private final Indexer<T> indexer;
  private final TransferService transferService;
  private final ResourceUpdater<T> resourceUpdater;
  private final KeyResolver keyResolver;
  private final String resourceTypeName;

  public TransferReconciler(
      SharedInformerFactory sharedInformerFactory,
      Class<T> resourceClass,
      TransferService transferService,
      ResourceUpdater<T> resourceUpdater,
      String resourceTypeName) {
    this.indexer = sharedInformerFactory
        .getExistingSharedIndexInformer(resourceClass)
        .getIndexer();
    this.transferService = transferService;
    this.resourceUpdater = resourceUpdater;
    this.keyResolver = new KeyResolver();
    this.resourceTypeName = resourceTypeName;
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String key = this.keyResolver.resolveKey(request.getNamespace(), request.getName());
    T obj = this.indexer.getByKey(key);
    if (obj == null) {
      logger.debug("{} [{}] not found while reconciling transfer", this.resourceTypeName, key);
      return new Result(false);
    }

    Map<String, String> annotations = K8sObjectUtils.getAnnotations(obj);
    String targetUserName = annotations.get(AnnotationConstants.USER_TRANSFER_KEY);
    if (targetUserName == null) {
      return new Result(false);
    }

    logger.info("Processing transfer of {} [{}] to user [{}]",
        this.resourceTypeName, key, targetUserName);

    V1alpha1AipubUser targetUser = this.transferService.getAipubUser(targetUserName);
    if (targetUser == null) {
      logger.warn("AipubUser [{}] not found for transfer of {} [{}]",
          targetUserName, this.resourceTypeName, key);
      return new Result(false);
    }
    if (targetUser.getSpec() == null || targetUser.getSpec().getId() == null) {
      logger.warn("AipubUser [{}] has no user id", targetUserName);
      return new Result(false);
    }

    // Read old username-v2 label before applying transfer
    String oldUsername = K8sObjectUtils.getLabels(obj)
        .get(LabelConstants.OBJECT_OWN_USERNAME_V2_KEY);

    this.transferService.applyTransferMetadata(obj, targetUser);
    this.resourceUpdater.update(obj);
    logger.info("Transferred {} [{}] to user [{}]",
        this.resourceTypeName, key, targetUserName);

    // Cascade to all objects with the same old username-v2 label
    if (oldUsername != null) {
      this.transferService.cascadeTransferByUsernameV2Label(obj, oldUsername, targetUser);
    }

    return new Result(false);
  }

}
