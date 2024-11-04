package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NodeGroupReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1alpha1NodeGroup> nodeGroupIndexer;
    private final BoundObjectResolver boundObjectResolver;
    private final StatusPatchHelper<V1alpha1NodeGroup> statusPatchHelper;

    public NodeGroupReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.reconciliationService = reconciliationService;
        this.nodeGroupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeGroup.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
        this.statusPatchHelper = new StatusPatchHelper<>(
                k8sApiProvider.getApiClient(),
                K8sObjectTypeConstants.NODE_GROUP_V1ALPHA1,
                ProjectApiConstants.NODE_GROUP_RESOURCE_PLURAL);
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String nodeGroupKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1alpha1NodeGroup> nodeGroupOpt = Optional.ofNullable(this.nodeGroupIndexer.getByKey(nodeGroupKey));
        if (nodeGroupOpt.isEmpty()) {
            return new Result(false);
        }
        V1alpha1NodeGroup nodeGroup = nodeGroupOpt.get();

        List<V1alpha1Project> boundProjects = this.boundObjectResolver.getAllBoundProjects(nodeGroup);
        List<V1Node> boundNodes = this.boundObjectResolver.getAllBoundNodes(nodeGroup);
        boundNodes = NodeUtils.getProjectManagedNodes(boundNodes);
        V1alpha1NodeGroupStatus reconciledStatus = this.reconciliationService.reconcileNodeGroupStatus(nodeGroup, boundProjects, boundNodes);

        return reconcileExistingNodeGroup(nodeGroupOpt.get(), reconciledStatus);
    }

    private Result reconcileExistingNodeGroup(V1alpha1NodeGroup nodeGroup, V1alpha1NodeGroupStatus reconciledStatus) throws ApiException {
        if (Objects.equals(nodeGroup.getStatus(), reconciledStatus)) {
            return new Result(false);
        }

        V1alpha1NodeGroup edited = new V1alpha1NodeGroup();
        edited.setApiVersion(nodeGroup.getApiVersion());
        edited.setKind(nodeGroup.getKind());
        edited.setMetadata(nodeGroup.getMetadata());
        edited.setSpec(nodeGroup.getSpec());
        edited.setStatus(reconciledStatus);
        updateNodeGroupStatus(edited);
        return new Result(false);
    }

    private void updateNodeGroupStatus(V1alpha1NodeGroup nodeGroup) throws ApiException {
        Objects.requireNonNull(nodeGroup.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(nodeGroup), nodeGroup.getStatus());
    }

}
