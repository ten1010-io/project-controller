package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.RbacV1SubjectBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectRoleEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectBinding;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectSpec;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectSpecQuota;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectStatusQuota;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectStatusQuotaMetric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class ProjectUtils {

  public static V1alpha1Project clone(V1alpha1Project object) {
    V1ObjectMeta metadataClone = new V1ObjectMetaBuilder(object.getMetadata()).build();
    V1alpha1ProjectSpec specClone = null;
    if (object.getSpec() != null) {
      specClone = clone(object.getSpec());
    }
    V1alpha1ProjectStatus statusClone = null;
    if (object.getStatus() != null) {
      statusClone = clone(object.getStatus());
    }

    V1alpha1Project clone = new V1alpha1Project();
    clone.setApiVersion(object.getApiVersion());
    clone.setKind(object.getKind());
    clone.setMetadata(metadataClone);
    clone.setSpec(specClone);
    clone.setStatus(statusClone);

    return clone;
  }

  public static List<V1alpha1ProjectMember> getSpecMembers(V1alpha1Project object) {
    if (object.getSpec() == null ||
        object.getSpec().getMembers() == null) {
      return List.of();
    }
    return object.getSpec().getMembers();
  }

  public static List<V1alpha1ProjectMember> getSpecMembers(V1alpha1Project object,
      ProjectRoleEnum roleEnum) {
    String targetRole = roleEnum.getStr();
    return getSpecMembers(object).stream()
        .filter(e -> targetRole.equalsIgnoreCase(e.getRole()))
        .toList();
  }

  public static Optional<V1alpha1ProjectSpecQuota> getSpecQuota(V1alpha1Project object) {
    if (object.getSpec() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(object.getSpec().getQuota());
  }

  public static Optional<String> getSpecPvcStorageQuota(V1alpha1Project object) {
    Optional<V1alpha1ProjectSpecQuota> quotaOpt = getSpecQuota(object);
    return quotaOpt.map(V1alpha1ProjectSpecQuota::getPvcStorage);
  }

  public static Map<String, String> getSpecExtendedResourcesQuota(V1alpha1Project object) {
    Optional<V1alpha1ProjectSpecQuota> quotaOpt = getSpecQuota(object);
    if (quotaOpt.isEmpty() || quotaOpt.get().getExtendedResources() == null) {
      return new HashMap<>();
    }
    return quotaOpt.get().getExtendedResources();
  }

  public static Optional<V1alpha1ProjectBinding> getSpecBinding(V1alpha1Project object) {
    if (object.getSpec() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(object.getSpec().getBinding());
  }

  public static List<String> getSpecBindingNodeGroups(V1alpha1Project object) {
    Optional<V1alpha1ProjectBinding> bindingOpt = getSpecBinding(object);
    return bindingOpt
        .map(V1alpha1ProjectBinding::getNodeGroups)
        .filter(Objects::nonNull)
        .orElseGet(List::of);
  }

  public static List<String> getSpecBindingNodes(V1alpha1Project object) {
    Optional<V1alpha1ProjectBinding> bindingOpt = getSpecBinding(object);
    return bindingOpt
        .map(V1alpha1ProjectBinding::getNodes)
        .filter(Objects::nonNull)
        .orElseGet(List::of);
  }

  public static List<String> getSpecBindingImageHubs(V1alpha1Project object) {
    Optional<V1alpha1ProjectBinding> bindingOpt = getSpecBinding(object);
    return bindingOpt
        .map(V1alpha1ProjectBinding::getImageHubs)
        .filter(Objects::nonNull)
        .orElseGet(List::of);
  }

  public static List<String> getStatusAllBoundAipubUsers(V1alpha1Project object) {
    if (object.getStatus() == null ||
        object.getStatus().getAllBoundAipubUsers() == null) {
      return List.of();
    }
    return object.getStatus().getAllBoundAipubUsers();
  }

  public static List<String> getStatusAllBoundImageHubs(V1alpha1Project object) {
    if (object.getStatus() == null ||
        object.getStatus().getAllBoundImageHubs() == null) {
      return List.of();
    }
    return object.getStatus().getAllBoundImageHubs();
  }

  private static V1alpha1ProjectMember clone(V1alpha1ProjectMember member) {
    V1alpha1ProjectMember clone = new V1alpha1ProjectMember();
    clone.setAipubUser(member.getAipubUser());
    if (member.getSubject() != null) {
      RbacV1Subject subject = new RbacV1SubjectBuilder(member.getSubject()).build();
      clone.setSubject(subject);
    }
    clone.setRole(member.getRole());

    return clone;
  }

  private static V1alpha1ProjectSpec clone(V1alpha1ProjectSpec spec) {
    V1alpha1ProjectSpec clone = new V1alpha1ProjectSpec();
    if (spec.getMembers() != null) {
      List<V1alpha1ProjectMember> members = spec.getMembers().stream()
          .map(ProjectUtils::clone)
          .toList();
      clone.setMembers(members);
    }
    if (spec.getQuota() != null) {
      V1alpha1ProjectSpecQuota quota = new V1alpha1ProjectSpecQuota();
      quota.setPvcStorage(spec.getQuota().getPvcStorage());
      clone.setQuota(quota);
    }
    if (spec.getBinding() != null) {
      V1alpha1ProjectBinding binding = new V1alpha1ProjectBinding();
      if (spec.getBinding().getNodes() != null) {
        binding.setNodes(new ArrayList<>(spec.getBinding().getNodes()));
      }
      if (spec.getBinding().getNodeGroups() != null) {
        binding.setNodeGroups(new ArrayList<>(spec.getBinding().getNodeGroups()));
      }
      if (spec.getBinding().getImageHubs() != null) {
        binding.setImageHubs(new ArrayList<>(spec.getBinding().getImageHubs()));
      }
      clone.setBinding(binding);
    }

    return clone;
  }

  private static V1alpha1ProjectStatus clone(V1alpha1ProjectStatus status) {
    V1alpha1ProjectStatus clone = new V1alpha1ProjectStatus();
    if (status.getAllBoundAipubUsers() != null) {
      clone.setAllBoundAipubUsers(new ArrayList<>(status.getAllBoundAipubUsers()));
    }
    if (status.getQuota() != null) {
      V1alpha1ProjectStatusQuota quota = new V1alpha1ProjectStatusQuota();
      if (status.getQuota().getPvcStorage() != null) {
        V1alpha1ProjectStatusQuotaMetric pvcStorage = new V1alpha1ProjectStatusQuotaMetric();
        pvcStorage.setLimit(status.getQuota().getPvcStorage().getLimit());
        pvcStorage.setUsed(status.getQuota().getPvcStorage().getUsed());
        quota.setPvcStorage(pvcStorage);
      }
      clone.setQuota(quota);
    }
    if (status.getAllBoundNodeGroups() != null) {
      clone.setAllBoundNodeGroups(new ArrayList<>(status.getAllBoundNodeGroups()));
    }
    if (status.getAllBoundNodes() != null) {
      clone.setAllBoundNodes(new ArrayList<>(status.getAllBoundNodes()));
    }
    if (status.getAllBoundImageHubs() != null) {
      clone.setAllBoundImageHubs(new ArrayList<>(status.getAllBoundImageHubs()));
    }

    return clone;
  }

}
