package io.ten1010.aipub.projectcontroller.informer.owned;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OwnedObjectInformerManagerTest {

  // username 레이블 존재 + 시스템 파생물 표식 부재를 서버사이드에서 동시에 요구한다.
  // 파생 Endpoints(endpoint-controller 가 Service 레이블을 복사해 생성)가
  // 소유권 추적에 들어오지 않도록 하는 필터다.
  @Test
  void ownedObjectsLabelSelector_requiresUsernameAndExcludesSystemDerived() {
    assertThat(OwnedObjectInformerManager.ownedObjectsLabelSelector())
        .isEqualTo("aipub.ten1010.io/username,!endpoints.kubernetes.io/managed-by");
  }

}
