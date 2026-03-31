# Local Kind Test - WorkloadLabel Webhook (E2E)

WorkloadLabelReviewHandler가 자식 리소스 CREATE 시 부모의 workload 라벨을 전파하는지 검증하는 E2E 테스트.

## What gets tested

| Feature | Python (original) | Java (this test) |
|---------|-------------------|------------------|
| Workload name label | `aipub.ten1010.io/workload-name` | `aipub.ten1010.io/workload-name` |
| Workload kind label | `aipub.ten1010.io/workload-kind` | `aipub.ten1010.io/workload-kind` |
| Label propagation | 부모 owner에서 자식으로 전파 | 동일 |
| Label creation | 부모에 라벨 없으면 부모 name/kind로 생성 | 동일 |

## Architecture

```
kubectl create deployment nginx --image=nginx  (admin context)
  │
  ▼
K8s API Server
  │
  ├─► MutatingWebhookConfiguration (workload-labels)
  │     │
  │     ▼
  │   project-controller.aipub.svc:8080
  │   POST /api/v1/admissionreviews
  │     │
  │     └─► WorkloadLabelReviewHandler
  │           → canHandle: CREATE + namespaced? → yes
  │           → Deployment has no ownerRef → skip (allow without mutation)
  │
  ▼
Deployment created (no workload labels — it's the top-level workload)
  │
  ▼
K8s creates ReplicaSet (ownerRef: Deployment)
  │
  ├─► WorkloadLabelReviewHandler
  │     → getOwnerObject: fetch Deployment via K8s API
  │     → getWorkloadLabelsFromOwner: Deployment has no workload labels
  │     → create labels: name=nginx, kind=Deployment
  │     → JSON patch: add workload-name, workload-kind
  │
  ▼
ReplicaSet gets: workload-name=nginx, workload-kind=Deployment
  │
  ▼
K8s creates Pod (ownerRef: ReplicaSet)
  │
  ├─► WorkloadLabelReviewHandler
  │     → getOwnerObject: fetch ReplicaSet via K8s API
  │     → getWorkloadLabelsFromOwner: ReplicaSet HAS workload labels
  │     → propagate labels: name=nginx, kind=Deployment (copied from RS)
  │
  ▼
Pod gets: workload-name=nginx, workload-kind=Deployment
```

## Prerequisites

- [kind](https://kind.sigs.k8s.io/)
- [Docker](https://docs.docker.com/get-docker/)
- kubectl, jq, openssl
- Java 21 Temurin (`sdk use java 21.0.9-tem`)

## Quick Start

기존 `k8s-dev/setup.sh`로 환경을 구성한다. workload-labels webhook은 `mutating-webhook-configuration.yaml`에 이미 포함되어 있으므로 추가 설정 불필요.

```bash
cd k8s-dev
./setup.sh
```

이미 클러스터가 떠 있는 경우 (코드만 변경 후 재배포):

```bash
# 1) 빌드
cd <project-root>
./mvnw -DskipTests clean package -q

# 2) Docker 이미지 빌드 & Kind 로드
docker build -t project-controller:dev -f - . <<'EOF'
FROM eclipse-temurin:21-jre
COPY target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
EOF
kind load docker-image project-controller:dev --name project-controller-dev

# 3) 파드 재시작 (이미지 변경 반영)
kubectl -n aipub rollout restart deployment/project-controller
kubectl -n aipub rollout status deployment/project-controller --timeout=120s
```

## Verify Mutations

### patches.yaml에 workload-labels webhook caBundle 추가

`k8s-dev/patches.yaml`에 새로 추가된 `workload-labels` webhook의 `caBundle` 항목이 필요하다.
기존 다른 webhook들과 동일한 caBundle 값을 사용한다.

`patches.yaml`에 아래 항목이 없으면 추가:

```yaml
  - name: workload-labels.project-controller.project.aipub.ten1010.io
    clientConfig:
      caBundle: <기존 webhook과 동일한 base64 CA>
```

### Test 1: Deployment → ReplicaSet → Pod 라벨 전파

```bash
# Deployment 생성 (admin context — workload label webhook의 대상)
kubectl -n test-ns create deployment nginx --image=nginx

# 잠시 대기 (ReplicaSet, Pod 생성 대기)
sleep 5
```

### Test 2: ReplicaSet workload 라벨 확인

```bash
kubectl -n test-ns get rs -l app=nginx \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

Expected:
```json
{
  "app": "nginx",
  "pod-template-hash": "...",
  "aipub.ten1010.io/workload-name": "nginx",
  "aipub.ten1010.io/workload-kind": "Deployment"
}
```

### Test 3: Pod workload 라벨 확인 (ReplicaSet에서 전파)

```bash
kubectl -n test-ns get pods -l app=nginx \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

Expected:
```json
{
  "app": "nginx",
  "pod-template-hash": "...",
  "aipub.ten1010.io/workload-name": "nginx",
  "aipub.ten1010.io/workload-kind": "Deployment"
}
```

### Test 4: Job → Pod 라벨 전파

```bash
kubectl -n test-ns create job test-job --image=busybox -- echo hello
sleep 5

kubectl -n test-ns get pods -l job-name=test-job \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

Expected:
```json
{
  "job-name": "test-job",
  "aipub.ten1010.io/workload-name": "test-job",
  "aipub.ten1010.io/workload-kind": "Job"
}
```

### Test 5: CronJob → Job → Pod 라벨 전파

```bash
kubectl -n test-ns create cronjob test-cron --image=busybox --schedule="* * * * *" -- echo hello

# CronJob이 Job을 생성할 때까지 최대 1분 대기
echo "Waiting for CronJob to create a Job..."
sleep 65

# Job 라벨 확인 (CronJob에서 생성됨)
kubectl -n test-ns get jobs -l batch.kubernetes.io/controller-uid \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

Expected (Job):
```json
{
  "aipub.ten1010.io/workload-name": "test-cron",
  "aipub.ten1010.io/workload-kind": "CronJob"
}
```

### Test 6: StatefulSet → Pod 라벨 전파

```bash
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: test-sts
  namespace: test-ns
spec:
  serviceName: test-sts
  replicas: 1
  selector:
    matchLabels:
      app: test-sts
  template:
    metadata:
      labels:
        app: test-sts
    spec:
      containers:
        - name: nginx
          image: nginx:latest
EOF

sleep 5

kubectl -n test-ns get pods -l app=test-sts \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

Expected:
```json
{
  "app": "test-sts",
  "statefulset.kubernetes.io/pod-name": "test-sts-0",
  "aipub.ten1010.io/workload-name": "test-sts",
  "aipub.ten1010.io/workload-kind": "StatefulSet"
}
```

### Test 7: 독립 Pod (ownerRef 없음 — 라벨 추가 안 됨)

```bash
kubectl -n test-ns run standalone-pod --image=nginx

kubectl -n test-ns get pod standalone-pod \
  -o jsonpath='{.metadata.labels}' | jq .
```

Expected: `aipub.ten1010.io/workload-name`, `aipub.ten1010.io/workload-kind` 없어야 함.

```json
{
  "run": "standalone-pod"
}
```

### Controller logs

```bash
kubectl -n aipub logs deployment/project-controller -f | grep -i workload
```

Look for:
- `WorkloadLabel handle: namespace=..., operation=CREATE`
- `WorkloadLabel: created labels from owner: name=..., kind=...`
- `WorkloadLabel: propagated labels from owner: name=..., kind=...`
- `WorkloadLabel: no owner object found, allowing without mutation`

## Emergency: Remove workload-labels webhook

workload label webhook에 문제가 생긴 경우, `failurePolicy: Ignore`이므로 webhook이 응답하지 않아도 리소스 생성은 정상 진행된다.

그래도 webhook 자체를 제거해야 하는 경우:

```bash
# MutatingWebhookConfiguration에서 workload-labels webhook만 제거하려면
# 전체 configuration을 수정해야 한다 (같은 MWC에 다른 webhook도 있으므로)
kubectl edit mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io
# → workload-labels.project-controller.project.aipub.ten1010.io 항목 삭제

# 또는 controller를 내리면 모든 webhook이 failurePolicy에 따라 처리됨
kubectl -n aipub scale deployment/project-controller --replicas=0
```

## Cleanup

```bash
# 테스트 리소스 삭제
kubectl -n test-ns delete deployment nginx
kubectl -n test-ns delete job test-job
kubectl -n test-ns delete cronjob test-cron
kubectl -n test-ns delete statefulset test-sts
kubectl -n test-ns delete pod standalone-pod

# 전체 환경 삭제
cd k8s-dev
./cleanup.sh
```

## Troubleshooting

**Controller pod not starting:**
```bash
kubectl -n aipub describe pod -l app=project-controller
kubectl -n aipub logs deployment/project-controller --tail=200
```

**Webhook not intercepting requests:**
```bash
# webhook 등록 확인
kubectl get mutatingwebhookconfiguration project-controller.project.aipub.ten1010.io -o yaml \
  | grep -A 20 "workload-labels"

# webhook이 없으면 kustomize 재배포
kubectl kustomize k8s-dev --load-restrictor LoadRestrictionsNone | kubectl apply -f -
```

**Labels not being added:**
```bash
# 1) webhook이 호출되는지 로그 확인
kubectl -n aipub logs deployment/project-controller -f | grep WorkloadLabel

# 2) dry-run으로 webhook 응답 확인
kubectl -n test-ns run debug --image=nginx --dry-run=server -o yaml | grep -A5 "labels"

# 3) ApiResourceDiscovery 초기화 확인 (로그 시작 부분)
kubectl -n aipub logs deployment/project-controller | grep "Discovered.*API resource"
```

**x509 certificate error:**
```bash
# caBundle 불일치 — cert 재생성 후 재배포
cd k8s-dev
rm -rf certs/controller
./setup.sh  # cert 재생성 + 재배포
```
