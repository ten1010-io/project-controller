# Production Deploy Guide

## 사전 준비 (맥북)

```bash
# 이미지 빌드 & Harbor push
./image_push.sh 1.5.0
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
  project-controller=registry.ten1010.io:8443/project-controller/project-controller:1.5.0

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
sudo kubectl -n taehyeong create deployment test-v2 --image=nginx

# v2 라벨 확인
sudo kubectl -n taehyeong get workspace label2 -o jsonpath='{.metadata.labels}' | jq .

# v2 owner annotation 확인
sudo kubectl -n taehyeong get workspace label2 -o jsonpath='{.metadata.annotations.aipub\.ten1010\.io/owner-reference-v2}' | jq .

# 실제 ownerReference 확인 (v1 기존 방식)
sudo kubectl -n taehyeong get workspace label2 -o jsonpath='{.metadata.ownerReferences}' | jq .

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
sudo kubectl -n taehyeong get all -l aipub.ten1010.io/username-v2=taehyeong

# v2 userid 라벨로 검색
sudo kubectl -n taehyeong get all -l aipub.ten1010.io/userid-v2=test-user-001

# 기존 v1 username 라벨로 검색
sudo kubectl -n <NAMESPACE> get all -l aipub.ten1010.io/username=testuser

# 특정 네임스페이스 전체에서 v2 라벨 가진 리소스 검색
sudo kubectl -n <NAMESPACE> get deployments,pods,services,configmaps,secrets -l aipub.ten1010.io/username-v2

# 모든 네임스페이스에서 검색
sudo kubectl get deployments --all-namespaces -l aipub.ten1010.io/username-v2=testuser
```

## 한줄 배포 명령어

```bash
CA_BUNDLE=$(sudo kubectl get mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io -o jsonpath='{.webhooks[0].clientConfig.caBundle}') && sed "s|<CA_BUNDLE>|${CA_BUNDLE}|g" kubernetes/controller/project-controller/templates/mutating-webhook-user-v2.yaml | sudo kubectl apply -f -
```

## 롤백

```bash
# v2 웹훅만 제거 (기존 웹훅에 영향 없음)
sudo kubectl delete mutatingwebhookconfiguration userrelationship-v2.project-controller.project.aipub.ten1010.io

# 컨트롤러 이미지 이전 버전으로 복구
sudo kubectl -n project-controller rollout undo deployment/project-controller
```
