package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface RBACResourceLister {

    List<V1ClusterRoleBinding> getClusterRoleBindingsByUser(String username);

    List<V1ClusterRoleBinding> getClusterRoleBindingsByGroup(String group);

    @Nullable
    V1ClusterRole getClusterRole(String name);

    List<V1RoleBinding> getRoleBindingsByUser(String username);

    List<V1RoleBinding> getRoleBindingsByGroup(String group);

    @Nullable
    V1Role getRole(String name, String namespace);

    Set<String> getAllNamespaceNames();

}
