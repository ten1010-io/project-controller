package io.ten1010.aipub.projectcontroller.leaderelection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderElectionRunnerTest {

  @TempDir
  Path tempDir;

  @Test
  void namespaceEnvSet_winsOverServiceAccountFile() throws Exception {
    Path file = writeNamespaceFile("from-file");

    assertThat(LeaderElectionRunner.resolveNamespace("from-env", file)).isEqualTo("from-env");
  }

  @Test
  void namespaceEnvBlank_fallsBackToServiceAccountFile() throws Exception {
    Path file = writeNamespaceFile("aipub");

    assertThat(LeaderElectionRunner.resolveNamespace("   ", file)).isEqualTo("aipub");
    assertThat(LeaderElectionRunner.resolveNamespace(null, file)).isEqualTo("aipub");
  }

  @Test
  void namespaceFileHasTrailingNewline_isTrimmed() throws Exception {
    Path file = writeNamespaceFile("aipub\n");

    assertThat(LeaderElectionRunner.resolveNamespace(null, file)).isEqualTo("aipub");
  }

  @Test
  void namespaceFileMissing_failsWithTheOverrideHint() {
    Path missing = this.tempDir.resolve("absent");

    assertThatThrownBy(() -> LeaderElectionRunner.resolveNamespace(null, missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("POD_NAMESPACE");
  }

  @Test
  void namespaceFileBlank_failsInsteadOfUsingAnEmptyNamespace() throws Exception {
    Path file = writeNamespaceFile("  \n");

    assertThatThrownBy(() -> LeaderElectionRunner.resolveNamespace(null, file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("POD_NAMESPACE");
  }

  @Test
  void identityEnvSet_winsOverHostname() {
    assertThat(LeaderElectionRunner.resolveIdentity("pod-a", () -> "host-b")).isEqualTo("pod-a");
  }

  @Test
  void identityEnvBlank_fallsBackToHostname() {
    assertThat(LeaderElectionRunner.resolveIdentity(null, () -> "project-controller-abc"))
        .isEqualTo("project-controller-abc");
    assertThat(LeaderElectionRunner.resolveIdentity("  ", () -> "project-controller-abc"))
        .isEqualTo("project-controller-abc");
  }

  @Test
  void identityHostnameBlank_failsInsteadOfSharingAnEmptyIdentity() {
    // 두 파드가 같은 빈 신원을 들면 서로의 리스를 뺏을 수 있어 선출이 무의미해진다
    assertThatThrownBy(() -> LeaderElectionRunner.resolveIdentity(null, () -> ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("POD_NAME");
  }

  private Path writeNamespaceFile(String content) throws Exception {
    Path file = this.tempDir.resolve("namespace");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

}
