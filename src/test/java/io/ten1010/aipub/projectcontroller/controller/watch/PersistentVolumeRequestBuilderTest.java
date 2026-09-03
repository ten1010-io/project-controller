package io.ten1010.aipub.projectcontroller.controller.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectRoleEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.RoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PV 이벤트 → 프로젝트 역할 ClusterRole 큐잉(AIP-34). 큐잉 조건이
 * {@code PersistentVolumeUtils.isUnclaimed} 보다 느슨하면 ClusterRole 을 바꾸지도 못하는
 * 리컨실이 프로젝트 수만큼 돌고, 이 컨트롤러는 워커가 1개라 뒤에 줄 선 권한 리컨실이 밀린다.
 */
class PersistentVolumeRequestBuilderTest {

  private static final String OWNER_PROJECT = "pjw";
  private static final String OTHER_PROJECT = "hk-test";

  private Function<V1PersistentVolume, List<Request>> requestBuilder;

  private static V1alpha1Project project(String name) {
    V1alpha1Project project = new V1alpha1Project();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    project.setMetadata(meta);
    return project;
  }

  private static V1PersistentVolume persistentVolume(V1ObjectReference claimRef, String phase) {
    V1PersistentVolume pv = new V1PersistentVolume();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("pv-1");
    pv.setMetadata(meta);
    if (claimRef != null) {
      V1PersistentVolumeSpec spec = new V1PersistentVolumeSpec();
      spec.setClaimRef(claimRef);
      pv.setSpec(spec);
    }
    V1PersistentVolumeStatus status = new V1PersistentVolumeStatus();
    status.setPhase(phase);
    pv.setStatus(status);
    return pv;
  }

  private static V1ObjectReference claimRef(String namespace) {
    V1ObjectReference ref = new V1ObjectReference();
    ref.setName("some-pvc");
    ref.setNamespace(namespace);
    return ref;
  }

  /** 기대 Request 는 프로덕션 리졸버로 만든다 — 상수를 다시 비교하면 전파를 검증하지 못한다. */
  private static List<Request> roleRequests(String projectName) {
    RoleNameResolver resolver = new RoleNameResolver();
    return List.of(
        new Request(resolver.resolveRoleName(projectName, ProjectRoleEnum.PROJECT_MANAGER)),
        new Request(resolver.resolveRoleName(projectName, ProjectRoleEnum.PROJECT_DEVELOPER)));
  }

  @BeforeEach
  void setUp() {
    SharedInformerFactory sharedInformerFactory =
        mock(SharedInformerFactory.class, RETURNS_DEEP_STUBS);
    V1alpha1Project owner = project(OWNER_PROJECT);
    when(sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer().list()).thenReturn(List.of(owner, project(OTHER_PROJECT)));
    when(sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer().getByKey(anyString())).thenReturn(null);
    when(sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer().getByKey(new KeyResolver().resolveKey(OWNER_PROJECT))).thenReturn(owner);
    this.requestBuilder =
        new RequestBuilderFactory(sharedInformerFactory).persistentVolumeToProjectRoles(false);
  }

  @Test
  @DisplayName("주인 없는 Available PV 는 모든 프로젝트 역할을 큐에 넣는다")
  void unclaimedAvailablePv_enqueuesEveryProjectRole() {
    List<Request> requests = this.requestBuilder.apply(persistentVolume(null, "Available"));

    assertThat(requests).containsExactlyInAnyOrderElementsOf(
        java.util.stream.Stream.concat(
            roleRequests(OWNER_PROJECT).stream(), roleRequests(OTHER_PROJECT).stream()).toList());
  }

  @Test
  @DisplayName("claimRef 없이 Available 이 아닌 PV 는 아무것도 큐에 넣지 않는다")
  void unavailablePvWithoutClaimRef_enqueuesNothing() {
    // 정적 PV 를 대량 등록하면 전부 Pending 으로 태어난다. 여기서 걸러야 등록 배치가
    // 전 프로젝트 리컨실로 번지지 않는다.
    assertThat(this.requestBuilder.apply(persistentVolume(null, "Pending"))).isEmpty();
    assertThat(this.requestBuilder.apply(persistentVolume(null, "Released"))).isEmpty();
    assertThat(this.requestBuilder.apply(persistentVolume(null, "Failed"))).isEmpty();
  }

  @Test
  @DisplayName("namespace 없는 claimRef 를 가진 PV 는 아무것도 큐에 넣지 않는다")
  void claimRefWithoutNamespace_enqueuesNothing() {
    // 어느 프로젝트에도 매핑되지 않으므로 조회 집합에 들어갈 수 없다.
    assertThat(this.requestBuilder.apply(persistentVolume(claimRef(null), "Available"))).isEmpty();
  }

  @Test
  @DisplayName("바인딩된 PV 는 claimRef.namespace 프로젝트의 역할만 큐에 넣는다")
  void boundPv_enqueuesOnlyOwningProjectRoles() {
    List<Request> requests =
        this.requestBuilder.apply(persistentVolume(claimRef(OWNER_PROJECT), "Bound"));

    assertThat(requests).containsExactlyInAnyOrderElementsOf(roleRequests(OWNER_PROJECT));
    assertThat(requests).doesNotContainAnyElementsOf(roleRequests(OTHER_PROJECT));
  }

  @Test
  @DisplayName("없는 프로젝트를 가리키는 claimRef 는 아무것도 큐에 넣지 않는다")
  void boundPvOfUnknownProject_enqueuesNothing() {
    assertThat(this.requestBuilder.apply(persistentVolume(claimRef("no-such-ns"), "Bound")))
        .isEmpty();
  }

  @Test
  @DisplayName("Available → Bound 전이는 old/new 합집합으로 이전 대상까지 갱신한다")
  void availableToBoundTransition_enqueuesUnionOfOldAndNew() {
    // 권한 회수가 이 합집합에 달려 있다 — old 를 빼면 비소유 프로젝트가 이름을 계속 들고 있다.
    V1PersistentVolume before = persistentVolume(null, "Available");
    V1PersistentVolume after = persistentVolume(claimRef(OWNER_PROJECT), "Bound");

    List<Request> union = java.util.stream.Stream.concat(
        this.requestBuilder.apply(before).stream(),
        this.requestBuilder.apply(after).stream()).distinct().toList();

    assertThat(union).containsExactlyInAnyOrderElementsOf(
        java.util.stream.Stream.concat(
            roleRequests(OWNER_PROJECT).stream(), roleRequests(OTHER_PROJECT).stream()).toList());
    assertThat(new OnUpdateFilterFactory().persistentVolumeClaimAndPhaseFilter()
        .test(before, after)).isTrue();
  }

}
