package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ClusterVolume;

/**
 * ClusterVolume 소유자 라벨 → 자식(PVC/PV) 미러링 컨트롤러. 큐의 키는 CV 이름이며 세 가지 이벤트가
 * 모인다: CV 의 소유자 라벨 변경(transfer), 자식 생성/라벨 변경(재생성·사후 라벨링·임의 수정),
 * 기동 시 CV onAdd(기존 자식 소급).
 */
public class ClusterVolumeChildLabelControllerFactory implements ControllerFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;
  private final ClusterVolumeChildLabelPatcher patcher;

  public ClusterVolumeChildLabelControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
    this.patcher = new CoreV1ClusterVolumeChildLabelPatcher(k8sApiProvider.getApiClient());
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName("clustervolume-child-label-controller")
        .withWorkerCount(1)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1ClusterVolume.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1PersistentVolumeClaim.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1PersistentVolume.class)::hasSynced)
        // allowlist 판정(NamespaceAllowlistResolver)이 Namespace 캐시를 읽는다 — 동기화 전에는
        // 모든 ns 가 "비 allowlist" 로 보여 기동 초기 소급 리컨실이 allowlist ns 의 PVC 에
        // 라벨을 붙일 수 있다
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1Namespace.class)::hasSynced)
        .watch(this::createClusterVolumeWatch)
        .watch(this::createPersistentVolumeClaimWatch)
        .watch(this::createPersistentVolumeWatch)
        .withReconciler(new ClusterVolumeChildLabelReconciler(this.sharedInformerFactory,
            this.namespaceAllowlistResolver, this.patcher))
        .build();
  }

  private ControllerWatch<V1alpha1ClusterVolume> createClusterVolumeWatch(
      WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1ClusterVolume> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1ClusterVolume.class);
    // 소유자 라벨이 바뀐 update(transfer)만 — status 갱신 등은 자식과 무관하다
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.ownerLabelsFilter());
    return watch;
  }

  private ControllerWatch<V1PersistentVolumeClaim> createPersistentVolumeClaimWatch(
      WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1PersistentVolumeClaim> watch = new DefaultControllerWatch<>(workQueue,
        V1PersistentVolumeClaim.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.labelsFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.clusterVolumeChildToClusterVolume());
    return watch;
  }

  private ControllerWatch<V1PersistentVolume> createPersistentVolumeWatch(
      WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1PersistentVolume> watch = new DefaultControllerWatch<>(workQueue,
        V1PersistentVolume.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.labelsFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.clusterVolumeChildToClusterVolume());
    return watch;
  }

}
