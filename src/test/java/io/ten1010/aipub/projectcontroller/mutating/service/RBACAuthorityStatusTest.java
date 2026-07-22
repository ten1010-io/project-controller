package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RBACAuthorityStatusTest {

  @Test
  void initialState_allEmpty() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();

    assertThat(status.getGet()).isEmpty();
    assertThat(status.isList()).isFalse();
    assertThat(status.getWatch()).isEmpty();
    assertThat(status.getPatch()).isEmpty();
    assertThat(status.getUpdate()).isEmpty();
    assertThat(status.isCreate()).isFalse();
    assertThat(status.getDelete()).isEmpty();
    assertThat(status.isDeletecollection()).isFalse();
  }

  // Python: add("get", ["pod1", "pod2"]) → get = {"pod1", "pod2"}
  @Test
  void add_listTypeVerb_addsResourceNames() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("get", List.of("pod1", "pod2"));

    assertThat(status.getGet()).containsExactlyInAnyOrder("pod1", "pod2");
  }

  // Python: add("list", ["*"]) → list = True
  @Test
  void add_boolTypeVerb_withWildcard_setsTrue() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("list", List.of("*"));

    assertThat(status.isList()).isTrue();
  }

  // Python: add("list", ["specific"]) → list remains False (not ["*"])
  @Test
  void add_boolTypeVerb_withoutWildcard_remainsFalse() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("list", List.of("specific"));

    assertThat(status.isList()).isFalse();
  }

  // Python: add("get", ["*"]) → get = ["*"]
  @Test
  void add_wildcardResourceName_collapsesToWildcard() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("get", List.of("pod1"));
    status.add("get", List.of("*"));

    assertThat(status.getGet()).containsExactly("*");
  }

  // Python: add_all(["*"]) → all verbs set
  @Test
  void addAll_wildcard_setsAllVerbs() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.addAll(List.of("*"));

    assertThat(status.getGet()).containsExactly("*");
    assertThat(status.isList()).isTrue();
    assertThat(status.getWatch()).containsExactly("*");
    assertThat(status.getPatch()).containsExactly("*");
    assertThat(status.getUpdate()).containsExactly("*");
    assertThat(status.isCreate()).isTrue();
    assertThat(status.getDelete()).containsExactly("*");
    assertThat(status.isDeletecollection()).isTrue();
  }

  // Python: add_all(["pod1"]) → list type verbs stay false (not "*")
  @Test
  void addAll_specificName_boolVerbsRemainFalse() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.addAll(List.of("pod1"));

    assertThat(status.getGet()).containsExactly("pod1");
    assertThat(status.isList()).isFalse();
    assertThat(status.isCreate()).isFalse();
  }

  // Python: union of multiple adds
  @Test
  void add_multipleAdds_unionResults() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("get", List.of("pod1"));
    status.add("get", List.of("pod2"));
    status.add("get", List.of("pod1")); // duplicate

    assertThat(status.getGet()).containsExactlyInAnyOrder("pod1", "pod2");
  }

  // Python: set(verb="get", value=["a", "b"]) replaces existing
  @Test
  void setGet_replacesExistingValues() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("get", List.of("*"));
    assertThat(status.getGet()).containsExactly("*");

    status.setGet(List.of("pod1", "pod2"));
    assertThat(status.getGet()).containsExactlyInAnyOrder("pod1", "pod2");
  }

  // Python: unknown verb → ignored
  @Test
  void add_unknownVerb_ignored() {
    RBACAuthorityStatus status = new RBACAuthorityStatus();
    status.add("unknownverb", List.of("*"));

    // No exception, no change
    assertThat(status.getGet()).isEmpty();
    assertThat(status.isList()).isFalse();
  }
}
