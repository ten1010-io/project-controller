# fix: ApiClient.buildCall 시그니처 변경 대응 및 OIDC 테스트 개선

## 문제

OIDC 유저가 Deployment를 생성하면 `aipub.ten1010.io/username`, `aipub.ten1010.io/userid` 라벨이 Deployment에는 붙지만, **자식 리소스(ReplicaSet, Pod)에는 전파되지 않는 버그** 발생.

## 원인

`client-java 25.0.0`에서 `ApiClient.buildCall()` 메서드 시그니처가 변경됨:

```
// Before (old client-java)
buildCall(path, method, ...)

// After (client-java 25.0.0)
buildCall(basePath, path, method, ...)
```

기존 코드가 `basePath` 파라미터 없이 호출하고 있어서:
1. **`ApiResourceDiscovery.fetchJson()`** - K8s API 리소스 디스커버리가 실패하여 plurals 맵이 비어 있음 (0개 리소스)
2. **`UserLabelReviewHandler.fetchObject()`** - 부모 리소스 조회가 실패하여 라벨 전파 불가

추가로 `execute(..., String.class)` 호출 시 Gson이 JSON 응답을 String 리터럴로 역직렬화하려 하여 `JsonSyntaxException` 발생.

## 수정 내용

### 1. `ApiResourceDiscovery.java` - K8s API 리소스 디스커버리 수정

- `buildCall()`에 `this.apiClient.getBasePath()` 첫 번째 파라미터 추가
- `execute(..., String.class)` 대신 OkHttp `Call.execute()` 직접 사용하여 raw response body 처리
- 디스커버리 결과 로깅 강화 (발견된 리소스 수, Deployment plural 확인)

### 2. `UserLabelReviewHandler.java` - 부모 리소스 라벨 조회 수정

- `fetchObject()`에 동일한 `buildCall()` 시그니처 수정 적용
- OkHttp `Call.execute()` 직접 사용
- 라벨 전파 과정 디버그 로깅 추가 (INFO 레벨)

### 3. `setup.sh` - OIDC 테스트 컨텍스트 자동 생성

- 셋업 완료 후 `oidc` kubectl context 자동 생성 (cert-free, token only)
- `--token` 대신 `--context=oidc` 사용 안내
- Kind kubeconfig의 client-certificate 인증이 bearer token보다 우선하므로 별도 context 필요

### 4. `LOCAL-TEST.md` - 테스트 가이드 수정

- `--token="$TOKEN"` → `--context=oidc` 변경
- JWT 디코딩 명령어 base64 패딩 처리 추가
- `--token` 플래그가 cert 인증에 의해 무시되는 문제 설명 추가

## 수정된 파일

| 파일 | 변경 |
|------|------|
| `src/.../mutating/service/ApiResourceDiscovery.java` | buildCall 시그니처 수정, OkHttp 직접 사용 |
| `src/.../mutating/service/UserLabelReviewHandler.java` | fetchObject 동일 수정, 디버그 로깅 추가 |
| `k8s-dev/setup.sh` | OIDC context 자동 생성 |
| `k8s-dev/LOCAL-TEST.md` | --context=oidc 사용으로 변경 |

## 검증 결과

```
=== Deployment labels ===
  aipub.ten1010.io/userid: test-user-001
  aipub.ten1010.io/username: testuser

=== ReplicaSet labels ===
  aipub.ten1010.io/userid: test-user-001
  aipub.ten1010.io/username: testuser

=== Pod labels ===
  aipub.ten1010.io/userid: test-user-001
  aipub.ten1010.io/username: testuser
```

Deployment → ReplicaSet → Pod 전 레벨에서 라벨 전파 정상 동작 확인.
