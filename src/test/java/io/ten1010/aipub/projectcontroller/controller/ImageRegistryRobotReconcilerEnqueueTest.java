package io.ten1010.aipub.projectcontroller.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.controller.namespaced.ImageRegistrySecretRequestQueue;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotSecretStore;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotUsernameResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotCreated;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHubSpec;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectBinding;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageRegistryRobotReconcilerEnqueueTest {

  private static final String PROJECT = "abc";
  private static final String IMAGE_HUB = "hub-1";
  private static final String USERNAME = "robot$project-aipub-ten1010-io-" + PROJECT;

  private ImageRegistryRobotService robotService;
  private ImageRegistrySecretRequestQueue secretRequestQueue;
  private Indexer<V1alpha1Project> projectIndexer;
  private Indexer<V1alpha1ImageHub> imageHubIndexer;
  private ImageRegistryRobotReconciler reconciler;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.robotService = mock(ImageRegistryRobotService.class);
    this.secretRequestQueue = mock(ImageRegistrySecretRequestQueue.class);
    this.projectIndexer = mock(Indexer.class);
    this.imageHubIndexer = mock(Indexer.class);

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    registerIndexer(factory, V1alpha1Project.class, this.projectIndexer);
    registerIndexer(factory, V1alpha1ImageHub.class, this.imageHubIndexer);

    ImageRegistryRobotUsernameResolver usernameResolver = name ->
        "robot$project-aipub-ten1010-io-" + name;
    this.reconciler = new ImageRegistryRobotReconciler(this.robotService, usernameResolver,
        new ImageRegistryRobotSecretStore(), this.secretRequestQueue, factory);
  }

  /**
   * robot 은 Harbor 오브젝트라 k8s 이벤트가 없다. 여기서 깨우지 않으면 Secret 컨트롤러가 재시도 간격인 60초를 기다리고, 그 사이 워크스페이스 커밋이
   * ImageSecretNotFound 로 실패한다.
   */
  @Test
  void robotCreated_enqueuesTheSecretRequestImmediately() {
    givenProjectBoundTo(IMAGE_HUB);
    when(this.robotService.listImageRegistryRobots(any())).thenReturn(List.of());
    when(this.robotService.createImageRegistryRobot(any()))
        .thenReturn(Optional.of(new ImageRegistryRobotCreated()));

    this.reconciler.reconcile(new Request(PROJECT));

    verify(this.secretRequestQueue).enqueueByProjectName(PROJECT);
  }

  /** 이미 있는 robot 은 비밀번호가 그대로라 Secret 을 다시 만들 이유가 없다. */
  @Test
  void robotAlreadyExists_doesNotEnqueue() {
    givenProjectBoundTo(IMAGE_HUB);
    ImageRegistryRobot existing = new ImageRegistryRobot();
    existing.setId("7");
    existing.setUsername(USERNAME);
    existing.setPermissions(List.of());
    when(this.robotService.listImageRegistryRobots(any())).thenReturn(List.of(existing));

    this.reconciler.reconcile(new Request(PROJECT));

    verify(this.secretRequestQueue, never()).enqueueByProjectName(any());
  }

  /** 연결된 허브가 없으면 robot 을 만들지 않으므로 깨울 것도 없다. */
  @Test
  void noBoundImageHub_doesNotEnqueue() {
    givenProjectBoundTo();
    when(this.robotService.listImageRegistryRobots(any())).thenReturn(List.of());

    this.reconciler.reconcile(new Request(PROJECT));

    verify(this.secretRequestQueue, never()).enqueueByProjectName(any());
  }

  private void givenProjectBoundTo(String... imageHubNames) {
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta().name(PROJECT));
    V1alpha1ProjectBinding binding = new V1alpha1ProjectBinding();
    binding.setImageHubs(List.of(imageHubNames));
    V1alpha1ProjectSpec spec = new V1alpha1ProjectSpec();
    spec.setBinding(binding);
    project.setSpec(spec);
    when(this.projectIndexer.getByKey(PROJECT)).thenReturn(project);

    for (String name : imageHubNames) {
      V1alpha1ImageHub imageHub = new V1alpha1ImageHub();
      imageHub.setMetadata(new V1ObjectMeta().name(name));
      V1alpha1ImageHubSpec imageHubSpec = new V1alpha1ImageHubSpec();
      imageHubSpec.setId("32");
      imageHub.setSpec(imageHubSpec);
      when(this.imageHubIndexer.getByKey(name)).thenReturn(imageHub);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends io.kubernetes.client.common.KubernetesObject> void registerIndexer(
      SharedInformerFactory factory, Class<T> type, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(type)).thenReturn(informer);
  }

}
