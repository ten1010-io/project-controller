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
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUserStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AipubUserReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1alpha1AipubUser> aipubUserIndexer;
    private final BoundObjectResolver boundObjectResolver;
    private final StatusPatchHelper<V1alpha1AipubUser> statusPatchHelper;

    public AipubUserReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.reconciliationService = reconciliationService;
        this.aipubUserIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
        this.statusPatchHelper = new StatusPatchHelper<>(
                k8sApiProvider.getApiClient(),
                K8sObjectTypeConstants.AIPUB_USER_V1ALPHA1,
                ProjectApiConstants.AIPUB_USER_RESOURCE_PLURAL);
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String aipubUserKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1alpha1AipubUser> userOpt = Optional.ofNullable(this.aipubUserIndexer.getByKey(aipubUserKey));
        if (userOpt.isEmpty()) {
            return new Result(false);
        }
        V1alpha1AipubUser user = userOpt.get();

        List<V1alpha1Project> boundProjects = this.boundObjectResolver.getAllBoundProjects(user);
        List<V1alpha1ImageHub> boundHubs = this.boundObjectResolver.getAllBoundImageHubs(user);
        V1alpha1AipubUserStatus reconciledStatus = this.reconciliationService.reconcileAipubUserStatus(user, boundProjects, boundHubs);

        return reconcileExistingAipubUser(userOpt.get(), reconciledStatus);
    }

    private Result reconcileExistingAipubUser(V1alpha1AipubUser existing, V1alpha1AipubUserStatus reconciledStatus) throws ApiException {
        if (Objects.equals(existing.getStatus(), reconciledStatus)) {
            return new Result(false);
        }

        V1alpha1AipubUser edited = new V1alpha1AipubUser();
        edited.setApiVersion(existing.getApiVersion());
        edited.setKind(existing.getKind());
        edited.setMetadata(existing.getMetadata());
        edited.setSpec(existing.getSpec());
        edited.setStatus(reconciledStatus);
        updateStatus(edited);
        return new Result(false);
    }

    private void updateStatus(V1alpha1AipubUser aipubUser) throws ApiException {
        Objects.requireNonNull(aipubUser.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(aipubUser), aipubUser.getStatus());
    }

}
