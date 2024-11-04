package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1CronJobList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import io.ten1010.aipub.projectcontroller.informer.InformerRegistrar;

import java.util.List;
import java.util.Map;

public class CronJobInformerRegistrar implements InformerRegistrar {

    private final BatchV1Api batchV1Api;

    public CronJobInformerRegistrar(K8sApiProvider k8sApiProvider) {
        this.batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    public void registerInformer(SharedInformerFactory informerFactory) {
        registerCronJobInformer(informerFactory);
    }

    private void registerCronJobInformer(SharedInformerFactory informerFactory) {
        SharedIndexInformer<V1CronJob> informer = informerFactory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> this.batchV1Api.listCronJobForAllNamespaces()
                        .resourceVersion(params.resourceVersion)
                        .watch(params.watch)
                        .timeoutSeconds(params.timeoutSeconds)
                        .buildCall(null),
                V1CronJob.class,
                V1CronJobList.class);
        informer.addIndexers(Map.of(
                IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
                obj -> List.of(K8sObjectUtils.getNamespace(obj))));
    }

}
