package io.ten1010.aipub.projectcontroller.domain.k8s;

/**
 * 사용자 소유(username 레이블) 오브젝트 하나의 개인 Role 규칙 생성용 식별자.
 * group/resource 는 타입 정의(ResourceTarget)에서, name 은 개별 오브젝트에서 온다.
 */
public record OwnedObject(String group, String resource, String name) {
}
