package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class RBACAuthorityStatus {

    private static final Set<String> BOOL_VERBS = Set.of("list", "create", "deletecollection");

    private final Set<String> getNames;
    private boolean list;
    private final Set<String> watchNames;
    private final Set<String> patchNames;
    private final Set<String> updateNames;
    private boolean create;
    private final Set<String> deleteNames;
    private boolean deletecollection;

    public RBACAuthorityStatus() {
        this.getNames = new HashSet<>();
        this.list = false;
        this.watchNames = new HashSet<>();
        this.patchNames = new HashSet<>();
        this.updateNames = new HashSet<>();
        this.create = false;
        this.deleteNames = new HashSet<>();
        this.deletecollection = false;
    }

    public void add(String verb, List<String> resourceNames) {
        if (BOOL_VERBS.contains(verb)) {
            if (resourceNames.size() == 1 && "*".equals(resourceNames.get(0))) {
                setBoolVerb(verb, true);
            }
            return;
        }
        Optional<Set<String>> set = getSetForVerb(verb);
        set.ifPresent(s -> s.addAll(resourceNames));
    }

    public void addAll(List<String> resourceNames) {
        for (String verb : allVerbs()) {
            add(verb, resourceNames);
        }
    }

    public void setGet(List<String> value) {
        this.getNames.clear();
        this.getNames.addAll(value);
    }

    @JsonProperty("get")
    public List<String> getGet() {
        return toOutputList(getNames);
    }

    @JsonProperty("list")
    public boolean isList() {
        return list;
    }

    @JsonProperty("watch")
    public List<String> getWatch() {
        return toOutputList(watchNames);
    }

    @JsonProperty("patch")
    public List<String> getPatch() {
        return toOutputList(patchNames);
    }

    @JsonProperty("update")
    public List<String> getUpdate() {
        return toOutputList(updateNames);
    }

    @JsonProperty("create")
    public boolean isCreate() {
        return create;
    }

    @JsonProperty("delete")
    public List<String> getDelete() {
        return toOutputList(deleteNames);
    }

    @JsonProperty("deletecollection")
    public boolean isDeletecollection() {
        return deletecollection;
    }

    private List<String> toOutputList(Set<String> names) {
        if (names.contains("*")) {
            return List.of("*");
        }
        return new ArrayList<>(names);
    }

    private void setBoolVerb(String verb, boolean value) {
        switch (verb) {
            case "list":
                this.list = value;
                break;
            case "create":
                this.create = value;
                break;
            case "deletecollection":
                this.deletecollection = value;
                break;
        }
    }

    private Optional<Set<String>> getSetForVerb(String verb) {
        switch (verb) {
            case "get":
                return Optional.of(getNames);
            case "watch":
                return Optional.of(watchNames);
            case "patch":
                return Optional.of(patchNames);
            case "update":
                return Optional.of(updateNames);
            case "delete":
                return Optional.of(deleteNames);
            default:
                return Optional.empty();
        }
    }

    private static List<String> allVerbs() {
        return List.of("get", "list", "watch", "patch", "update", "create", "delete", "deletecollection");
    }

}
