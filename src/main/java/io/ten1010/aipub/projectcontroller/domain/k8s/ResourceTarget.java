package io.ten1010.aipub.projectcontroller.domain.k8s;

/**
 * 소유권 추적 대상 리소스 타입 식별자. group 이 빈 문자열이면 core API 그룹.
 * 소유권 대상은 전부 네임스페이스 스코프 리소스다.
 */
public record ResourceTarget(String group, String version, String plural) {
}
