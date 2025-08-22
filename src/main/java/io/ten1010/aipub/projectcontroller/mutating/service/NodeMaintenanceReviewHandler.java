package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Objects;

@Slf4j
public class NodeMaintenanceReviewHandler extends AbstractReviewHandler<V1alpha1NodeMaintenance> {

    private final AipubProperties aipubProperties;
    private final SubjectResolver subjectResolver;
    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1NodeMaintenance> nodeMaintenanceIndexer;

    public NodeMaintenanceReviewHandler(AipubProperties aipubProperties, SubjectResolver subjectResolver, SharedInformerFactory sharedInformerFactory) {
        super(K8sObjectTypeConstants.NODE_MAINTENANCE_V1ALPHA1);
        this.aipubProperties = aipubProperties;
        this.keyResolver = new KeyResolver();
        this.subjectResolver = subjectResolver;
        this.nodeMaintenanceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)
                .getIndexer();
    }

    @Override
    public void handle(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getUserInfo());

        V1alpha1NodeMaintenance nodeMaintenance = getRequestObject(review);
        var spec = Objects.requireNonNull(nodeMaintenance.getSpec());
        var actions = Objects.requireNonNull(spec.getActions());
        long uncordonCount = actions.stream().filter(x -> x.getType().equals("uncordon")).count();
        long drainCount = actions.stream().filter(x -> x.getType().equals("drain")).count();

        if (uncordonCount > 0 && drainCount > 0) {
            V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), "Forbidden");
            return;
        }
        V1AdmissionReviewUtils.allow(review);
    }

}
