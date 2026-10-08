package io.ten1010.aipub.projectcontroller.controller.namespaced;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImageRegistrySecretRequestQueueTest {

  @SuppressWarnings("unchecked")
  @Test
  void enqueue_targetsTheProjectNamespaceAndSecretName() {
    WorkQueue<Request> workQueue = mock(WorkQueue.class);
    ImageRegistrySecretRequestQueue queue = new ImageRegistrySecretRequestQueue();
    queue.bind(workQueue);

    queue.enqueueByProjectName("abc");

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(workQueue).add(captor.capture());
    assertThat(captor.getValue().getNamespace()).isEqualTo("abc");
    assertThat(captor.getValue().getName())
        .isEqualTo("image-registry-secret-project-aipub-ten1010-io-abc");
  }

  /** 기동 중에는 컨트롤러가 아직 없을 수 있다. 여기서 터지면 robot 생성 자체가 실패한다. */
  @Test
  void enqueueBeforeBind_isIgnoredInsteadOfThrowing() {
    ImageRegistrySecretRequestQueue queue = new ImageRegistrySecretRequestQueue();

    assertThatCode(() -> queue.enqueueByProjectName("abc")).doesNotThrowAnyException();
  }

}
