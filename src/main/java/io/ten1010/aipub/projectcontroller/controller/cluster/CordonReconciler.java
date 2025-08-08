package io.ten1010.aipub.projectcontroller.controller.cluster;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.kubectl.Kubectl;
import io.kubernetes.client.extended.kubectl.exception.KubectlException;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;

import java.util.Optional;

public class CordonReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final Indexer<V1Node> nodeIndexer;

    public CordonReconciler(
            SharedInformerFactory sharedInformerFactory
    ) {
        this.keyResolver = new KeyResolver();
        this.nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String nodeKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1Node> nodeOpt = Optional.ofNullable(this.nodeIndexer.getByKey(nodeKey));
        if (nodeOpt.isEmpty()) {
            return new Result(false);
        }
        V1Node node = nodeOpt.get();

        try {
            Kubectl.drain().gracePeriod(30)
                    .name(K8sObjectUtils.getName(node))
                    .execute();
        } catch (KubectlException e) {
            return new Result(false);
        }
        return new Result(true);

    }

}
