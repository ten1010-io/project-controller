package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Objects;

@Slf4j
public class NamespaceReviewHandler extends AbstractReviewHandler<V1Namespace> {

    private final AipubProperties aipubProperties;
    private final KeyResolver keyResolver;
    private final SubjectResolver subjectResolver;
    private final Indexer<V1Namespace> namespaceIndexer;

    public NamespaceReviewHandler(AipubProperties aipubProperties, SubjectResolver subjectResolver, SharedInformerFactory sharedInformerFactory) {
        super(K8sObjectTypeConstants.NAMESPACE_V1);
        this.aipubProperties = aipubProperties;
        this.keyResolver = new KeyResolver();
        this.subjectResolver = subjectResolver;
        this.namespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Namespace.class)
                .getIndexer();
    }

    @Override
    public void handle(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getUserInfo());

        V1Namespace namespace = this.namespaceIndexer.getByKey(this.keyResolver.resolveKey(getNamespaceName(review)));
        String namespaceName = K8sObjectUtils.getName(namespace);
        if (isReservedName(namespaceName)) {
            log.debug("Namespace {} is reserved", namespaceName);
            V1UserInfo userInfo = review.getRequest().getUserInfo();
            if (userInfo.getGroups() != null &&
                    (userInfo.getGroups().contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME) && !userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME))) {
                log.debug("Allowed namespace {} delete because requester is system admin", namespaceName);
                V1AdmissionReviewUtils.allow(review);
                return;
            }
            V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), String.format("%s is reserved name", namespaceName));
            return;
        }

        V1AdmissionReviewUtils.allow(review);
    }

    private String getNamespaceName(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getName());

        return review.getRequest().getName();
    }

    private boolean isReservedName(String name) {
        return this.aipubProperties.getReservedNamespace().contains(name);
    }

}
