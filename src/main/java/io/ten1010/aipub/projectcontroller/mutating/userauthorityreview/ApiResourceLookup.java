package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface ApiResourceLookup {

    boolean exists(String groupResource);

    boolean isNamespaced(String groupResource);

    List<String> getAllObjectNames(String groupResource, @Nullable String namespace);

}
