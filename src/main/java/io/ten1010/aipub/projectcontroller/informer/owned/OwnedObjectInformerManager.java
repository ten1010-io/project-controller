package io.ten1010.aipub.projectcontroller.informer.owned;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.util.CallGeneratorParams;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.OwnedObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.OwnershipPolicy;
import io.ten1010.aipub.projectcontroller.domain.k8s.ResourceTarget;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1PartialObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1PartialObjectList;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.UsernameUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.jspecify.annotations.Nullable;

/**
 * {@link OwnershipPolicy#OWNED_TARGETS} 의 고정 네이티브 타입마다 username 레이블 셀렉터가
 * 걸린 메타데이터 전용 인포머를 유지하고, 소유 오브젝트 변화를 개인 Role 리컨실 큐로
 * 라우팅한다. 리컨실러는 이 매니저의 소유자 인덱스로 특정 사용자의 소유 오브젝트만
 * 조회하여 resourceNames 기반 RBAC 규칙을 생성한다.
 */
@Slf4j
public class OwnedObjectInformerManager {

  /**
   * 소유자 → 오브젝트 인덱스. "{namespace}/{username}" 을 키로 사용한다.
   * 이벤트 한 건이 트리거하는 리컨실이 전체 캐시 풀스캔 없이 해당 소유자의 오브젝트만
   * O(1) 로 조회하기 위한 것이다.
   */
  private static final String OWNER_TO_OBJECTS_INDEXER_NAME = "OWNER_TO_OBJECTS";

  /**
   * SharedInformerFactory 는 인포머를 API 타입 클래스로 캐싱하므로, 모든 대상이
   * V1PartialObject 를 공유하는 이 매니저에서는 타깃마다 전용 팩토리를 만들어야 한다
   * (하나의 팩토리에 16개를 등록하면 첫 인포머 하나로 합쳐져 버린다).
   */
  private record TrackedTarget(ResourceTarget target, SharedInformerFactory informerFactory,
      SharedIndexInformer<V1PartialObject> informer) {
  }

  private final ApiClient apiClient;
  private final AipubUserRoleNameResolver roleNameResolver;
  private final Indexer<V1Role> roleIndexer;
  private final Map<String, TrackedTarget> trackedTargets;

  @Setter
  private volatile WorkQueue<Request> aipubUserRoleWorkQueue;

  public OwnedObjectInformerManager(ApiClient apiClient,
      SharedInformerFactory sharedInformerFactory) {
    this.apiClient = apiClient;
    this.roleNameResolver = new AipubUserRoleNameResolver();
    this.roleIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Role.class)
        .getIndexer();
    this.trackedTargets = new ConcurrentHashMap<>();
  }

  /**
   * 소유권 대상 타입 추적을 시작한다. 인포머의 초기 onAdd 이벤트가 유실되지 않도록
   * 개인 Role 컨트롤러의 워크큐 등록이 끝난 뒤에 호출해야 한다
   * (생성자에서 시작하면 큐 등록 전 이벤트가 버려져, 기동 초기 리컨실이 지운 규칙을
   * 되살릴 트리거가 사라진다).
   */
  public synchronized void start() {
    if (!this.trackedTargets.isEmpty()) {
      return;
    }
    for (ResourceTarget target : OwnershipPolicy.OWNED_TARGETS) {
      registerInformer(target);
    }
    for (TrackedTarget tracked : this.trackedTargets.values()) {
      tracked.informerFactory().startAllRegisteredInformers();
    }
    log.info("Started owned-object informers: targets={}", this.trackedTargets.size());
  }

  /**
   * 모든 개인 Role 을 재큐잉하는 주기 백스톱. 이벤트 경로가 어떤 이유로든 유실되어도
   * (기동 타이밍 레이스, 예기치 못한 드리프트) 다음 주기에 수렴을 보장한다.
   */
  public void resweepPersonalRoles() {
    WorkQueue<Request> queue = this.aipubUserRoleWorkQueue;
    if (queue == null) {
      return;
    }
    for (V1Role role : this.roleIndexer.list()) {
      String name = K8sObjectUtils.getName(role);
      if (this.roleNameResolver.resolveAipubUserName(name).isEmpty()) {
        continue;
      }
      queue.add(new Request(K8sObjectUtils.getNamespace(role), name));
    }
  }

  public List<OwnedObject> getOwnedObjects(String namespace, String aipubUserName) {
    String ownerIndexKey = ownerIndexKey(namespace, aipubUserName);
    List<OwnedObject> owned = new ArrayList<>();
    for (TrackedTarget tracked : this.trackedTargets.values()) {
      for (V1PartialObject obj : tracked.informer().getIndexer()
          .byIndex(OWNER_TO_OBJECTS_INDEXER_NAME, ownerIndexKey)) {
        owned.add(new OwnedObject(tracked.target().group(), tracked.target().plural(),
            K8sObjectUtils.getName(obj)));
      }
    }
    // reconcileExistingRole 이 규칙 리스트를 equals 로 비교하므로 순서가 결정적이어야 한다
    owned.sort(Comparator.comparing(OwnedObject::group)
        .thenComparing(OwnedObject::resource)
        .thenComparing(OwnedObject::name));
    return owned;
  }

  private static String ownerIndexKey(String namespace, String aipubUserName) {
    return namespace + "/" + aipubUserName;
  }

  private void registerInformer(ResourceTarget target) {
    SharedInformerFactory informerFactory = new SharedInformerFactory(this.apiClient);
    SharedIndexInformer<V1PartialObject> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> buildListCall(target, params),
        V1PartialObject.class,
        V1PartialObjectList.class);
    informer.addIndexers(Map.of(
        OWNER_TO_OBJECTS_INDEXER_NAME,
        obj -> UsernameUtils.getUsername(obj)
            .map(username -> {
              String namespace = resolveNamespace(obj);
              return namespace == null
                  ? List.<String>of()
                  : List.of(ownerIndexKey(namespace, username));
            })
            .orElse(List.of())));
    informer.addEventHandler(new ResourceEventHandler<>() {

      @Override
      public void onAdd(V1PartialObject obj) {
        log.info("Owned object created: group={}, resource={}, namespace={}, name={}, owner={}",
            target.group(), target.plural(), resolveNamespace(obj), K8sObjectUtils.getName(obj),
            UsernameUtils.getUsername(obj).orElse(""));
        enqueueOwnerRole(obj);
      }

      @Override
      public void onUpdate(V1PartialObject oldObj, V1PartialObject newObj) {
        // 소유권 규칙은 (소유자, 오브젝트 이름)에만 의존하므로, username 레이블이
        // 안 바뀐 update(status 갱신 등)는 리컨실을 트리거할 이유가 없다
        if (UsernameUtils.getUsername(oldObj).equals(UsernameUtils.getUsername(newObj))) {
          return;
        }
        log.info("Owned object ownership changed: group={}, resource={}, namespace={}, name={}, "
                + "previousOwner={}, owner={}",
            target.group(), target.plural(), resolveNamespace(newObj),
            K8sObjectUtils.getName(newObj),
            UsernameUtils.getUsername(oldObj).orElse(""),
            UsernameUtils.getUsername(newObj).orElse(""));
        enqueueOwnerRole(oldObj);
        enqueueOwnerRole(newObj);
      }

      @Override
      public void onDelete(V1PartialObject obj, boolean deletedFinalStateUnknown) {
        log.info("Owned object deleted: group={}, resource={}, namespace={}, name={}, owner={}",
            target.group(), target.plural(), resolveNamespace(obj), K8sObjectUtils.getName(obj),
            UsernameUtils.getUsername(obj).orElse(""));
        enqueueOwnerRole(obj);
      }

    });
    this.trackedTargets.put(target.group() + "/" + target.plural(),
        new TrackedTarget(target, informerFactory, informer));
  }

  /**
   * 소유권 추적용 레이블 셀렉터: username 레이블이 있고, 시스템 파생물 표식
   * (OwnershipPolicy.SYSTEM_DERIVED_LABEL_KEYS)이 없는 오브젝트만.
   * 서버사이드 필터라 파생물은 캐시·이벤트·로그 어디에도 나타나지 않는다.
   */
  static String ownedObjectsLabelSelector() {
    StringBuilder selector = new StringBuilder(LabelConstants.OBJECT_OWN_USERNAME_KEY);
    for (String key : OwnershipPolicy.SYSTEM_DERIVED_LABEL_KEYS) {
      selector.append(",!").append(key);
    }
    return selector.toString();
  }

  /**
   * core("") 그룹은 /api/v1, 나머지는 /apis/{group}/{version} 의 전체 네임스페이스
   * LIST/WATCH 경로를 사용한다. username 레이블이 있는 오브젝트만 list/watch 한다.
   */
  private Call buildListCall(ResourceTarget target, CallGeneratorParams params) {
    String basePath = target.group().isEmpty()
        ? "/api/" + target.version()
        : "/apis/" + target.group() + "/" + target.version();
    String path = basePath + "/" + target.plural();

    List<Pair> queryParams = new ArrayList<>();
    queryParams.add(new Pair("labelSelector", ownedObjectsLabelSelector()));
    if (params.resourceVersion != null) {
      queryParams.add(new Pair("resourceVersion", params.resourceVersion));
    }
    if (params.watch != null) {
      queryParams.add(new Pair("watch", String.valueOf(params.watch)));
    }
    if (params.timeoutSeconds != null) {
      queryParams.add(new Pair("timeoutSeconds", String.valueOf(params.timeoutSeconds)));
    }

    try {
      return this.apiClient.buildCall(
          this.apiClient.getBasePath(), path, "GET",
          queryParams, List.of(),
          null,
          Map.of(), Map.of(), Map.of(),
          new String[]{"BearerToken"}, null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to build list call: " + path, e);
    }
  }

  private void enqueueOwnerRole(V1PartialObject obj) {
    Optional<String> usernameOpt = UsernameUtils.getUsername(obj);
    if (usernameOpt.isEmpty()) {
      return;
    }
    String roleName;
    try {
      roleName = this.roleNameResolver.resolveRoleName(usernameOpt.get());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid username label value: {}", usernameOpt.get());
      return;
    }
    WorkQueue<Request> queue = this.aipubUserRoleWorkQueue;
    String namespace = resolveNamespace(obj);
    if (queue == null || namespace == null) {
      return;
    }
    queue.add(new Request(namespace, roleName));
  }

  @Nullable
  private static String resolveNamespace(V1PartialObject obj) {
    return obj.getMetadata() == null ? null : obj.getMetadata().getNamespace();
  }

}
