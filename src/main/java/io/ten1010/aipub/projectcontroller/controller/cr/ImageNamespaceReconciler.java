package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageNamespace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageNamespaceStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImageNamespaceReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1alpha1ImageNamespace> imageNamespaceIndexer;
    private final BoundObjectResolver boundObjectResolver;
    private final StatusPatchHelper<V1alpha1ImageNamespace> statusPatchHelper;

    public ImageNamespaceReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.reconciliationService = reconciliationService;
        this.imageNamespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1ImageNamespace.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
        this.statusPatchHelper = new StatusPatchHelper<>(
                k8sApiProvider.getApiClient(),
                K8sObjectTypeConstants.IMAGE_NAMESPACE_V1ALPHA1,
                ProjectApiConstants.IMAGE_NAMESPACE_RESOURCE_PLURAL);
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String imageNamespaceKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1alpha1ImageNamespace> imageNamespaceOpt = Optional.ofNullable(this.imageNamespaceIndexer.getByKey(imageNamespaceKey));
        if (imageNamespaceOpt.isEmpty()) {
            return new Result(false);
        }
        V1alpha1ImageNamespace imageNamespace = imageNamespaceOpt.get();

        List<V1alpha1Project> boundProjects = this.boundObjectResolver.getAllBoundProjects(imageNamespace);
        List<V1alpha1AipubUser> boundAipubUsers = this.boundObjectResolver.getAllBoundAipubUsers(imageNamespace);
        V1alpha1ImageNamespaceStatus reconciledStatus = this.reconciliationService.reconcileImageNamespaceStatus(imageNamespace, boundProjects, boundAipubUsers);

        return reconcileExistingImageNamespace(imageNamespaceOpt.get(), reconciledStatus);
    }

    private Result reconcileExistingImageNamespace(V1alpha1ImageNamespace existing, V1alpha1ImageNamespaceStatus reconciledStatus) throws ApiException {
        if (Objects.equals(existing.getStatus(), reconciledStatus)) {
            return new Result(false);
        }

        V1alpha1ImageNamespace edited = new V1alpha1ImageNamespace();
        edited.setApiVersion(existing.getApiVersion());
        edited.setKind(existing.getKind());
        edited.setMetadata(existing.getMetadata());
        edited.setSpec(existing.getSpec());
        edited.setStatus(reconciledStatus);
        updateStatus(edited);
        return new Result(false);
    }

    private void updateStatus(V1alpha1ImageNamespace imageNamespace) throws ApiException {
        Objects.requireNonNull(imageNamespace.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(imageNamespace), imageNamespace.getStatus());
    }

}
