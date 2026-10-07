package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.ten1010.aipub.projectcontroller.domain.k8s.ImageRegistrySecretNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * image registry secret 컨트롤러의 워크큐에 밖에서 요청을 넣는 통로.
 *
 * <p>robot 은 Harbor 오브젝트라 k8s 이벤트가 없다. robot 이 생겨도 Secret 컨트롤러를 깨울 신호가 없어, 바인딩 직후 한 번 헛돌고 나면 재시도
 * 간격인 60초를 꼬박 기다린 뒤에야 Secret 이 생긴다. 그 사이 워크스페이스 커밋이 ImageSecretNotFound 로 실패한다.
 */
@Slf4j
public class ImageRegistrySecretRequestQueue {

  private final ImageRegistrySecretNameResolver secretNameResolver;
  private final NamespaceNameResolver namespaceNameResolver;

  /** 컨트롤러가 만들어질 때 주입된다. 기동 중 enqueue 가 들어올 수 있어 volatile 로 둔다. */
  @Nullable
  private volatile WorkQueue<Request> workQueue;

  public ImageRegistrySecretRequestQueue() {
    this.secretNameResolver = new ImageRegistrySecretNameResolver();
    this.namespaceNameResolver = new NamespaceNameResolver();
  }

  void bind(WorkQueue<Request> workQueue) {
    this.workQueue = Objects.requireNonNull(workQueue);
  }

  /** 해당 프로젝트의 image registry secret 을 즉시 다시 리컨실하게 한다. */
  public void enqueueByProjectName(String projectName) {
    Objects.requireNonNull(projectName);
    WorkQueue<Request> queue = this.workQueue;
    if (queue == null) {
      // 기동 중이라 아직 컨트롤러가 없다. 60초 재시도가 받아 주므로 지연만 남는다.
      log.debug("Image registry secret work queue is not bound yet [project={}]", projectName);
      return;
    }
    queue.add(new Request(
        this.namespaceNameResolver.resolveNamespaceName(projectName),
        this.secretNameResolver.resolveSecretName(projectName)));
  }

}
