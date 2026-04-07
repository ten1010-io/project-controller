# Webhook Configuration 목록

## MutatingWebhookConfiguration

### 1. `project-controller.project.aipub.ten1010.io`

기존 리소스 mutation용. 단일 MWC에 5개 webhook 항목 포함.

| Webhook name | Endpoint | Resource | failurePolicy |
|-------------|----------|----------|---------------|
| `pods.project-controller.project.aipub.ten1010.io` | `/api/v1/admissionreviews` | pods (CREATE/UPDATE) | Ignore |
| `deployments.project-controller.project.aipub.ten1010.io` | `/api/v1/admissionreviews` | deployments (CREATE/UPDATE) | Ignore |
| `namespaces.project-controller.project.aipub.ten1010.io` | `/api/v1/admissionreviews` | namespaces (CREATE) | Ignore |
| `projects.project-controller.project.aipub.ten1010.io` | `/api/v1/admissionreviews` | projects (CREATE) | Fail |
| `imagereviews.project-controller.project.aipub.ten1010.io` | `/api/v1/admissionreviews` | imagereviews (CREATE) | Fail |

YAML: `mutating-webhook-configuration.yaml`

---

### 2. `userrelationship-v2.project-controller.project.aipub.ten1010.io`

사용자 리소스 생성 시 ownerReference + label 주입.

| Webhook name | Endpoint | Resource | failurePolicy |
|-------------|----------|----------|---------------|
| `userrelationship-v2.project-controller.project.aipub.ten1010.io` | `/api/v1/userrelationship/mutate` | pods, replicationcontrollers, services, configmaps, secrets, pvc, ingresses, batch/*, apps/*, aipub.ten1010.io/* (CREATE, Namespaced) | Ignore |

YAML: `mutating-webhook-user-v2.yaml`

---

### 3. `workload-label.project-controller.project.aipub.ten1010.io`

워크로드 생성 시 부모의 workload label 전파.

| Webhook name | Endpoint | Resource | failurePolicy |
|-------------|----------|----------|---------------|
| `workload-label.project-controller.project.aipub.ten1010.io` | `/api/v1/workloadlabel/mutate` | pods, replicationcontrollers, daemonsets, deployments, replicasets, statefulsets, jobs, cronjobs (CREATE, Namespaced) | Ignore |

YAML: `mutating-webhook-workload-label.yaml`

---

### 4. `userauthorityreview-mutate.project-controller.project.aipub.ten1010.io`

UserAuthorityReview 생성 시 RBAC 권한 분석 → status + dummy ownerRef 주입.

| Webhook name | Endpoint | Resource | failurePolicy |
|-------------|----------|----------|---------------|
| `userauthorityreview-mutate.project-controller.project.aipub.ten1010.io` | `/api/v1/userauthorityreview/mutate` | userauthorityreviews (CREATE, *) | Fail |

YAML: `mutating-webhook-user-authority-review.yaml`

---

## ValidatingWebhookConfiguration

### 1. `userauthorityreview-validate.project-controller.project.aipub.ten1010.io`

UserAuthorityReview 생성 시 mutate webhook이 정상 수행되었는지(dummy ownerRef 존재) 검증.

| Webhook name | Endpoint | Resource | failurePolicy |
|-------------|----------|----------|---------------|
| `userauthorityreview-validate.project-controller.project.aipub.ten1010.io` | `/api/v1/userauthorityreview/validate` | userauthorityreviews (CREATE, *) | Fail |

YAML: `validating-webhook-user-authority-review.yaml`

---

## Python 대응 관계

| Python MWC/VWC (`aipub-admission-controller`) | Java MWC/VWC |
|-----------------------------------------------|-------------|
| MWC `aipub-admission-controller` > `user.*` | MWC `userrelationship-v2.*` |
| MWC `aipub-admission-controller` > `workload.*` | MWC `workload-label.*` |
| MWC `aipub-admission-controller` > `userauthorityreview.*` | MWC `userauthorityreview-mutate.*` |
| VWC `aipub-admission-controller` > `user.*` | (미포팅) |
| VWC `aipub-admission-controller` > `userauthorityreview.*` | VWC `userauthorityreview-validate.*` |

## 설계 원칙

- **MWC/VWC를 webhook별로 분리** — 독립 배포/롤백 가능. 하나의 webhook에 문제가 생겨도 다른 webhook에 영향 없음
- **failurePolicy: Fail** — 권한/보안 관련 webhook (projects, imagereviews, userauthorityreview). webhook 실패 시 리소스 생성 차단
- **failurePolicy: Ignore** — 부가 기능 webhook (pods, deployments, namespaces, userrelationship, workload-label). webhook 실패해도 리소스 생성 허용
