# Production Deploy Guide

## 사전 준비 (맥북)

```bash
# 이미지 빌드 & Harbor push
./image_push.sh 1.7.5
```

## 프로덕션 서버에서 실행

### 1. 기존 caBundle 값 확인

```bash
CA_BUNDLE=$(sudo kubectl get mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io \
  -o jsonpath='{.webhooks[0].clientConfig.caBundle}')
echo $CA_BUNDLE
```

### 2. 컨트롤러 이미지 업데이트

```bash
sudo kubectl -n project-controller set image deployment/project-controller \
  project-controller=<HARBOR_REGISTRY>/project-controller/project-controller:1.7.5

# 롤아웃 확인
sudo kubectl -n project-controller rollout status deployment/project-controller --timeout=120s
```

### 3. v2 웹훅 apply

```bash
# caBundle 치환 후 apply
sed "s|<CA_BUNDLE>|${CA_BUNDLE}|g" \
  ./mutating-webhook-user-v2.yaml \
  | sudo kubectl apply -f -
```

### 4. 확인

```bash
# 웹훅 등록 확인
sudo kubectl get mutatingwebhookconfiguration | grep user-v2

# 컨트롤러 로그
sudo kubectl -n project-controller logs deployment/project-controller -f --tail=50
```

### 5. 테스트

```bash
# 사용자 네임스페이스 확인 (시스템 네임스페이스 제외)
sudo kubectl get ns --no-headers | grep -v -E "kube-system|kube-public|kube-node-lease|kube-flannel|aipub|coaster|harbor|ingress-nginx|keycloak|linkerd|metallb|project-controller|aipub-promstack|trident|aipub-efk"

# 테스트할 네임스페이스에서 deployment 생성 (네임스페이스 변경해서 사용)
sudo kubectl -n <NAMESPACE> create deployment test-v2 --image=nginx

# v2 라벨 확인
sudo kubectl -n <NAMESPACE> get workspace label2 -o jsonpath='{.metadata.labels}' | jq .

# v2 owner annotation 확인
sudo kubectl -n <NAMESPACE> get workspace label2 -o jsonpath='{.metadata.annotations.aipub\.ten1010\.io/owner-reference-v2}' | jq .

# 실제 ownerReference 확인 (v1 기존 방식)
sudo kubectl -n <NAMESPACE> get workspace label2 -o jsonpath='{.metadata.ownerReferences}' | jq .

# 정리
sudo kubectl -n <NAMESPACE> delete deployment test-v2
```

### 6. 기존 라벨/ownerReference 확인

```bash
# 기존 라벨 확인 (v1)
sudo kubectl -n <NAMESPACE> get deployment test-v2 -o jsonpath='{.metadata.labels}' | jq .

# 기존 ownerReference 확인 (v1)
sudo kubectl -n <NAMESPACE> get deployment test-v2 -o jsonpath='{.metadata.ownerReferences}' | jq .

# v2 라벨 확인
sudo kubectl -n <NAMESPACE> get deployment test-v2 -o jsonpath='{.metadata.labels.aipub\.ten1010\.io/username-v2}'

# v2 owner annotation 확인
sudo kubectl -n <NAMESPACE> get deployment test-v2 -o jsonpath='{.metadata.annotations.aipub\.ten1010\.io/owner-reference-v2}' | jq .
```

### 7. 특정 라벨로 리소스 검색

```bash
# v2 username 라벨로 검색
sudo kubectl -n <NAMESPACE> get all -l aipub.ten1010.io/username-v2=<USERNAME>

# v2 userid 라벨로 검색
sudo kubectl -n <NAMESPACE> get all -l aipub.ten1010.io/userid-v2=<USER_ID>

# 기존 v1 username 라벨로 검색
sudo kubectl -n <NAMESPACE> get all -l aipub.ten1010.io/username=<USERNAME>

# 특정 네임스페이스 전체에서 v2 라벨 가진 리소스 검색
sudo kubectl -n <NAMESPACE> get deployments,pods,services,configmaps,secrets -l aipub.ten1010.io/username-v2

# 모든 네임스페이스에서 검색
sudo kubectl get deployments --all-namespaces -l aipub.ten1010.io/username-v2=<USERNAME>
```

## 한줄 배포 명령어

```bash
CA_BUNDLE=$(sudo kubectl get mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io -o jsonpath='{.webhooks[0].clientConfig.caBundle}') && sed "s|<CA_BUNDLE>|${CA_BUNDLE}|g" kubernetes/controller/project-controller/templates/mutating-webhook-user-v2.yaml | sudo kubectl apply -f -
```

## UserAuthorityReview 웹훅 전환 (Python → Java)

Python `aipub-admission-controller`의 userauthorityreview mutate/validate를 Java `project-controller`로 전환하는 절차.

### 1. Python 측 UserAuthorityReview 웹훅 비활성화

Python의 MutatingWebhookConfiguration(`aipub-admission-controller`)에서 userauthorityreview 항목을 제거합니다.
ValidatingWebhookConfiguration도 동일하게 제거합니다.

```bash
# 현재 Python 웹훅에 등록된 userauthorityreview 확인
sudo kubectl get mutatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '.webhooks[] | select(.name | contains("userauthorityreview")) | .name'

sudo kubectl get validatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '.webhooks[] | select(.name | contains("userauthorityreview")) | .name'
```

웹훅 항목 제거 (MWC에서 userauthorityreview 항목만 삭제):

```bash
# mutating — userauthorityreview 항목 제거
sudo kubectl get mutatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '.webhooks = [.webhooks[] | select(.name | contains("userauthorityreview") | not)]' \
  | sudo kubectl apply -f -

# validating — userauthorityreview 항목 제거
sudo kubectl get validatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '.webhooks = [.webhooks[] | select(.name | contains("userauthorityreview") | not)]' \
  | sudo kubectl apply -f -
```

제거 확인:

```bash
sudo kubectl get mutatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '[.webhooks[].name]'
# → userauthorityreview.admission-controller.aipub.ten1010.io 가 없어야 함

sudo kubectl get validatingwebhookconfiguration aipub-admission-controller -o json \
  | jq '[.webhooks[].name]'
# → 동일
```

### 2. Java 측 UserAuthorityReview 웹훅 등록

```bash
# caBundle 가져오기
CA_BUNDLE=$(sudo kubectl get mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io \
  -o jsonpath='{.webhooks[0].clientConfig.caBundle}')

# Mutating webhook 등록
sed "s|<CA_BUNDLE>|${CA_BUNDLE}|g" \
  kubernetes/controller/project-controller/templates/mutating-webhook-user-authority-review.yaml \
  | sudo kubectl apply -f -

# Validating webhook 등록
sed "s|<CA_BUNDLE>|${CA_BUNDLE}|g" \
  kubernetes/controller/project-controller/templates/validating-webhook-user-authority-review.yaml \
  | sudo kubectl apply -f -
```

등록 확인:

```bash
sudo kubectl get mutatingwebhookconfiguration | grep userauthorityreview
sudo kubectl get validatingwebhookconfiguration | grep userauthorityreview
```

### 3. 동작 확인

```bash
# 컨트롤러 로그에서 UserAuthorityReview 처리 확인
sudo kubectl -n project-controller logs deployment/project-controller -f | grep -i "UserAuthorityReview"

# 테스트: UserAuthorityReview 생성 (OIDC 사용자가 실행해야 함)
# status에 RBAC 권한이 채워지고, ownerReferences에 dummy가 있으면 성공
sudo kubectl get uar -o json | jq '.items[-1] | {status, ownerReferences: .metadata.ownerReferences}'
```

### 4. 롤백 (Java → Python 복원)

```bash
# Java 웹훅 제거
sudo kubectl delete mutatingwebhookconfiguration userauthorityreview-mutate.project-controller.project.aipub.ten1010.io
sudo kubectl delete validatingwebhookconfiguration userauthorityreview-validate.project-controller.project.aipub.ten1010.io

# Python 웹훅 복원 (원본 YAML 재적용)
# aipub-admission-controller의 mutating-webhook-config.yaml, validating-webhook-config.yaml 재적용
```

## 롤백

```bash
# v2 웹훅만 제거 (기존 웹훅에 영향 없음)
sudo kubectl delete mutatingwebhookconfiguration userrelationship-v2.project-controller.project.aipub.ten1010.io

# 컨트롤러 이미지 이전 버전으로 복구
sudo kubectl -n project-controller rollout undo deployment/project-controller
```
