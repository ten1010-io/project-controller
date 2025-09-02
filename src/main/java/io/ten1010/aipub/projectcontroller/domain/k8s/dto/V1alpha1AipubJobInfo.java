package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;

@Data
public class V1alpha1AipubJobInfo {

    private String creationTimestamp;
    private Long exitCode;
    private String finishedAt;
    private String name;
    private String nodeName;
    private String reason;
    private String startedAt;
    private String timestamp;
    private String uid;

}
