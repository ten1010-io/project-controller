package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1OwnerReferenceBuilder;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ArtifactService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.RepositoryService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.AipubUserUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ImageNamespaceUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ImageReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImageReviewReviewHandler extends AbstractReviewHandler<V1alpha1ImageReview> {

    private final static V1OwnerReference DUMMY_REF = new V1OwnerReferenceBuilder()
            .withApiVersion(K8sObjectTypeConstants.IMAGE_REVIEW_V1ALPHA1.getTypeKey().getApiVersion())
            .withKind(K8sObjectTypeConstants.IMAGE_REVIEW_V1ALPHA1.getTypeKey().getKind())
            .withName("dummy-for-image-review-being-removed-on-creation")
            .withUid("dummy-uid")
            .withController(false)
            .withBlockOwnerDeletion(false)
            .build();

    private final RepositoryService repositoryService;
    private final ArtifactService artifactService;
    private final UserInfoAnalyzer userInfoAnalyzer;
    private final Indexer<V1alpha1ImageNamespace> imageNamespaceIndexer;
    private final KeyResolver keyResolver;

    public ImageReviewReviewHandler(
            RepositoryService repositoryService, ArtifactService artifactService, SharedInformerFactory sharedInformerFactory) {
        super(K8sObjectTypeConstants.IMAGE_REVIEW_V1ALPHA1);
        this.repositoryService = repositoryService;
        this.artifactService = artifactService;
        this.userInfoAnalyzer = new UserInfoAnalyzer(sharedInformerFactory);
        this.imageNamespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1ImageNamespace.class)
                .getIndexer();
        this.keyResolver = new KeyResolver();
    }

    @Override
    public void handle(V1AdmissionReview review) {
        V1alpha1ImageReview imageReview = getRequestObject(review);
        Optional<String> imgNsOpt = ImageReviewUtils.getImgNS(imageReview);
        if (imgNsOpt.isEmpty()) {
            V1AdmissionReviewUtils.reject(review, HttpStatus.BAD_REQUEST.value(), "imgNS required");
            return;
        }
        String targetImgNS = imgNsOpt.get();
        Optional<String> repoOpt = ImageReviewUtils.getRepo(imageReview);

        if (denyIfUnauthorized(review, targetImgNS)) {
            return;
        }

        doService(review, targetImgNS, repoOpt.orElse(null));
    }

    private boolean denyIfUnauthorized(V1AdmissionReview review, String targetImgNS) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getUserInfo());

        UserInfoAnalysis analysis = this.userInfoAnalyzer.analyze(review.getRequest().getUserInfo());
        if (analysis.isMaster()) {
            return false;
        }
        if (analysis.isAipubAdmin()) {
            return false;
        }
        if (!analysis.isAipubMember()) {
            V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), "Not aipub member");
            return true;
        }
        V1alpha1AipubUser aipubUser = analysis.getAipubUser().orElseThrow();
        List<String> boundImageNamespaces = AipubUserUtils.getAllBoundImageNamespaces(aipubUser);
        if (boundImageNamespaces.stream().noneMatch(e -> e.equals(targetImgNS))) {
            V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), String.format("Aipub member not bound to ImageNamespace with name[%s]", targetImgNS));
            return true;
        }

        return false;
    }

    private void doService(V1AdmissionReview review, String targetImgNS, @Nullable String targetRepo) {
        V1alpha1ImageNamespace imageNamespace = this.imageNamespaceIndexer.getByKey(this.keyResolver.resolveKey(targetImgNS));
        if (imageNamespace == null) {
            V1AdmissionReviewUtils.reject(review, HttpStatus.BAD_REQUEST.value(), String.format("ImageNamespace with name[%s] not found", targetImgNS));
            return;
        }

        V1alpha1ImageReviewStatus status = new V1alpha1ImageReviewStatus();
        if (targetRepo == null) {
            List<Repository> repositories = this.repositoryService.listNamespacedRepositories(
                    ImageNamespaceUtils.getSpecId(imageNamespace), new RepositoryListOptions());
            List<V1alpha1ReviewRepository> reviewRepositories = repositories.stream()
                    .map(e -> {
                        V1alpha1ReviewRepository repository = new V1alpha1ReviewRepository();
                        repository.setName(e.getName());
                        return repository;
                    })
                    .toList();
            status.setRepositories(reviewRepositories);
            status.setArtifacts(null);
        } else {
            List<Artifact> artifacts = this.artifactService.listArtifacts(
                    ImageNamespaceUtils.getSpecId(imageNamespace), targetRepo, new ArtifactListOptions());
            List<V1alpha1ReviewArtifact> reviewArtifacts = artifacts.stream()
                    .map(artifact -> {
                        List<String> tags;
                        if (artifact.getTags() == null) {
                            tags = List.of();
                        } else {
                            tags = artifact.getTags().stream()
                                    .map(ArtifactTag::getName)
                                    .filter(Objects::nonNull)
                                    .toList();
                        }
                        V1alpha1ReviewArtifact reviewArtifact = new V1alpha1ReviewArtifact();
                        reviewArtifact.setDigest(artifact.getDigest());
                        reviewArtifact.setTags(tags);

                        return reviewArtifact;
                    })
                    .toList();
            status.setRepositories(null);
            status.setArtifacts(reviewArtifacts);
        }

        JsonPatchOperation patchOperation1 = new JsonPatchOperationBuilder()
                .replace()
                .setPath("/metadata/ownerReferences")
                .setValue(createJsonNode(List.of(DUMMY_REF)))
                .build();
        JsonPatchOperation patchOperation2 = new JsonPatchOperationBuilder()
                .replace()
                .setPath("/status")
                .setValue(createJsonNode(status))
                .build();
        JsonPatch jsonPatch = new JsonPatchBuilder()
                .addToOperations(patchOperation1)
                .addToOperations(patchOperation2)
                .build();

        V1AdmissionReviewUtils.allow(review, jsonPatch);
    }

}
