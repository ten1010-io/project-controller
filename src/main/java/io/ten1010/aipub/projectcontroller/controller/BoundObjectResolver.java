package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.LabelUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeGroupUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;

import java.util.*;

public class BoundObjectResolver {

    private static <T> List<T> getIntersection(List<T> list1, List<T> list2) {
        return list1.stream()
                .distinct()
                .filter(list2::contains)
                .toList();
    }

    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final Indexer<V1alpha1AipubUser> aipubUserIndexer;
    private final Indexer<V1alpha1NodeGroup> nodeGroupIndexer;
    private final Indexer<V1alpha1ImageNamespace> imageNamespaceIndexer;
    private final Indexer<V1Node> nodeIndexer;

    public BoundObjectResolver(SharedInformerFactory sharedInformerFactory) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.aipubUserIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
                .getIndexer();
        this.nodeGroupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeGroup.class)
                .getIndexer();
        this.imageNamespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1ImageNamespace.class)
                .getIndexer();
        this.nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
    }

    public List<V1alpha1AipubUser> getAllBoundAipubUsers(V1alpha1Project project) {
        return getDirectlyBoundAipubUsers(project);
    }

    public List<V1alpha1NodeGroup> getAllBoundNodeGroups(V1alpha1Project project) {
        return getDirectlyBoundNodeGroups(project);
    }

    public List<V1alpha1ImageNamespace> getAllBoundImageNamespaces(V1alpha1Project project) {
        return getDirectlyBoundImageNamespaces(project);
    }

    public List<V1Node> getAllBoundNodes(V1alpha1Project project) {
        List<V1Node> nodesDirectlyBoundToProject = getDirectlyBoundNodes(project);
        List<V1Node> nodesIndirectlyBoundToProject = getDirectlyBoundNodeGroups(project).stream()
                .flatMap(e -> getAllBoundNodes(e).stream())
                .toList();
        List<V1Node> allBoundNodes = new ArrayList<>(nodesDirectlyBoundToProject);
        allBoundNodes.addAll(nodesIndirectlyBoundToProject);
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundNodes);
    }

    public List<V1alpha1Project> getAllBoundProjects(V1alpha1AipubUser aipubUser) {
        return getDirectlyBoundProjects(aipubUser);
    }

    public List<V1alpha1ImageNamespace> getAllBoundImageNamespaces(V1alpha1AipubUser aipubUser) {
        List<V1alpha1Project> projects = getAllBoundProjects(aipubUser);
        return projects.stream()
                .flatMap(e -> ProjectUtils.getSpecBindingImageNamespaces(e).stream())
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.imageNamespaceIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<V1alpha1Project> getAllBoundProjects(V1alpha1NodeGroup nodeGroup) {
        return getDirectlyBoundProjects(nodeGroup);
    }

    public List<V1Node> getAllBoundNodes(V1alpha1NodeGroup nodeGroup) {
        List<V1Node> allBoundNodes = new ArrayList<>(getDirectlyBoundNodes(nodeGroup));
        if (NodeGroupUtils.isNodeSelectorEnabled(nodeGroup)) {
            allBoundNodes.addAll(getBoundNodesByNodeSelector(nodeGroup));
        }
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundNodes);
    }

    public List<V1alpha1Project> getAllBoundProjects(V1alpha1ImageNamespace imageNamespace) {
        return getDirectlyBoundProjects(imageNamespace);
    }

    public List<V1alpha1AipubUser> getAllBoundAipubUsers(V1alpha1ImageNamespace imageNamespace) {
        List<V1alpha1AipubUser> allBoundUsers = getAllBoundProjects(imageNamespace).stream()
                .flatMap(e -> getAllBoundAipubUsers(e).stream())
                .toList();
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundUsers);
    }

    public List<V1alpha1Project> getAllBoundProjects(V1Node node) {
        List<V1alpha1Project> allBoundProjects = new ArrayList<>(getDirectlyBoundProjects(node));
        List<V1alpha1Project> projectsIndirectlyBoundToNode = getAllBoundNodeGroups(node).stream()
                .flatMap(nodeGroup -> this.getDirectlyBoundProjects(nodeGroup).stream())
                .toList();
        allBoundProjects.addAll(projectsIndirectlyBoundToNode);

        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundProjects);
    }

    public List<V1alpha1NodeGroup> getAllBoundNodeGroups(V1Node node) {
        List<V1alpha1NodeGroup> allBoundNodeGroups = new ArrayList<>(getDirectlyBoundNodeGroups(node));
        allBoundNodeGroups.addAll(getBoundNodeGroupsByNodeSelector(node));
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundNodeGroups);
    }

    private List<V1Node> getDirectlyBoundNodes(V1alpha1Project project) {
        return ProjectUtils.getSpecBindingNodes(project).stream()
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.nodeIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<V1alpha1AipubUser> getDirectlyBoundAipubUsers(V1alpha1Project project) {
        return ProjectUtils.getSpecMembers(project).stream()
                .map(V1alpha1ProjectMember::getAipubUser)
                .filter(Objects::nonNull)
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.aipubUserIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<V1alpha1NodeGroup> getDirectlyBoundNodeGroups(V1alpha1Project project) {
        return ProjectUtils.getSpecBindingNodeGroups(project).stream()
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.nodeGroupIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<V1alpha1ImageNamespace> getDirectlyBoundImageNamespaces(V1alpha1Project project) {
        return ProjectUtils.getSpecBindingImageNamespaces(project).stream()
                .map(this.keyResolver::resolveKey)
                .map(this.imageNamespaceIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<V1alpha1Project> getDirectlyBoundProjects(V1alpha1AipubUser aipubUser) {
        return this.projectIndexer.byIndex(
                IndexerConstants.AIPUB_USER_NAME_TO_PROJECTS_INDEXER_NAME,
                K8sObjectUtils.getName(aipubUser));
    }

    private List<V1Node> getDirectlyBoundNodes(V1alpha1NodeGroup nodeGroup) {
        return NodeGroupUtils.getSpecNodes(nodeGroup).stream()
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.nodeIndexer::getByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<V1Node> getBoundNodesByNodeSelector(V1alpha1NodeGroup nodeGroup) {
        Map<String, String> nodeSelector = NodeGroupUtils.getSpecNodeSelector(nodeGroup);
        List<String> selectorLabelStrings = LabelUtils.getLabelStrings(nodeSelector);
        if (selectorLabelStrings.isEmpty()) {
            return this.nodeIndexer.list();
        }
        List<List<V1Node>> nodesForEachLabelString = new ArrayList<>();
        for (String labelString : selectorLabelStrings) {
            List<V1Node> nodes = this.nodeIndexer.byIndex(IndexerConstants.LABEL_STRING_TO_OBJECTS_INDEXER_NAME, labelString);
            nodesForEachLabelString.add(nodes);
        }
        return nodesForEachLabelString.stream()
                .reduce(BoundObjectResolver::getIntersection)
                .orElseThrow();
    }

    private List<V1alpha1Project> getDirectlyBoundProjects(V1alpha1NodeGroup nodeGroup) {
        return this.projectIndexer.byIndex(
                IndexerConstants.NODE_GROUP_NAME_TO_PROJECTS_INDEXER_NAME,
                K8sObjectUtils.getName(nodeGroup));
    }

    private List<V1alpha1Project> getDirectlyBoundProjects(V1alpha1ImageNamespace imageNamespace) {
        return this.projectIndexer.byIndex(
                IndexerConstants.IMAGE_NAMESPACE_NAME_TO_PROJECTS_INDEXER_NAME,
                K8sObjectUtils.getName(imageNamespace));
    }

    private List<V1alpha1Project> getDirectlyBoundProjects(V1Node node) {
        return this.projectIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_PROJECTS_INDEXER_NAME,
                K8sObjectUtils.getName(node));
    }

    private List<V1alpha1NodeGroup> getDirectlyBoundNodeGroups(V1Node node) {
        return this.nodeGroupIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_NODE_GROUPS_INDEXER_NAME,
                K8sObjectUtils.getName(node));
    }

    private List<V1alpha1NodeGroup> getBoundNodeGroupsByNodeSelector(V1Node node) {
        List<V1alpha1NodeGroup> nodeGroupsBoundToNodeByNodeSelector = new ArrayList<>();
        List<String> labelStrings = LabelUtils.getLabelStrings(K8sObjectUtils.getLabels(node));
        Set<String> labelStringsSet = Set.copyOf(labelStrings);
        for (V1alpha1NodeGroup nodeGroup : this.nodeGroupIndexer.byIndex(
                IndexerConstants.ENABLE_NODE_SELECTOR_TO_NODE_GROUPS_INDEXER_NAME,
                IndexerConstants.TRUE_VALUE)) {
            Map<String, String> nodeSelector = NodeGroupUtils.getSpecNodeSelector(nodeGroup);
            List<String> selectorLabelStrings = LabelUtils.getLabelStrings(nodeSelector);
            if (labelStringsSet.containsAll(selectorLabelStrings)) {
                nodeGroupsBoundToNodeByNodeSelector.add(nodeGroup);
            }
        }
        return nodeGroupsBoundToNodeByNodeSelector;
    }

}
