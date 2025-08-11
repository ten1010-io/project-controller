package io.ten1010.aipub.projectcontroller.controller.cluster;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class UncordonReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final Indexer<V1Node> nodeIndexer;
    private final CoreV1Api coreV1Api;

    public UncordonReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider
    ) {
        this.keyResolver = new KeyResolver();
        this.nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
        this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String nodeKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1Node> nodeOpt = Optional.ofNullable(this.nodeIndexer.getByKey(nodeKey));
        if (nodeOpt.isEmpty()) {
            return new Result(false);
        }
        V1Node node = nodeOpt.get();
        String name = K8sObjectUtils.getName(node);
        V1NodeSpec spec = Objects.requireNonNull(node.getSpec());
        if (Boolean.TRUE.equals(spec.getUnschedulable())) {
            int cnt = 0;
            for (V1Pod item : coreV1Api.listNamespacedPod("project-controller").execute().getItems()) {
                V1PodSpec _spec = Objects.requireNonNull(item.getSpec());
                V1ObjectMeta metadata = Objects.requireNonNull(item.getMetadata());
                boolean isDaemonset = false;
                for (V1OwnerReference ownerReference : metadata.getOwnerReferences()) {
                    if (ownerReference.getKind().equalsIgnoreCase("DaemonSet")) {
                        isDaemonset = true;
                        break;
                    }
                }
                if (!isDaemonset && name.equals(_spec.getNodeName())) {
                    cnt++;
                }
            }

            if (cnt == 0) {
                // cordon -> uncordon
                log.info("uncordon node : name - {}", name);
                node.getSpec().setUnschedulable(false);
                coreV1Api.replaceNode(name, node).execute();
            }
        }
        return new Result(true);

    }

}
