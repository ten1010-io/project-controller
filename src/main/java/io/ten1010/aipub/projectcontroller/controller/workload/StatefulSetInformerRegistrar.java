package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import io.ten1010.aipub.projectcontroller.informer.InformerRegistrar;

import java.util.List;
import java.util.Map;

public class StatefulSetInformerRegistrar implements InformerRegistrar {

    private final AppsV1Api appsV1Api;

    public StatefulSetInformerRegistrar(K8sApiProvider k8sApiProvider) {
        this.appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    public void registerInformer(SharedInformerFactory informerFactory) {
        registerStatefulSetInformer(informerFactory);
    }

    private void registerStatefulSetInformer(SharedInformerFactory informerFactory) {
        SharedIndexInformer<V1StatefulSet> informer = informerFactory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> this.appsV1Api.listStatefulSetForAllNamespaces()
                        .resourceVersion(params.resourceVersion)
                        .watch(params.watch)
                        .timeoutSeconds(params.timeoutSeconds)
                        .buildCall(null),
                V1StatefulSet.class,
                V1StatefulSetList.class);
        informer.addIndexers(Map.of(
                IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
                obj -> List.of(K8sObjectUtils.getNamespace(obj))));
    }

}
