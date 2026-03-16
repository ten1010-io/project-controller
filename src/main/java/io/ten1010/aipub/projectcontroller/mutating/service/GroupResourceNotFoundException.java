package io.ten1010.aipub.projectcontroller.mutating.service;

public class GroupResourceNotFoundException extends RuntimeException {

  private final String groupResource;

  public GroupResourceNotFoundException(String groupResource) {
    super("Not found group resource: " + groupResource);
    this.groupResource = groupResource;
  }

  public String getGroupResource() {
    return this.groupResource;
  }

}
