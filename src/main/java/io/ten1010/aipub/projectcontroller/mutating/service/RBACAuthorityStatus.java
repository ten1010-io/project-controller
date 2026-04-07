package io.ten1010.aipub.projectcontroller.mutating.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * Port of Python models/user_authority_review.py RBACAuthorityStatus.
 * Tracks RBAC permissions per verb for a specific group/resource[/namespace].
 */
public class RBACAuthorityStatus {

  private static final List<String> BOOL_TYPE_VERBS = List.of("list", "create", "deletecollection");
  private static final List<String> LIST_TYPE_VERBS = List.of("get", "watch", "patch", "update", "delete");

  private final Set<String> get = new HashSet<>();
  @Getter
  private boolean list;
  private final Set<String> watch = new HashSet<>();
  private final Set<String> patch = new HashSet<>();
  private final Set<String> update = new HashSet<>();
  @Getter
  private boolean create;
  private final Set<String> delete = new HashSet<>();
  @Getter
  private boolean deletecollection;

  public void add(String verb, List<String> resourceNames) {
    switch (verb) {
      case "list" -> {
        if (resourceNames.contains("*")) {
          this.list = true;
        }
      }
      case "create" -> {
        if (resourceNames.contains("*")) {
          this.create = true;
        }
      }
      case "deletecollection" -> {
        if (resourceNames.contains("*")) {
          this.deletecollection = true;
        }
      }
      case "get" -> this.get.addAll(resourceNames);
      case "watch" -> this.watch.addAll(resourceNames);
      case "patch" -> this.patch.addAll(resourceNames);
      case "update" -> this.update.addAll(resourceNames);
      case "delete" -> this.delete.addAll(resourceNames);
      default -> { }
    }
  }

  public void addAll(List<String> resourceNames) {
    for (String verb : BOOL_TYPE_VERBS) {
      add(verb, resourceNames);
    }
    for (String verb : LIST_TYPE_VERBS) {
      add(verb, resourceNames);
    }
  }

  public List<String> getGet() {
    if (this.get.contains("*")) {
      return List.of("*");
    }
    return List.copyOf(this.get);
  }

  public void setGet(List<String> value) {
    this.get.clear();
    this.get.addAll(value);
  }

  public List<String> getWatch() {
    if (this.watch.contains("*")) {
      return List.of("*");
    }
    return List.copyOf(this.watch);
  }

  public List<String> getPatch() {
    if (this.patch.contains("*")) {
      return List.of("*");
    }
    return List.copyOf(this.patch);
  }

  public List<String> getUpdate() {
    if (this.update.contains("*")) {
      return List.of("*");
    }
    return List.copyOf(this.update);
  }

  public List<String> getDelete() {
    if (this.delete.contains("*")) {
      return List.of("*");
    }
    return List.copyOf(this.delete);
  }

}
