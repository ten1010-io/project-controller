package io.ten1010.aipub.projectcontroller.controller.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1WorkloadGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WorkloadGroup 소유자 이전(transfer) 시 구·신 소유자의 개인 Role 이 모두 즉시 재조정되는지 검증한다.
 * 구 소유자 Role 이 큐에 들어가지 않으면 이전된 오브젝트에 대한 update/delete 권한이
 * 다음 백스톱 주기(OwnedObjectRoleResweeper, 10분)까지 남는다.
 */
class WorkloadGroupOwnerTransferWatchTest {

  // 네임스페이스명 = 프로젝트명 (NamespaceNameResolver 가 그대로 매핑). 실 클러스터의 프로젝트 이름을 쓴다.
  private static final String NAMESPACE = "ten-test-project-01";
  private static final String OLD_OWNER = "taehyeong-qa-03";
  private static final String NEW_OWNER = "taehyeong-qa-04";

  private DefaultControllerWatch<V1alpha1WorkloadGroup> watch;
  private List<Request> enqueued;

  private static V1alpha1WorkloadGroup workloadGroup(String name, String owner) {
    V1alpha1WorkloadGroup workloadGroup = new V1alpha1WorkloadGroup();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace(NAMESPACE);
    if (owner != null) {
      meta.setLabels(Map.of(LabelConstants.OBJECT_OWN_USERNAME_KEY, owner));
    }
    workloadGroup.setMetadata(meta);
    return workloadGroup;
  }

  /**
   * 기대 Request 를 오브젝트에서 유도한다 — 네임스페이스는 오브젝트 메타데이터에서 꺼내고,
   * Role 이름은 프로덕션 리졸버로 만든다. 상수를 다시 비교하면 전파를 검증하지 못한다.
   */
  private static Request roleRequest(V1alpha1WorkloadGroup workloadGroup, String owner) {
    return new Request(K8sObjectUtils.getNamespace(workloadGroup),
        new AipubUserRoleNameResolver().resolveRoleName(owner));
  }

  @BeforeEach
  void setUp() {
    this.enqueued = new ArrayList<>();
    WorkQueue<Request> queue = new WorkQueue<>() {

      @Override
      public void add(Request item) {
        WorkloadGroupOwnerTransferWatchTest.this.enqueued.add(item);
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
        return WorkloadGroupOwnerTransferWatchTest.this.enqueued.size();
      }

      @Override
      public void shutDown() {
      }

      @Override
      public boolean isShuttingDown() {
        return false;
      }

    };
    RequestBuilderFactory requestBuilderFactory =
        new RequestBuilderFactory(mock(SharedInformerFactory.class, RETURNS_DEEP_STUBS));
    this.watch = new DefaultControllerWatch<>(queue, V1alpha1WorkloadGroup.class);
    this.watch.setOnUpdateFilter(new OnUpdateFilterFactory().workloadGroupFilter());
    this.watch.setRequestBuilder(requestBuilderFactory.workloadGroupToAipubUserRoles());
  }

  @Test
  @DisplayName("소유자 라벨이 바뀌면 구 소유자와 신 소유자 Role 이 모두 큐에 들어간다")
  void ownerLabelChanged_enqueuesBothOwnerRoles() {
    V1alpha1WorkloadGroup before = workloadGroup("wg1", OLD_OWNER);
    V1alpha1WorkloadGroup after = workloadGroup("wg1", NEW_OWNER);

    this.watch.getResourceEventHandler().onUpdate(before, after);

    assertThat(this.enqueued).containsExactlyInAnyOrder(
        roleRequest(before, OLD_OWNER), roleRequest(after, NEW_OWNER));
  }

  @Test
  @DisplayName("요청 네임스페이스는 오브젝트의 네임스페이스에서 나온다")
  void requestNamespace_comesFromObject() {
    V1alpha1WorkloadGroup other = workloadGroup("wg1", OLD_OWNER);
    Objects.requireNonNull(other.getMetadata()).setNamespace("yshee");

    this.watch.getResourceEventHandler().onAdd(other);

    assertThat(this.enqueued).containsExactly(roleRequest(other, OLD_OWNER));
    assertThat(this.enqueued.get(0).getNamespace()).isEqualTo("yshee");
  }

  @Test
  @DisplayName("status 변경 등 라벨·ownerReference 가 그대로인 update 는 큐에 넣지 않는다")
  void unrelatedUpdate_enqueuesNothing() {
    this.watch.getResourceEventHandler()
        .onUpdate(workloadGroup("wg1", OLD_OWNER), workloadGroup("wg1", OLD_OWNER));

    assertThat(this.enqueued).isEmpty();
  }

  @Test
  @DisplayName("생성·삭제는 해당 소유자 Role 을 큐에 넣는다")
  void addAndDelete_enqueueOwnerRole() {
    V1alpha1WorkloadGroup workloadGroup = workloadGroup("wg1", OLD_OWNER);

    this.watch.getResourceEventHandler().onAdd(workloadGroup);
    this.watch.getResourceEventHandler().onDelete(workloadGroup, false);

    assertThat(this.enqueued).containsExactly(
        roleRequest(workloadGroup, OLD_OWNER), roleRequest(workloadGroup, OLD_OWNER));
  }

  @Test
  @DisplayName("소유자 라벨이 없는 오브젝트는 큐에 넣을 대상이 없다")
  void unlabeledObject_enqueuesNothing() {
    this.watch.getResourceEventHandler().onAdd(workloadGroup("wg1", null));

    assertThat(this.enqueued).isEmpty();
  }

}
