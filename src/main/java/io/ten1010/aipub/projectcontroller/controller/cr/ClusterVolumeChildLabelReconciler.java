package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ClusterVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * ClusterVolume(CV) 의 소유자 라벨(username/userid)을 CV 컨트롤러가 만든 자식(복제/앵커 PVC·PV)에
 * 미러링한다. 요청 키는 CV 이름이다.
 *
 * <p>userrelationship 웹훅은 자식 CREATE 시점에 한 번 라벨을 찍지만, 그 뒤 CV 소유권이
 * 이전(transfer)되거나 자식이 사후 라벨링(CSI 가 만든 앵커 PV)·재생성(자가치유)되면 웹훅으로는
 * 따라갈 수 없다. 이 리컨실러가 "자식의 두 소유자 라벨 = CV 의 두 소유자 라벨" 을 불변식으로
 * 유지한다 — CV 에 없는 키는 자식에서도 제거한다.
 *
 * <p>allowlist 네임스페이스 안의 PVC 는 웹훅·개인 Role 리컨실러와 같은 규칙으로 건너뛴다.
 * CV 가 없으면(삭제 중·삭제됨) 자식은 CV finalizer/GC 가 정리하므로 라벨을 건드리지 않는다.
 */
@Slf4j
public class ClusterVolumeChildLabelReconciler extends AbstractReconciler {

  private static final List<String> MIRRORED_LABEL_KEYS = List.of(
      LabelConstants.OBJECT_OWN_USERNAME_KEY,
      LabelConstants.OBJECT_OWN_USERID_KEY);

  private final KeyResolver keyResolver;
  private final Indexer<V1alpha1ClusterVolume> clusterVolumeIndexer;
  private final Indexer<V1PersistentVolumeClaim> persistentVolumeClaimIndexer;
  private final Indexer<V1PersistentVolume> persistentVolumeIndexer;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;
  private final ClusterVolumeChildLabelPatcher patcher;

  public ClusterVolumeChildLabelReconciler(SharedInformerFactory sharedInformerFactory,
      NamespaceAllowlistResolver namespaceAllowlistResolver,
      ClusterVolumeChildLabelPatcher patcher) {
    this.keyResolver = new KeyResolver();
    this.clusterVolumeIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1ClusterVolume.class)
        .getIndexer();
    this.persistentVolumeClaimIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1PersistentVolumeClaim.class)
        .getIndexer();
    this.persistentVolumeIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1PersistentVolume.class)
        .getIndexer();
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
    this.patcher = patcher;
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String clusterVolumeName = request.getName();
    Optional<V1alpha1ClusterVolume> clusterVolumeOpt = Optional.ofNullable(
        this.clusterVolumeIndexer.getByKey(this.keyResolver.resolveKey(clusterVolumeName)));
    if (clusterVolumeOpt.isEmpty()) {
      return new Result(false);
    }
    Map<String, String> clusterVolumeLabels = K8sObjectUtils.getLabels(clusterVolumeOpt.get());

    List<V1PersistentVolumeClaim> claims = this.persistentVolumeClaimIndexer.byIndex(
        IndexerConstants.CLUSTER_VOLUME_OWNER_TO_OBJECTS_INDEXER_NAME, clusterVolumeName);
    for (V1PersistentVolumeClaim claim : claims) {
      String namespace = K8sObjectUtils.getNamespace(claim);
      if (this.namespaceAllowlistResolver.isAllowlisted(namespace)) {
        continue;
      }
      if (K8sObjectUtils.isTerminating(claim)) {
        continue;
      }
      Map<String, @Nullable String> diff = computeLabelDiff(clusterVolumeLabels, claim);
      if (diff.isEmpty()) {
        continue;
      }
      String name = K8sObjectUtils.getName(claim);
      log.info("Syncing ClusterVolume owner labels to PersistentVolumeClaim: clusterVolume={}, "
          + "namespace={}, name={}, labels={}", clusterVolumeName, namespace, name, diff);
      try {
        this.patcher.patchPersistentVolumeClaimLabels(namespace, name, diff);
      } catch (ApiException e) {
        if (e.getCode() != HttpURLConnection.HTTP_NOT_FOUND) {
          throw e;
        }
        // 인덱스와 API 사이에 삭제된 자식 — 다음 이벤트가 인덱스를 갱신하므로 무시한다
      }
    }

    List<V1PersistentVolume> volumes = this.persistentVolumeIndexer.byIndex(
        IndexerConstants.CLUSTER_VOLUME_OWNER_TO_OBJECTS_INDEXER_NAME, clusterVolumeName);
    for (V1PersistentVolume volume : volumes) {
      if (K8sObjectUtils.isTerminating(volume)) {
        continue;
      }
      Map<String, @Nullable String> diff = computeLabelDiff(clusterVolumeLabels, volume);
      if (diff.isEmpty()) {
        continue;
      }
      String name = K8sObjectUtils.getName(volume);
      log.info("Syncing ClusterVolume owner labels to PersistentVolume: clusterVolume={}, "
          + "name={}, labels={}", clusterVolumeName, name, diff);
      try {
        this.patcher.patchPersistentVolumeLabels(name, diff);
      } catch (ApiException e) {
        if (e.getCode() != HttpURLConnection.HTTP_NOT_FOUND) {
          throw e;
        }
      }
    }

    return new Result(false);
  }

  /**
   * 미러 대상 키마다 CV 값과 자식 값이 다르면 (키 → CV 값) 을 담는다. CV 에 키가 없으면 값은 null
   * (merge patch 에서 제거). 같으면 담지 않으므로 비어 있으면 패치할 것이 없다.
   */
  static Map<String, @Nullable String> computeLabelDiff(Map<String, String> clusterVolumeLabels,
      KubernetesObject child) {
    Map<String, String> childLabels = K8sObjectUtils.getLabels(child);
    Map<String, @Nullable String> diff = new HashMap<>();
    for (String key : MIRRORED_LABEL_KEYS) {
      String desired = clusterVolumeLabels.get(key);
      if (!Objects.equals(desired, childLabels.get(key))) {
        diff.put(key, desired);
      }
    }
    return diff;
  }

}
