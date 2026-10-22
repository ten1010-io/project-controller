package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 인포머와 같은 Gson 설정으로 CR 을 읽어 필드명 오타를 잡는다.
// 리컨실 경로가 name/label 만 쓰기 때문에, 여기서 안 보면 어긋나도 드러나지 않는다.
class WorkloadDtoDeserializationTest {

  private static final JSON JSON_CODEC = new JSON();

  @Test
  @DisplayName("Workspace 의 expose.inference 가 매핑된다")
  void workspaceExposeInference_isMapped() {
    String json = """
        {
          "apiVersion": "aipub.ten1010.io/v1beta1",
          "kind": "Workspace",
          "metadata": { "name": "ws", "namespace": "proj" },
          "spec": {
            "ports": [
              {
                "name": "infer",
                "port": 8000,
                "expose": {
                  "enabled": true,
                  "type": "INFERENCE",
                  "inference": { "host": "infer-ws", "path": "/v1" }
                }
              }
            ]
          }
        }
        """;

    V1beta1Workspace workspace = JSON_CODEC.deserialize(json, V1beta1Workspace.class);

    V1beta1WorkspacePortExpose expose = workspace.getSpec().getPorts().get(0).getExpose();
    assertThat(expose.getType()).isEqualTo("INFERENCE");
    assertThat(expose.getInference().getHost()).isEqualTo("infer-ws");
    assertThat(expose.getInference().getPath()).isEqualTo("/v1");
    assertThat(expose.getTcp()).isNull();
    assertThat(expose.getHttp()).isNull();
  }

  @Test
  @DisplayName("Workspace 의 suspendOption.commit 과 status.suspendCommit 이 매핑된다")
  void workspaceSuspendCommit_isMapped() {
    String json = """
        {
          "apiVersion": "aipub.ten1010.io/v1beta1",
          "kind": "Workspace",
          "metadata": { "name": "ws", "namespace": "proj" },
          "spec": {
            "suspend": true,
            "suspendOption": {
              "commit": {
                "enabled": true,
                "repository": "harbor.example.com/team-a/ws-snapshots",
                "applyImage": true
              }
            }
          },
          "status": {
            "suspendCommit": {
              "image": "harbor.example.com/team-a/ws-snapshots:1",
              "prevImage": "busybox:1.36",
              "appliedImage": "harbor.example.com/team-a/ws-snapshots:1"
            }
          }
        }
        """;

    V1beta1Workspace workspace = JSON_CODEC.deserialize(json, V1beta1Workspace.class);

    V1beta1WorkspaceSuspendOption.Commit commit = workspace.getSpec().getSuspendOption().getCommit();
    assertThat(commit.getEnabled()).isTrue();
    assertThat(commit.getRepository()).isEqualTo("harbor.example.com/team-a/ws-snapshots");
    assertThat(commit.getApplyImage()).isTrue();

    V1beta1WorkspaceSuspendCommitStatus suspendCommit = workspace.getStatus().getSuspendCommit();
    assertThat(suspendCommit.getImage()).isEqualTo("harbor.example.com/team-a/ws-snapshots:1");
    assertThat(suspendCommit.getPrevImage()).isEqualTo("busybox:1.36");
    assertThat(suspendCommit.getAppliedImage())
        .isEqualTo("harbor.example.com/team-a/ws-snapshots:1");
  }

  @Test
  @DisplayName("Operation 의 expose.inference 가 매핑된다")
  void operationExposeInference_isMapped() {
    String json = """
        {
          "apiVersion": "aipub.ten1010.io/v1alpha1",
          "kind": "Operation",
          "metadata": { "name": "op", "namespace": "proj" },
          "spec": {
            "ports": [
              {
                "name": "infer",
                "port": 8000,
                "expose": {
                  "enabled": true,
                  "type": "INFERENCE",
                  "inference": { "host": "infer-op", "path": "/v1" }
                }
              }
            ]
          },
          "status": {
            "ports": [
              { "name": "infer", "port": 8000, "type": "INFERENCE",
                "externalAddress": "https://infer-op.example.com/v1" }
            ]
          }
        }
        """;

    V1alpha1Operation operation = JSON_CODEC.deserialize(json, V1alpha1Operation.class);

    V1alpha1OperationPortExpose expose = operation.getSpec().getPorts().get(0).getExpose();
    assertThat(expose.getType()).isEqualTo("INFERENCE");
    assertThat(expose.getInference().getHost()).isEqualTo("infer-op");
    assertThat(expose.getInference().getPath()).isEqualTo("/v1");

    V1alpha1OperationStatusPort statusPort = operation.getStatus().getPorts().get(0);
    assertThat(statusPort.getType()).isEqualTo("INFERENCE");
    assertThat(statusPort.getExternalAddress()).isEqualTo("https://infer-op.example.com/v1");
  }

  @Test
  @DisplayName("Workspace 의 tools.type 은 vscode 를 그대로 싣는다")
  void workspaceVscodeTool_isMapped() {
    String json = """
        {
          "apiVersion": "aipub.ten1010.io/v1beta1",
          "kind": "Workspace",
          "metadata": { "name": "ws", "namespace": "proj" },
          "spec": { "tools": [ { "type": "vscode" } ] },
          "status": { "tools": [ { "type": "vscode", "address": "https://vscode-ws.example.com/" } ] }
        }
        """;

    V1beta1Workspace workspace = JSON_CODEC.deserialize(json, V1beta1Workspace.class);

    assertThat(workspace.getSpec().getTools().get(0).getType()).isEqualTo("vscode");
    assertThat(workspace.getStatus().getTools().get(0).getAddress())
        .isEqualTo("https://vscode-ws.example.com/");
  }
}
