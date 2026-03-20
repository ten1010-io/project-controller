package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;

import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.ten1010.aipub.projectcontroller.controller.transfer.TransferControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.transfer.TransferService;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferControllerConfiguration {

  @Bean
  public TransferService transferService(K8sApiProvider k8sApiProvider,
      SharedInformerFactory sharedInformerFactory) {
    return new TransferService(k8sApiProvider, sharedInformerFactory);
  }

  @Bean
  public Controller cronJobTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    BatchV1Api batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        cronJob -> batchV1Api.replaceNamespacedCronJob(
            K8sObjectUtils.getName(cronJob), K8sObjectUtils.getNamespace(cronJob), cronJob)
            .execute(),
        V1CronJob.class, "cronjob-transfer-controller")
        .createController();
  }

  @Bean
  public Controller daemonSetTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    AppsV1Api appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        ds -> appsV1Api.replaceNamespacedDaemonSet(
            K8sObjectUtils.getName(ds), K8sObjectUtils.getNamespace(ds), ds)
            .execute(),
        V1DaemonSet.class, "daemonset-transfer-controller")
        .createController();
  }

  @Bean
  public Controller deploymentTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    AppsV1Api appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        dep -> appsV1Api.replaceNamespacedDeployment(
            K8sObjectUtils.getName(dep), K8sObjectUtils.getNamespace(dep), dep)
            .execute(),
        V1Deployment.class, "deployment-transfer-controller")
        .createController();
  }

  @Bean
  public Controller jobTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    BatchV1Api batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        job -> batchV1Api.replaceNamespacedJob(
            K8sObjectUtils.getName(job), K8sObjectUtils.getNamespace(job), job)
            .execute(),
        V1Job.class, "job-transfer-controller")
        .createController();
  }

  @Bean
  public Controller podTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    CoreV1Api coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        pod -> coreV1Api.replaceNamespacedPod(
            K8sObjectUtils.getName(pod), K8sObjectUtils.getNamespace(pod), pod)
            .execute(),
        V1Pod.class, "pod-transfer-controller")
        .createController();
  }

  @Bean
  public Controller replicaSetTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    AppsV1Api appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        rs -> appsV1Api.replaceNamespacedReplicaSet(
            K8sObjectUtils.getName(rs), K8sObjectUtils.getNamespace(rs), rs)
            .execute(),
        V1ReplicaSet.class, "replicaset-transfer-controller")
        .createController();
  }

  @Bean
  public Controller statefulSetTransferController(SharedInformerFactory sharedInformerFactory,
      TransferService transferService, K8sApiProvider k8sApiProvider) {
    AppsV1Api appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    return new TransferControllerFactory<>(
        sharedInformerFactory, transferService,
        ss -> appsV1Api.replaceNamespacedStatefulSet(
            K8sObjectUtils.getName(ss), K8sObjectUtils.getNamespace(ss), ss)
            .execute(),
        V1StatefulSet.class, "statefulset-transfer-controller")
        .createController();
  }

}
