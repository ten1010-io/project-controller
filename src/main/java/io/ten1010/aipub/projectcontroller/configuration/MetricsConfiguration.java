package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageBuild;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1beta1Workspace;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Configuration;

/**
 * 관측성(Micrometer/Prometheus) 계측 설정.
 *
 * <p>reconcile 지연은 {@link AbstractReconciler} 에 정적 주입한 MeterRegistry 로 기록하고, informer
 * 캐시 크기는 리소스 타입별 Gauge 로 노출한다. admission webhook 지연은 actuator 의 {@code
 * http.server.requests} 로 자동 계측된다. workqueue 깊이는 kubernetes-client 의 DefaultController
 * 내부라 공개 접근 수단이 없어 이번 범위에서 제외한다(reconcile Timer 로 간접 관측).
 */
@Configuration
public class MetricsConfiguration {

  private static final String INFORMER_CACHE_SIZE_METRIC = "projectcontroller.informer.cache.size";

  private static final List<Class<? extends KubernetesObject>> INFORMER_TYPES = List.of(
      V1alpha1Project.class,
      V1alpha1AipubUser.class,
      V1alpha1NodeGroup.class,
      V1alpha1ImageHub.class,
      V1alpha1ResourceSet.class,
      V1alpha1Operation.class,
      V1beta1Workspace.class,
      V1alpha1AipubVolume.class,
      V1alpha1ChainJob.class,
      V1alpha1SftpServer.class,
      V1alpha1ImageBuild.class,
      V1Namespace.class,
      V1Node.class,
      V1Pod.class,
      V1Secret.class,
      V1ResourceQuota.class,
      V1ClusterRole.class,
      V1ClusterRoleBinding.class,
      V1Role.class,
      V1RoleBinding.class,
      V1CronJob.class,
      V1DaemonSet.class,
      V1Deployment.class,
      V1Job.class,
      V1ReplicaSet.class,
      V1StatefulSet.class);

  private final MeterRegistry meterRegistry;
  private final SharedInformerFactory sharedInformerFactory;

  public MetricsConfiguration(MeterRegistry meterRegistry,
      SharedInformerFactory sharedInformerFactory) {
    this.meterRegistry = meterRegistry;
    this.sharedInformerFactory = sharedInformerFactory;
  }

  @PostConstruct
  public void registerMetrics() {
    AbstractReconciler.setMeterRegistry(this.meterRegistry);
    INFORMER_TYPES.forEach(this::registerInformerCacheSizeGauge);
  }

  private <T extends KubernetesObject> void registerInformerCacheSizeGauge(Class<T> type) {
    SharedIndexInformer<T> informer =
        this.sharedInformerFactory.getExistingSharedIndexInformer(type);
    if (informer == null) {
      return;
    }
    Gauge.builder(INFORMER_CACHE_SIZE_METRIC, informer, i -> i.getIndexer().list().size())
        .description("Number of objects held in the informer cache")
        .tag("resource", type.getSimpleName())
        .register(this.meterRegistry);
  }

}
