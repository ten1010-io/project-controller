package io.ten1010.aipub.projectcontroller.controller.cr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ClusterVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ClusterVolumeUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "자식(PVC/PV)의 소유자 라벨 = ClusterVolume 의 소유자 라벨" 불변식을 검증한다. 소유권 이전(transfer)
 * 은 CV 라벨이 바뀐 뒤 같은 요청이 다시 들어오는 것과 같으므로 교체 케이스가 곧 transfer 케이스다.
 */
class ClusterVolumeChildLabelReconcilerTest {

  private static final String CV = "cv-1";
  private static final String USERNAME_KEY = LabelConstants.OBJECT_OWN_USERNAME_KEY;
  private static final String USERID_KEY = LabelConstants.OBJECT_OWN_USERID_KEY;
  private static final String OWNER_KEY = LabelConstants.CLUSTER_VOLUME_OWNER_KEY;

  /** 패치 호출을 기록하는 가짜 패처. 실패를 주입할 수 있다. */
  private static final class RecordingPatcher implements ClusterVolumeChildLabelPatcher {

    record Call(String kind, @Nullable String namespace, String name,
        Map<String, @Nullable String> labels) {
    }

    final List<Call> calls = new ArrayList<>();
    @Nullable
    ApiException failure;

    @Override
    public void patchPersistentVolumeClaimLabels(String namespace, String name,
        Map<String, @Nullable String> labels) throws ApiException {
      this.calls.add(new Call("PersistentVolumeClaim", namespace, name, new HashMap<>(labels)));
      if (this.failure != null) {
        throw this.failure;
      }
    }

    @Override
    public void patchPersistentVolumeLabels(String name, Map<String, @Nullable String> labels)
        throws ApiException {
      this.calls.add(new Call("PersistentVolume", null, name, new HashMap<>(labels)));
      if (this.failure != null) {
        throw this.failure;
      }
    }

  }

  private Cache<V1alpha1ClusterVolume> clusterVolumeCache;
  private Cache<V1PersistentVolumeClaim> claimCache;
  private Cache<V1PersistentVolume> volumeCache;
  private RecordingPatcher patcher;
  private ClusterVolumeChildLabelReconciler reconciler;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.clusterVolumeCache = new Cache<>();
    this.claimCache = new Cache<>();
    this.claimCache.addIndexFunc(IndexerConstants.CLUSTER_VOLUME_OWNER_TO_OBJECTS_INDEXER_NAME,
        obj -> ClusterVolumeUtils.getOwnerClusterVolumeName(obj).map(List::of).orElse(List.of()));
    this.volumeCache = new Cache<>();
    this.volumeCache.addIndexFunc(IndexerConstants.CLUSTER_VOLUME_OWNER_TO_OBJECTS_INDEXER_NAME,
        obj -> ClusterVolumeUtils.getOwnerClusterVolumeName(obj).map(List::of).orElse(List.of()));

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1alpha1ClusterVolume> cvInformer = mock(SharedIndexInformer.class);
    when(cvInformer.getIndexer()).thenReturn(this.clusterVolumeCache);
    when(factory.getExistingSharedIndexInformer(V1alpha1ClusterVolume.class))
        .thenReturn(cvInformer);
    SharedIndexInformer<V1PersistentVolumeClaim> pvcInformer = mock(SharedIndexInformer.class);
    when(pvcInformer.getIndexer()).thenReturn(this.claimCache);
    when(factory.getExistingSharedIndexInformer(V1PersistentVolumeClaim.class))
        .thenReturn(pvcInformer);
    SharedIndexInformer<V1PersistentVolume> pvInformer = mock(SharedIndexInformer.class);
    when(pvInformer.getIndexer()).thenReturn(this.volumeCache);
    when(factory.getExistingSharedIndexInformer(V1PersistentVolume.class)).thenReturn(pvInformer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("allowlisted-ns")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    this.patcher = new RecordingPatcher();
    this.reconciler = new ClusterVolumeChildLabelReconciler(factory,
        new NamespaceAllowlistResolver(namespaceCache), this.patcher);
  }

  private static Map<String, String> ownerLabels(String username, String userid) {
    return Map.of(USERNAME_KEY, username, USERID_KEY, userid);
  }

  private void addClusterVolume(String name, Map<String, String> labels) {
    V1alpha1ClusterVolume cv = new V1alpha1ClusterVolume();
    cv.setMetadata(new V1ObjectMeta().name(name).labels(new HashMap<>(labels)));
    this.clusterVolumeCache.add(cv);
  }

  private V1PersistentVolumeClaim addClaim(String namespace, String name, String owner,
      Map<String, String> extraLabels) {
    Map<String, String> labels = new HashMap<>(extraLabels);
    labels.put(OWNER_KEY, owner);
    V1PersistentVolumeClaim claim = new V1PersistentVolumeClaim()
        .metadata(new V1ObjectMeta().namespace(namespace).name(name).labels(labels));
    this.claimCache.add(claim);
    return claim;
  }

  private V1PersistentVolume addVolume(String name, String owner, Map<String, String> extraLabels) {
    Map<String, String> labels = new HashMap<>(extraLabels);
    labels.put(OWNER_KEY, owner);
    V1PersistentVolume volume = new V1PersistentVolume()
        .metadata(new V1ObjectMeta().name(name).labels(labels));
    this.volumeCache.add(volume);
    return volume;
  }

  private Result reconcile(String clusterVolumeName) {
    return this.reconciler.reconcile(new Request(null, clusterVolumeName));
  }

  @Test
  @DisplayName("라벨 없는 복제 PVC·PV 에 CV 의 username/userid 를 붙인다")
  void childrenMissingLabels_patchesBoth() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", CV, CV, Map.of());
    addVolume("pv-proj-a-" + CV, CV, Map.of());

    Result result = reconcile(CV);

    assertThat(result.isRequeue()).isFalse();
    assertThat(this.patcher.calls).hasSize(2);
    assertThat(this.patcher.calls).extracting(RecordingPatcher.Call::kind)
        .containsExactlyInAnyOrder("PersistentVolumeClaim", "PersistentVolume");
    for (RecordingPatcher.Call call : this.patcher.calls) {
      assertThat(call.labels()).containsOnly(entry(USERNAME_KEY, "alice"), entry(USERID_KEY, "u-1"));
    }
    assertThat(this.patcher.calls.stream()
        .filter(c -> c.kind().equals("PersistentVolumeClaim")).findFirst().orElseThrow().namespace())
        .isEqualTo("proj-a");
  }

  @Test
  @DisplayName("이미 일치하는 자식은 패치하지 않는다 (멱등)")
  void childrenInSync_noPatch() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", CV, CV, ownerLabels("alice", "u-1"));
    addVolume("pv-1", CV, ownerLabels("alice", "u-1"));

    reconcile(CV);

    assertThat(this.patcher.calls).isEmpty();
  }

  @Test
  @DisplayName("transfer: CV 소유자가 바뀌면 자식 라벨을 새 소유자로 교체한다")
  void ownerTransferred_replacesChildLabels() {
    addClusterVolume(CV, ownerLabels("bob", "u-2"));
    addClaim("proj-a", CV, CV, ownerLabels("alice", "u-1"));
    addClaim("proj-b", CV, CV, ownerLabels("alice", "u-1"));

    reconcile(CV);

    assertThat(this.patcher.calls).hasSize(2);
    for (RecordingPatcher.Call call : this.patcher.calls) {
      assertThat(call.labels()).containsOnly(entry(USERNAME_KEY, "bob"), entry(USERID_KEY, "u-2"));
    }
  }

  @Test
  @DisplayName("CV 에 소유자 라벨이 없으면 자식의 라벨을 제거한다 (merge patch null)")
  void clusterVolumeWithoutOwnerLabels_removesChildLabels() {
    addClusterVolume(CV, Map.of());
    addClaim("proj-a", CV, CV, ownerLabels("alice", "u-1"));

    reconcile(CV);

    assertThat(this.patcher.calls).hasSize(1);
    Map<String, @Nullable String> labels = this.patcher.calls.get(0).labels();
    assertThat(labels).containsOnlyKeys(USERNAME_KEY, USERID_KEY);
    assertThat(labels.get(USERNAME_KEY)).isNull();
    assertThat(labels.get(USERID_KEY)).isNull();
  }

  @Test
  @DisplayName("한 키만 다르면 그 키만 패치한다")
  void partialDrift_patchesOnlyDriftedKey() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", CV, CV, Map.of(USERNAME_KEY, "alice", USERID_KEY, "stale"));

    reconcile(CV);

    assertThat(this.patcher.calls).hasSize(1);
    assertThat(this.patcher.calls.get(0).labels()).containsOnly(entry(USERID_KEY, "u-1"));
  }

  @Test
  @DisplayName("CV 가 없으면(삭제 중·삭제됨) 자식을 건드리지 않는다")
  void clusterVolumeNotFound_noPatch() {
    addClaim("proj-a", CV, CV, ownerLabels("alice", "u-1"));

    Result result = reconcile(CV);

    assertThat(result.isRequeue()).isFalse();
    assertThat(this.patcher.calls).isEmpty();
  }

  @Test
  @DisplayName("다른 CV 의 자식은 건드리지 않는다")
  void childOfOtherClusterVolume_untouched() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", "other", "other-cv", Map.of());
    addVolume("pv-other", "other-cv", Map.of());

    reconcile(CV);

    assertThat(this.patcher.calls).isEmpty();
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 PVC 는 건너뛴다 (웹훅·개인 Role 리컨실러와 같은 규칙); PV 는 처리")
  void claimInAllowlistedNamespace_skipped() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("allowlisted-ns", CV, CV, Map.of());
    addVolume("pv-anchor", CV, Map.of());

    reconcile(CV);

    assertThat(this.patcher.calls).hasSize(1);
    assertThat(this.patcher.calls.get(0).kind()).isEqualTo("PersistentVolume");
  }

  @Test
  @DisplayName("삭제 중(deletionTimestamp)인 자식은 건너뛴다")
  void terminatingChild_skipped() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    V1PersistentVolumeClaim claim = addClaim("proj-a", CV, CV, Map.of());
    claim.getMetadata().setDeletionTimestamp(OffsetDateTime.now());

    reconcile(CV);

    assertThat(this.patcher.calls).isEmpty();
  }

  @Test
  @DisplayName("패치 404(인덱스와 API 사이에 삭제)는 무시하고 성공으로 끝낸다")
  void patchNotFound_ignored() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", CV, CV, Map.of());
    this.patcher.failure = new ApiException(404, "not found");

    Result result = reconcile(CV);

    assertThat(result.isRequeue()).isFalse();
  }

  @Test
  @DisplayName("패치가 다른 오류로 실패하면 재큐잉한다 (AbstractReconciler 계약)")
  void patchFailure_requeues() {
    addClusterVolume(CV, ownerLabels("alice", "u-1"));
    addClaim("proj-a", CV, CV, Map.of());
    this.patcher.failure = new ApiException(500, "boom");

    Result result = reconcile(CV);

    assertThat(result.isRequeue()).isTrue();
  }

}
