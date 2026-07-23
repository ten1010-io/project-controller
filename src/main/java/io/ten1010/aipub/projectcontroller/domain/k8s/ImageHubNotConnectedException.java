package io.ten1010.aipub.projectcontroller.domain.k8s;

/**
 * Project 에 ImageHub 가 연동되어 있지 않아 image registry 관련 리소스를 resolve 할 수 없을 때 발생한다.
 *
 * <p>이는 오류가 아니라 아직 ImageHub 가 연결되지 않은 정상적으로 발생 가능한 상태이므로,
 * 리컨실러에서 잡아 경고 수준의 로그로 처리한다.
 */
public class ImageHubNotConnectedException extends RuntimeException {

  public ImageHubNotConnectedException(String message) {
    super(message);
  }

}
