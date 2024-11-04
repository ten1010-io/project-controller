package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.mutating.AdmissionReviewConstants;
import io.ten1010.aipub.projectcontroller.mutating.IllegalPropertyException;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;

import java.util.Objects;

public abstract class AbstractReviewHandler<T extends KubernetesObject> implements ReviewHandler {

    private static K8sObjectTypeKey getTypeKey(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getKind());
        Objects.requireNonNull(request.getKind().getGroup());
        Objects.requireNonNull(request.getKind().getVersion());
        Objects.requireNonNull(request.getKind().getKind());

        String group = request.getKind().getGroup();
        if (group.isEmpty()) {
            group = "core";
        }

        return new K8sObjectTypeKey(group, request.getKind().getVersion(), request.getKind().getKind());
    }

    private final K8sObjectType<T> targetType;
    private final ObjectMapper mapper;

    public AbstractReviewHandler(K8sObjectType<T> targetType) {
        this.targetType = targetType;
        this.mapper = new ObjectMapperFactory().createObjectMapper();
    }

    @Override
    public boolean canHandle(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());

        K8sObjectTypeKey typeKey = getTypeKey(review.getRequest());
        return this.targetType.getTypeKey().equals(typeKey);
    }

    protected T getRequestObject(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getObject());

        try {
            return this.mapper.treeToValue(review.getRequest().getObject(), this.targetType.getObjClass());
        } catch (JsonProcessingException e) {
            throw new IllegalPropertyException(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/object", "Fail to deserialize");
        }
    }

    protected JsonNode createJsonNode(Object object) {
        return this.mapper.valueToTree(object);
    }

}
