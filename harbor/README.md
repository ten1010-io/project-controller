# Harbor Image Push Setup (macOS)

## 1. harbor/.env 설정

```
HARBOR_REGISTRY=<url>
HARBOR_USERNAME=<username>
HARBOR_PASSWORD=<password>
```

## 2. /etc/hosts 추가

Harbor가 토큰 인증 시 내부 주소(`vnode2.pnode1.idc1.ten1010.io`)로 리다이렉트하므로, 외부 IP로 매핑 필요:

```bash
sudo sh -c 'echo "101.202.0.27  vnode2.pnode1.idc1.ten1010.io" >> /etc/hosts'
```

## 3. Docker Desktop insecure registry 설정

Harbor 인증서가 self-signed이므로 Docker Desktop에서 insecure registry로 등록:

Docker Desktop → Settings → Docker Engine:

```json
{
  "insecure-registries": [
    "vnode2.pnode1.idc1.ten1010.io:8443",
    "external.vnode2.pnode1.idc1.ten1010.io:8443"
  ]
}
```

Apply & Restart.

## 4. 이미지 빌드 & Push

```bash
./image_push.sh <tag>

# 예시
./image_push.sh 1.5.0
```

이미지 경로: `vnode2.pnode1.idc1.ten1010.io:8443/project-controller/project-controller:<tag>`
