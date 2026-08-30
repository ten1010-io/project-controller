package io.ten1010.aipub.projectcontroller.controller.cr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ClusterVolume;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ClusterVolume 자식 라벨 컨트롤러의 두 워치가 올바른 키(CV 이름, namespace null)를 큐에 넣는지 검증한다.
 * CV 의 소유자 라벨 변경(transfer)이 큐에 들어가지 않으면 자식 라벨이 stale 로 남는다.
 */
class ClusterVolumeChildLabelWatchTest {

  private static final String CV = "cv-1";

  private List<Request> enqueued;
  private DefaultControllerWatch<V1alpha1ClusterVolume> clusterVolumeWatch;
  private DefaultControllerWatch<V1PersistentVolumeClaim> claimWatch;
  private DefaultControllerWatch<V1PersistentVolume> volumeWatch;

  private static V1alpha1ClusterVolume clusterVolume(String owner) {
    V1alpha1ClusterVolume cv = new V1alpha1ClusterVolume();
    V1ObjectMeta meta = new V1ObjectMeta().name(CV);
    if (owner != null) {
      meta.setLabels(Map.of(LabelConstants.OBJECT_OWN_USERNAME_KEY, owner,
          LabelConstants.OBJECT_OWN_USERID_KEY, owner + "-id"));
    }
    cv.setMetadata(meta);
    return cv;
  }

  private static V1PersistentVolumeClaim claim(String ownerClusterVolume,
      Map<String, String> extraLabels) {
    Map<String, String> labels = new HashMap<>(extraLabels);
    if (ownerClusterVolume != null) {
      labels.put(LabelConstants.CLUSTER_VOLUME_OWNER_KEY, ownerClusterVolume);
    }
    return new V1PersistentVolumeClaim()
        .metadata(new V1ObjectMeta().namespace("proj-a").name("pvc-1").labels(labels));
  }

  @BeforeEach
  void setUp() {
    this.enqueued = new ArrayList<>();
    WorkQueue<Request> queue = new WorkQueue<>() {

      @Override
      public void add(Request item) {
        ClusterVolumeChildLabelWatchTest.this.enqueued.add(item);
      }

      @Override
      public Request get() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void done(Request item) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int length() {
        return ClusterVolumeChildLabelWatchTest.this.enqueued.size();
      }

      @Override
      public void shutDown() {
      }

      @Override
      public boolean isShuttingDown() {
        return false;
      }

    };
    OnUpdateFilterFactory filterFactory = new OnUpdateFilterFactory();
    RequestBuilderFactory requestBuilderFactory =
        new RequestBuilderFactory(mock(SharedInformerFactory.class, RETURNS_DEEP_STUBS));

    this.clusterVolumeWatch = new DefaultControllerWatch<>(queue, V1alpha1ClusterVolume.class);
    this.clusterVolumeWatch.setOnUpdateFilter(filterFactory.ownerLabelsFilter());

    this.claimWatch = new DefaultControllerWatch<>(queue, V1PersistentVolumeClaim.class);
    this.claimWatch.setOnUpdateFilter(filterFactory.labelsFilter());
    this.claimWatch.setRequestBuilder(requestBuilderFactory.clusterVolumeChildToClusterVolume());

    this.volumeWatch = new DefaultControllerWatch<>(queue, V1PersistentVolume.class);
    this.volumeWatch.setOnUpdateFilter(filterFactory.labelsFilter());
    this.volumeWatch.setRequestBuilder(requestBuilderFactory.clusterVolumeChildToClusterVolume());
  }

  private static V1PersistentVolume volume(String ownerClusterVolume) {
    Map<String, String> labels = new HashMap<>();
    if (ownerClusterVolume != null) {
      labels.put(LabelConstants.CLUSTER_VOLUME_OWNER_KEY, ownerClusterVolume);
    }
    return new V1PersistentVolume().metadata(new V1ObjectMeta().name("pv-1").labels(labels));
  }

  @Test
  @DisplayName("CV 소유자 라벨 변경(transfer)은 CV 이름을 큐에 넣는다")
  void clusterVolumeOwnerChanged_enqueuesClusterVolume() {
    this.clusterVolumeWatch.getResourceEventHandler()
        .onUpdate(clusterVolume("alice"), clusterVolume("bob"));

    assertThat(this.enqueued).containsExactly(new Request(null, CV));
  }

  @Test
  @DisplayName("소유자 라벨이 그대로인 CV update(status 등)는 큐에 넣지 않는다")
  void clusterVolumeUnrelatedUpdate_enqueuesNothing() {
    this.clusterVolumeWatch.getResourceEventHandler()
        .onUpdate(clusterVolume("alice"), clusterVolume("alice"));

    assertThat(this.enqueued).isEmpty();
  }

  @Test
  @DisplayName("CV 생성(기동 시 onAdd 포함)은 큐에 넣는다 — 기존 자식 소급 경로")
  void clusterVolumeAdded_enqueues() {
    this.clusterVolumeWatch.getResourceEventHandler().onAdd(clusterVolume("alice"));

    assertThat(this.enqueued).containsExactly(new Request(null, CV));
  }

  @Test
  @DisplayName("owner 라벨을 가진 자식 PVC 생성은 부모 CV 이름을 큐에 넣는다")
  void claimAdded_enqueuesOwnerClusterVolume() {
    this.claimWatch.getResourceEventHandler().onAdd(claim(CV, Map.of()));

    assertThat(this.enqueued).containsExactly(new Request(null, CV));
  }

  @Test
  @DisplayName("owner 라벨이 없는 PVC 는 큐에 넣지 않는다")
  void claimWithoutOwnerLabel_enqueuesNothing() {
    this.claimWatch.getResourceEventHandler().onAdd(claim(null, Map.of()));

    assertThat(this.enqueued).isEmpty();
  }

  @Test
  @DisplayName("자식 PVC 의 라벨 변경(임의 수정 등)은 부모 CV 를 큐에 넣고, 라벨이 그대로면 넣지 않는다")
  void claimLabelsChanged_enqueuesOwnerClusterVolume() {
    this.claimWatch.getResourceEventHandler().onUpdate(
        claim(CV, Map.of(LabelConstants.OBJECT_OWN_USERNAME_KEY, "alice")),
        claim(CV, Map.of()));
    assertThat(this.enqueued).containsExactly(new Request(null, CV));

    this.enqueued.clear();
    this.claimWatch.getResourceEventHandler().onUpdate(claim(CV, Map.of()), claim(CV, Map.of()));
    assertThat(this.enqueued).isEmpty();
  }

  @Test
  @DisplayName("CV 소유자 라벨이 제거되는 update 도 큐에 넣는다 (자식 라벨 제거 경로)")
  void clusterVolumeOwnerRemoved_enqueuesClusterVolume() {
    this.clusterVolumeWatch.getResourceEventHandler()
        .onUpdate(clusterVolume("alice"), clusterVolume(null));

    assertThat(this.enqueued).containsExactly(new Request(null, CV));
  }

  @Test
  @DisplayName("PV 워치도 owner 라벨 값으로 부모 CV 를 큐에 넣고, 라벨 없는 PV 는 무시한다")
  void volumeAdded_enqueuesOwnerClusterVolume() {
    this.volumeWatch.getResourceEventHandler().onAdd(volume(CV));
    assertThat(this.enqueued).containsExactly(new Request(null, CV));

    this.enqueued.clear();
    this.volumeWatch.getResourceEventHandler().onAdd(volume(null));
    assertThat(this.enqueued).isEmpty();
  }

}
