package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.ImageHubNotConnectedException;
import io.ten1010.aipub.projectcontroller.domain.k8s.ImageRegistrySecretNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageRegistrySecretReconciler extends AbstractReconciler {

  private static final Logger log = LoggerFactory.getLogger(ImageRegistrySecretReconciler.class);

  private final KeyResolver keyResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final ImageRegistrySecretNameResolver secretNameResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1Namespace> namespaceIndexer;
  private final Indexer<V1Secret> secretIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final CoreV1Api coreV1Api;

  public ImageRegistrySecretReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.keyResolver = new KeyResolver();
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.secretNameResolver = new ImageRegistrySecretNameResolver();
    this.reconciliationService = reconciliationService;
    this.namespaceIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Namespace.class)
        .getIndexer();
    this.secretIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Secret.class)
        .getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    Optional<String> projNameOpt = this.secretNameResolver.resolveProjectName(request.getName());
    if (projNameOpt.isEmpty()) {
      return new Result(false);
    }
    String projName = projNameOpt.get();

    String secretKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<V1Secret> secretOpt = Optional.ofNullable(this.secretIndexer.getByKey(secretKey));
    String projKey = this.keyResolver.resolveKey(projName);
    Optional<V1alpha1Project> projectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(projKey));
    Optional<V1Namespace> namespaceOpt = Optional.ofNullable(this.namespaceIndexer.getByKey(projKey));

    if (namespaceOpt.isEmpty()) {
      return new Result(false);
    }

    if (projectOpt.isEmpty()) {
      if (secretOpt.isPresent()) {
        deleteSecret(secretOpt.get());
        return new Result(false);
      }
      return new Result(false);
    }

    List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(
        secretOpt.orElse(null), projectOpt.get());
    String reconciledType = this.reconciliationService.reconcileImageRegistrySecretType(
        projectOpt.get());

    if (secretOpt.isPresent()) {
      String projNameFromSecretName = projNameOpt.get();
      String secretNamespace = K8sObjectUtils.getNamespace(secretOpt.get());
      Map<String, String> reconciledLabels = this.reconciliationService.reconcileSecretLabels(
          namespaceOpt.get(), projectOpt.get());
      String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(secretNamespace);
      if (!projNameFromSecretName.equals(projNameFromNamespace)) {
        deleteSecret(secretOpt.get());
        return new Result(false);
      }

      Optional<String> currentRobotIdOpt = this.reconciliationService.resolveImageRegistryRobotId(
          projectOpt.get());
      if (currentRobotIdOpt.isEmpty()) {
        return logImageHubNotConnectedAndRequeue(projName);
      }
      String currentRobotId = currentRobotIdOpt.get();
      String existingRobotId = K8sObjectUtils.getAnnotations(secretOpt.get())
          .get(LabelConstants.IMAGE_REGISTRY_ROBOT_ID_KEY);

      Map<String, byte[]> reconciledData;
      if (currentRobotId.equals(existingRobotId)) {
        // robot 이 그대로면 비밀번호도 여전히 유효하다 — refreshSecret 을 또 부를 필요 없이
        // 기존 Secret 데이터를 그대로 재사용한다. permission(bindingImageHubs) 변경은 robot
        // 을 재생성하지 않으므로, 이 비교만으로 "재발급이 필요한가"를 판단할 수 있다.
        reconciledData = secretOpt.get().getData();
      } else {
        try {
          reconciledData = this.reconciliationService.reconcileImageRegistrySecretData(
              projectOpt.get());
        } catch (ImageHubNotConnectedException e) {
          return logImageHubNotConnectedAndRequeue(projName);
        }
      }
      Map<String, String> reconciledAnnotations = new HashMap<>(
          K8sObjectUtils.getAnnotations(secretOpt.get()));
      reconciledAnnotations.put(LabelConstants.IMAGE_REGISTRY_ROBOT_ID_KEY, currentRobotId);

      return reconcileExistingSecret(secretOpt.get(), reconciledReferences, reconciledType,
          reconciledData, reconciledLabels, reconciledAnnotations);
    }

    if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
      Map<String, byte[]> reconciledData;
      try {
        reconciledData = this.reconciliationService.reconcileImageRegistrySecretData(
            projectOpt.get());
      } catch (ImageHubNotConnectedException e) {
        return logImageHubNotConnectedAndRequeue(projName);
      }
      Map<String, String> reconciledLabels = this.reconciliationService.reconcileSecretLabels(
          namespaceOpt.get(), projectOpt.get());
      Map<String, String> reconciledAnnotations = this.reconciliationService
          .resolveImageRegistryRobotId(projectOpt.get())
          .map(id -> Map.of(LabelConstants.IMAGE_REGISTRY_ROBOT_ID_KEY, id))
          .orElse(Map.of());
      return reconcileNoExistingSecret(request.getNamespace(), request.getName(),
          reconciledReferences, reconciledType, reconciledData, reconciledLabels,
          reconciledAnnotations);
    }

    return new Result(false);
  }

  private Result logImageHubNotConnectedAndRequeue(String projName) {
    log.warn("ImageHub is not connected to the project [name={}]. "
        + "Skipping image registry secret reconciliation until it is connected.", projName);
    return new Result(true, getGeneralFailRequeueDuration());
  }

  private Result reconcileNoExistingSecret(
      String namespace,
      String objName,
      List<V1OwnerReference> reconciledReferences,
      String reconciledType,
      Map<String, byte[]> reconciledData,
      Map<String, String> reconciledLabels,
      Map<String, String> reconciledAnnotations) throws ApiException {
    V1Secret secret = new V1SecretBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(objName)
        .withOwnerReferences(reconciledReferences)
        .withLabels(reconciledLabels)
        .withAnnotations(reconciledAnnotations)
        .endMetadata()
        .withType(reconciledType)
        .withData(reconciledData)
        .build();
    createSecret(namespace, secret);

    return new Result(false);
  }

  private Result reconcileExistingSecret(
      V1Secret existing,
      List<V1OwnerReference> reconciledReferences,
      String reconciledType,
      Map<String, byte[]> reconciledData,
      Map<String, String> reconciledLabels,
      Map<String, String> reconciledAnnotations) throws ApiException {
    if (Set.copyOf(K8sObjectUtils.getOwnerReferences(existing))
        .equals(Set.copyOf(reconciledReferences)) &&
        Objects.equals(existing.getType(), reconciledType) &&
        Objects.equals(existing.getData(), reconciledData) &&
        Objects.equals(K8sObjectUtils.getAnnotations(existing), reconciledAnnotations)) {
      return new Result(false);
    }
    V1Secret edited = new V1SecretBuilder(existing)
        .editMetadata()
        .withOwnerReferences(reconciledReferences)
        .withLabels(reconciledLabels)
        .withAnnotations(reconciledAnnotations)
        .endMetadata()
        .withType(reconciledType)
        .withData(reconciledData)
        .build();
    updateSecret(K8sObjectUtils.getNamespace(existing), K8sObjectUtils.getName(existing), edited);

    return new Result(false);
  }

  private void createSecret(String namespace, V1Secret secret) throws ApiException {
    this.coreV1Api
        .createNamespacedSecret(namespace, secret)
        .execute();
  }

  private void updateSecret(String namespace, String objName, V1Secret secret) throws ApiException {
    this.coreV1Api
        .replaceNamespacedSecret(objName, namespace, secret)
        .execute();
  }

  private void deleteSecret(String namespace, String objName) throws ApiException {
    this.coreV1Api
        .deleteNamespacedSecret(objName, namespace)
        .execute();
  }

  private void deleteSecret(V1Secret object) throws ApiException {
    deleteSecret(K8sObjectUtils.getNamespace(object), K8sObjectUtils.getName(object));
  }

}
