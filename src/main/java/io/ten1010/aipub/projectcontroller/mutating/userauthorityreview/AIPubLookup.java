package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface AIPubLookup {

    @Nullable
    String resolveUserName(String oidcUsername, List<String> groups);

    boolean userExists(String aipubUserName);

    AIPubRole getAIPubRole(List<String> groups, String aipubUserName);

}
