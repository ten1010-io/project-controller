package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RBACAuthorityStatusTest {

    @Test
    void should_initialize_with_empty_state() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        Assertions.assertEquals(List.of(), status.getGet());
        Assertions.assertFalse(status.isList());
        Assertions.assertEquals(List.of(), status.getWatch());
        Assertions.assertEquals(List.of(), status.getPatch());
        Assertions.assertEquals(List.of(), status.getUpdate());
        Assertions.assertFalse(status.isCreate());
        Assertions.assertEquals(List.of(), status.getDelete());
        Assertions.assertFalse(status.isDeletecollection());
    }

    @Test
    void should_add_specific_resource_names_to_set_verb() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("get", List.of("deployment-1", "deployment-2"));

        Assertions.assertTrue(status.getGet().contains("deployment-1"));
        Assertions.assertTrue(status.getGet().contains("deployment-2"));
        Assertions.assertEquals(2, status.getGet().size());
    }

    @Test
    void should_return_wildcard_when_set_contains_asterisk() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("get", List.of("*"));

        Assertions.assertEquals(List.of("*"), status.getGet());
    }

    @Test
    void should_set_bool_verb_to_true_only_with_wildcard() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("list", List.of("*"));
        Assertions.assertTrue(status.isList());

        RBACAuthorityStatus status2 = new RBACAuthorityStatus();
        status2.add("list", List.of("some-name"));
        Assertions.assertFalse(status2.isList());
    }

    @Test
    void should_set_create_bool_verb() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("create", List.of("*"));

        Assertions.assertTrue(status.isCreate());
    }

    @Test
    void should_set_deletecollection_bool_verb() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("deletecollection", List.of("*"));

        Assertions.assertTrue(status.isDeletecollection());
    }

    @Test
    void should_addAll_apply_to_all_verbs() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.addAll(List.of("*"));

        Assertions.assertEquals(List.of("*"), status.getGet());
        Assertions.assertTrue(status.isList());
        Assertions.assertEquals(List.of("*"), status.getWatch());
        Assertions.assertEquals(List.of("*"), status.getPatch());
        Assertions.assertEquals(List.of("*"), status.getUpdate());
        Assertions.assertTrue(status.isCreate());
        Assertions.assertEquals(List.of("*"), status.getDelete());
        Assertions.assertTrue(status.isDeletecollection());
    }

    @Test
    void should_addAll_with_specific_names_set_collection_verbs_but_not_booleans() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.addAll(List.of("obj-1", "obj-2"));

        Assertions.assertTrue(status.getGet().containsAll(List.of("obj-1", "obj-2")));
        Assertions.assertFalse(status.isList());
        Assertions.assertTrue(status.getWatch().containsAll(List.of("obj-1", "obj-2")));
        Assertions.assertFalse(status.isCreate());
        Assertions.assertFalse(status.isDeletecollection());
    }

    @Test
    void should_union_multiple_adds() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("get", List.of("a", "b"));
        status.add("get", List.of("b", "c"));

        Assertions.assertEquals(3, status.getGet().size());
        Assertions.assertTrue(status.getGet().containsAll(List.of("a", "b", "c")));
    }

    @Test
    void should_setGet_replace_existing_values() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("get", List.of("*"));
        Assertions.assertEquals(List.of("*"), status.getGet());

        status.setGet(List.of("obj-1", "obj-2"));
        Assertions.assertEquals(2, status.getGet().size());
        Assertions.assertTrue(status.getGet().containsAll(List.of("obj-1", "obj-2")));
    }

    @Test
    void should_ignore_unknown_verb() {
        RBACAuthorityStatus status = new RBACAuthorityStatus();

        status.add("unknown-verb", List.of("*"));

        Assertions.assertEquals(List.of(), status.getGet());
        Assertions.assertFalse(status.isList());
    }

}
