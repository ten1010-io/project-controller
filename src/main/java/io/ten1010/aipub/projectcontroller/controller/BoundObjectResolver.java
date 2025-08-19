package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.*;
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
    private final Indexer<V1alpha1ImageHub> imageHubIndexer;
    private final Indexer<V1Node> nodeIndexer;
    private final Indexer<V1alpha1ResourceSet> resourceSetIndexer;
    private final Indexer<V1alpha1NodeMaintenance> nodeMaintenanceIndexer;
    private final Indexer<V1Pod> podIndexer;

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
        this.imageHubIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1ImageHub.class)
                .getIndexer();
        this.nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
        this.resourceSetIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1ResourceSet.class)
                .getIndexer();
        this.nodeMaintenanceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)
                .getIndexer();
        this.podIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Pod.class)
                .getIndexer();
    }

    public List<V1alpha1AipubUser> getAllBoundAipubUsers(V1alpha1Project project) {
        return getDirectlyBoundAipubUsers(project);
    }

    public List<V1alpha1NodeGroup> getAllBoundNodeGroups(V1alpha1Project project) {
        return getDirectlyBoundNodeGroups(project);
    }

    public List<V1alpha1ImageHub> getAllBoundImageHubs(V1alpha1Project project) {
        return getDirectlyBoundImageHubs(project);
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

    public List<V1alpha1ImageHub> getAllBoundImageHubs(V1alpha1AipubUser aipubUser) {
        List<V1alpha1Project> projects = getAllBoundProjects(aipubUser);
        return projects.stream()
                .flatMap(e -> ProjectUtils.getSpecBindingImageHubs(e).stream())
                .distinct()
                .map(this.keyResolver::resolveKey)
                .map(this.imageHubIndexer::getByKey)
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

    public List<V1Node> getAllBoundNodes(V1alpha1ResourceSet resourceSet) {
        return ResourceSetUtils.getSpecNodeNames(resourceSet).stream()
                .map(this.nodeIndexer::getByKey)
                .distinct()
                .filter(Objects::nonNull)
                .toList();
    }

    public List<V1alpha1Project> getAllBoundProjects(V1alpha1ImageHub imageHub) {
        return getDirectlyBoundProjects(imageHub);
    }

    public List<V1alpha1AipubUser> getAllBoundAipubUsers(V1alpha1ImageHub imageHub) {
        List<V1alpha1AipubUser> allBoundUsers = getAllBoundProjects(imageHub).stream()
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

    // used : PodReconciler
    // NodeMaintenance 목록에서 pod 를 찾아서 응답한다.
    public Optional<V1alpha1NodeMaintenanceAction> optNodeMaintenanceActionDrainPod(V1Node node, V1Pod pod) {
        List<V1alpha1NodeMaintenance> allBoundNodeGroups = this.nodeMaintenanceIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_NODE_MAINTENANCE_INDEXER_NAME,
                K8sObjectUtils.getName(node));
        if (allBoundNodeGroups.isEmpty()) {
            return Optional.empty();
        }
        for (V1alpha1NodeMaintenance allBoundNodeGroup : allBoundNodeGroups) {
            if (allBoundNodeGroup.getSpec().getTargetNodes().contains(node.getMetadata().getName())) {
                var actions = allBoundNodeGroup.getSpec().getActions();
                var ownerReferences = Objects.requireNonNull(pod.getMetadata().getOwnerReferences());
                boolean isDaemonset = false;
                for (V1OwnerReference ownerReference : ownerReferences) {
                    if (ownerReference.getKind().equalsIgnoreCase("DaemonSet")) {
                        isDaemonset = true;
                        break;
                    }
                }
                for (V1alpha1NodeMaintenanceAction action : actions) {
                    if (action.getType().equals("drain")) {
                        if (isDaemonset) {
                            if (action.getIgnoreDaemonSets()) {
                                // todo System.out.println("isDrainTargetPod[daemonset] = " + node.getMetadata().getName() + " // " + pod.getMetadata().getName() + " // " + allBoundNodeGroups.size());
                                return Optional.of(action);
                            }
                        } else {
                            // todo System.out.println("isDrainTargetPod = " + node.getMetadata().getName() + " // " + pod.getMetadata().getName() + " // " + allBoundNodeGroups.size());
                            return Optional.of(action);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean isDrainedTargetNode(V1Node node) {
        List<V1alpha1NodeMaintenance> allBoundNodeGroups = this.nodeMaintenanceIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_NODE_MAINTENANCE_INDEXER_NAME,
                K8sObjectUtils.getName(node));
        if (allBoundNodeGroups.isEmpty()) {
            return false;
        }
        int resultCnt = 0;
        for (V1alpha1NodeMaintenance allBoundNodeGroup : allBoundNodeGroups) {
            if (allBoundNodeGroup.getSpec().getTargetNodes().contains(node.getMetadata().getName())) {
                var actions = allBoundNodeGroup.getSpec().getActions();
                List<V1Pod> pods = podIndexer.byIndex(IndexerConstants.NODE_NAME_TO_POD_INDEXER_NAME, node.getMetadata().getName());
                for (V1Pod pod : pods) {
                    var ownerReferences = Objects.requireNonNull(pod.getMetadata().getOwnerReferences());
                    boolean isDaemonset = false;
                    for (V1OwnerReference ownerReference : ownerReferences) {
                        if (ownerReference.getKind().equalsIgnoreCase("DaemonSet")) {
                            isDaemonset = true;
                            break;
                        }
                    }
                    for (V1alpha1NodeMaintenanceAction action : actions) {
                        if (action.getType().equals("drain")) {
                            if (isDaemonset) {
                                if (action.getIgnoreDaemonSets()) {
                                    resultCnt++;
                                }
                            } else {
                                resultCnt++;
                            }
                        }
                    }
                }
            }
        }
        return resultCnt == 0 ? true : false;
    }

    // used : NodeReconciler
    // NodeMaintenance 목록에서 node 를 찾아서 응답한다.
    public List<V1alpha1NodeMaintenance> getAllBoundNodeMaintenances(V1Node node) {
        List<V1alpha1NodeMaintenance> allBoundNodeGroups = this.nodeMaintenanceIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_NODE_MAINTENANCE_INDEXER_NAME,
                K8sObjectUtils.getName(node));
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundNodeGroups);
    }

    // used RequestBuilderFactory
    // 노드 목록에서 NodeMaintenance 의 targetNodes 를 찾아서 응답한다.
    public List<V1Node> getAllBoundNodeByNodeMaintenances(V1alpha1NodeMaintenance node) {
        List<V1Node> allBoundNodeGroups = new ArrayList<>();
        for (String targetNode : node.getSpec().getTargetNodes()) {
            Optional<V1Node> opt = Optional.ofNullable(this.nodeIndexer.getByKey(targetNode));
            if (opt.isPresent()) {
                allBoundNodeGroups.add(opt.get());
            }
        }
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundNodeGroups);
    }

    public List<V1alpha1ResourceSet> getAllBoundResourceSets(V1alpha1Project project) {
        List<V1Node> allBoundNodes = new ArrayList<>(getDirectlyBoundNodes(project));
        List<V1alpha1ResourceSet> allBoundResourceSets = new ArrayList<>();
        allBoundNodes.forEach(node ->
                allBoundResourceSets.addAll(getDirectlyBoundResourceSets(node)));
        return K8sObjectUtils.distinctByKey(this.keyResolver, allBoundResourceSets);
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

    private List<V1alpha1ImageHub> getDirectlyBoundImageHubs(V1alpha1Project project) {
        return ProjectUtils.getSpecBindingImageHubs(project).stream()
                .map(this.keyResolver::resolveKey)
                .map(this.imageHubIndexer::getByKey)
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

    private List<V1alpha1Project> getDirectlyBoundProjects(V1alpha1ImageHub imageHub) {
        return this.projectIndexer.byIndex(
                IndexerConstants.IMAGE_HUB_NAME_TO_PROJECTS_INDEXER_NAME,
                K8sObjectUtils.getName(imageHub));
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

    private List<V1alpha1ResourceSet> getDirectlyBoundResourceSets(V1Node node) {
        return this.resourceSetIndexer.byIndex(
                IndexerConstants.NODE_NAME_TO_RESOURCE_SETS_INDEXER_NAME,
                K8sObjectUtils.getName(node));
    }

}
