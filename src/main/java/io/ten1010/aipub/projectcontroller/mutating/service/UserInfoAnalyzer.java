package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.LabelUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Objects;

public class UserInfoAnalyzer {

  private final Indexer<V1alpha1AipubUser> userIndexer;
  private final KeyResolver keyResolver;

  public UserInfoAnalyzer(SharedInformerFactory sharedInformerFactory) {
    this.userIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
        .getIndexer();
    this.keyResolver = new KeyResolver();
  }

  public static boolean isAuthenticated(List<String> groups) {
    return groups.contains(K8sGroupConstants.SYSTEM_AUTHENTICATED_GROUP_NAME);
  }

  public static boolean isServiceAccount(List<String> groups) {
    return groups.contains(K8sGroupConstants.SYSTEM_SERVICEACCOUNTS_GROUP_NAME);
  }

  public static boolean isMaster(List<String> groups) {
    return groups.contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME);
  }

  public static boolean isAipubAdmin(List<String> groups) {
    return groups.contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME);
  }

  public static boolean isAipubMember(List<String> groups) {
    return groups.contains(K8sGroupConstants.AIPUB_MEMBER_GROUP_NAME);
  }

  public UserInfoAnalysis analyze(V1UserInfo userInfo) {
    Objects.requireNonNull(userInfo.getUsername());
    Objects.requireNonNull(userInfo.getGroups());

    V1alpha1AipubUser aipubUser = null;
    if (isAipubMember(userInfo.getGroups())) {
      String aipubUserKey = this.keyResolver.resolveKey(
          LabelUtils.getValueOfLabelString(userInfo.getUsername()));
      aipubUser = this.userIndexer.getByKey(aipubUserKey);
      Objects.requireNonNull(aipubUser);
    }

    return new UserInfoAnalysis(userInfo.getUsername(), userInfo.getGroups(), aipubUser);
  }

  public UserInfoAnalysis analyzeV2(V1UserInfo userInfo) {
    Objects.requireNonNull(userInfo.getUsername());
    Objects.requireNonNull(userInfo.getGroups());

    V1alpha1AipubUser aipubUser = null;
    if (isAipubMember(userInfo.getGroups())) {
      String username = userInfo.getUsername();
      String aipubUserName = username.contains(":")
          ? username.substring(username.lastIndexOf(":") + 1)
          : username;
      String aipubUserKey = this.keyResolver.resolveKey(aipubUserName);
      aipubUser = this.userIndexer.getByKey(aipubUserKey);
    }

    return new UserInfoAnalysis(userInfo.getUsername(), userInfo.getGroups(), aipubUser);
  }

}
