package io.ten1010.aipub.projectcontroller.mutating.service;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class UserInfoAnalysis {

    @Getter
    private final String username;
    @Getter
    private final List<String> groups;

    @Nullable
    private final V1alpha1AipubUser aipubUser;

    public boolean isAuthenticated() {
        return UserInfoAnalyzer.isAuthenticated(this.groups);
    }

    public boolean isServiceAccount() {
        return UserInfoAnalyzer.isServiceAccount(this.groups);
    }

    public boolean isMaster() {
        return UserInfoAnalyzer.isMaster(this.groups);
    }

    public boolean isAipubAdmin() {
        return UserInfoAnalyzer.isAipubAdmin(this.groups);
    }

    public boolean isAipubMember() {
        return UserInfoAnalyzer.isAipubMember(this.groups);
    }

    public Optional<V1alpha1AipubUser> getAipubUser() {
        return Optional.ofNullable(this.aipubUser);
    }

}
