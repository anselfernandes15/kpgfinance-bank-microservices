# KPG Finance: Cloud-Native Banking Microservices on Kubernetes

A distributed banking backend built with **Spring Boot 4 / Spring Cloud 2025**, packaged as OCI images with **Jib**, and deployed to **Kubernetes** with a layered set of **Helm** charts. The project covers the main pieces of a production-style microservice platform: an edge gateway, centralized config, Kubernetes-native service discovery, OAuth2 security, event-driven messaging, resilience patterns, and full observability (logs, metrics, traces).

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1-brightgreen) ![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-blue) ![Kafka](https://img.shields.io/badge/Kafka-4.x-black) ![Keycloak](https://img.shields.io/badge/Keycloak-OAuth2-red)

---

## Table of contents

- [Architecture](#architecture)
- [Skills demonstrated](#skills-demonstrated)
- [Repository layout](#repository-layout)
- [Kubernetes deployment (Helm)](#kubernetes-deployment-helm)
  - [Prerequisites](#prerequisites)
  - [1. Create the secrets](#1-create-the-secrets)
  - [2. Build the chart dependencies](#2-build-the-chart-dependencies)
  - [3. Install the platform](#3-install-the-platform)
  - [4. Verify the rollout](#4-verify-the-rollout)
  - [5. Access the services](#5-access-the-services)
  - [Multiple environments (default / QA / prod)](#multiple-environments-default--qa--prod)
  - [Upgrade, rollback, uninstall](#upgrade-rollback-uninstall)
- [Helm chart design](#helm-chart-design)
- [Kubernetes features used](#kubernetes-features-used)
- [Calling the APIs](#calling-the-apis)
- [Observability](#observability)
- [Building the container images](#building-the-container-images)
- [Secrets management](#secrets-management)
- [Troubleshooting](#troubleshooting)

---

## Architecture

```
                        ┌──────────────┐
          OAuth2 JWT    │   Keycloak   │  (client-credentials, role-based access)
        ┌──────────────►│  :7080       │
        │               └──────────────┘
        │                      ▲ JWKS
┌───────┴──┐   HTTPS    ┌──────┴────────┐  rate limit  ┌─────────┐
│  Client  │──────────► │ Gateway Server│◄────────────►│  Redis  │
└──────────┘            │ :8072 (edge)  │              └─────────┘
                        └──┬─────┬─────┬┘
          circuit breaker  │     │     │ retry
                ┌──────────┘     │     └──────────┐
                ▼                ▼                ▼
          ┌──────────┐     ┌──────────┐     ┌──────────┐
          │ Accounts │────►│  Loans   │     │  Cards   │
          │  :9000   │────►│  :9001   │     │  :9002   │
          └────┬─────┘     └──────────┘     └──────────┘
               │ publish / consume (Spring Cloud Stream)
               ▼
          ┌──────────┐            ┌──────────┐
          │  Kafka   │◄──────────►│ Message  │ (email | sms functions)
          │  :9092   │            │  :9010   │
          └──────────┘            └──────────┘

  Every service ──► Config Server :8071 (Git-backed, per-profile config)
  Every service ──► Spring Cloud Kubernetes Discovery Server (reads the K8s API)
  Every service ──► Grafana LGTM :4318 (OTLP logs + metrics + traces)
```

| Service | Port | Responsibility |
|---|---|---|
| `gatewayserver` | 8072 | Edge server: routing, JWT validation, role-based authorization, circuit breaker, retry, Redis rate limiter, trace-id response header |
| `configserver` | 8071 | Spring Cloud Config Server backed by a Git repo, with `default` / `qa` / `prod` profiles and `{cipher}` encrypted values |
| `discoveryserver` | 80 → 8761 | Spring Cloud Kubernetes Discovery Server. Discovers Pods/Services through the Kubernetes API (RBAC-scoped) |
| `accounts` | 9000 | Customer and account management. Aggregates loans and cards data over HTTP service clients with fallbacks. Publishes events to Kafka |
| `loans` | 9001 | Loan management |
| `cards` | 9002 | Card management |
| `message` | 9010 | Event-driven consumer (Spring Cloud Function `email\|sms`) that acknowledges back over Kafka |
| `keycloak` | 7080 | Identity provider / OAuth2 authorization server |
| `kafka` | 9092 | Event broker (KRaft mode, no ZooKeeper) |
| `redis` | 6379 | Backing store for the gateway's distributed rate limiter |
| `grafana-lgtm` | 3000 / 4318 | Grafana + Loki + Tempo + Mimir (Prometheus) all-in-one OTLP backend |

---

## Skills demonstrated

**Microservices**
- Domain-split services (accounts / loans / cards / message), each with its own data store
- API gateway pattern with path rewriting and per-route filters
- Externalized, environment-specific configuration (Spring Cloud Config + Git)
- Declarative HTTP service clients with fallback implementations
- OpenAPI / Swagger documentation per service

**Distributed systems**
- **Resilience4j**: circuit breaker (accounts route), retry with exponential backoff (loans route), rate limiter, time limiter
- **Redis-backed distributed rate limiting** at the edge (cards route)
- **Event-driven, asynchronous communication** with Kafka + Spring Cloud Stream (accounts → message → accounts)
- **Service discovery** without Eureka: Spring Cloud Kubernetes reads the Kubernetes API directly
- **Distributed tracing** with OpenTelemetry, trace/span ids in every log line and a trace-id response header

**Security**
- OAuth2 resource server at the gateway, JWTs issued by Keycloak
- Keycloak realm roles mapped to Spring authorities (`ACCOUNTS`, `CARDS`, `LOANS`)
- Secrets injected into pods through Kubernetes `Secret` objects, never baked into images

**Containerization**
- Distroless-style images built **without a Dockerfile** using Google **Jib**
- Parallel build/push scripts for Bash and Windows

**Kubernetes & Helm**
- Umbrella chart + per-service subcharts + a **library chart** of shared templates (DRY)
- `global` values propagated to every subchart; per-environment values files
- Startup, readiness, and liveness probes on Spring Boot Actuator health groups
- CPU/memory requests and limits on every workload
- ConfigMap-driven configuration, Secret-driven credentials
- ServiceAccount + Role + RoleBinding (least-privilege RBAC) for the discovery server
- Namespace-per-environment isolation (`kpgfinance`, `kpgfinance-qa`, `kpgfinance-prod`)

---

## Repository layout

```
.
├── accounts/            # Spring Boot service
├── loans/               # Spring Boot service
├── cards/               # Spring Boot service
├── message/             # Spring Cloud Function / Stream consumer
├── gatewayserver/       # Spring Cloud Gateway (edge server)
├── configserver/        # Spring Cloud Config Server
├── build-scripts/       # Build & push all images with Jib (sh + cmd)
├── grafana/             # Grafana dashboards
└── helm-kpgfinance/
    ├── kpgfinance-common/       # Library chart: shared Deployment/Service/ConfigMap/label templates
    ├── kpgfinance-services/     # One application chart per microservice
    │   ├── accounts/  loans/  cards/  message/
    │   ├── gatewayserver/  configserver/
    │   └── discoveryserver/     # includes rbac.yaml
    └── kpgfinance-platform/     # Umbrella chart: infra + all services in ONE release
        ├── templates/           # namespace, secrets, configmap, redis, kafka, keycloak, grafana-lgtm
        ├── values.yaml          # default environment
        └── values/
            ├── values-qa.yaml
            └── values-prod.yaml
```

---

## Kubernetes deployment (Helm)

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Kubernetes cluster | 1.29+ | Docker Desktop Kubernetes, minikube, kind, or any cloud cluster (EKS/GKE/AKS) |
| `kubectl` | matching your cluster | `kubectl cluster-info` should succeed |
| Helm | 3.x or 4.x | `helm version` |
| Resources | ~6 CPU / 8 GB RAM free | Kafka, Keycloak, and Grafana LGTM are the heaviest pods |

> The default service images are published as `anselfernandes7/<service>:s17` on Docker Hub, so you can deploy without building anything. To use your own images, see [Building the container images](#building-the-container-images).

### 1. Create the secrets

The platform chart reads the Keycloak admin credentials from its values (`secrets.*`). The defaults (`admin`/`admin`) are **demo values only**. Override them at install time instead of editing `values.yaml`:

```bash
export KC_ADMIN_PASSWORD='choose-a-strong-password'
```

The config server needs its encryption key (for `{cipher}` values) and, if the config Git repo is private, a read-only Git token. Create these as a Kubernetes Secret in the target namespace:

```bash
kubectl create namespace kpgfinance

kubectl -n kpgfinance create secret generic configserver-secrets \
  --from-literal=ENCRYPT_KEY='<your-encrypt-key>' \
  --from-literal=CONFIG_GIT_USERNAME='<github-username>' \
  --from-literal=CONFIG_GIT_TOKEN='<fine-grained-read-only-token>'
```

Spring Boot maps these environment variables straight onto `encrypt.key` and `spring.cloud.config.server.git.*` (see `configserver/src/main/resources/application.yml`). If the config repo is public, leave out the two Git entries.

> If you created the namespace yourself as shown above, install with `--set namespace.create=false` so the chart doesn't try to create it again.

### 2. Build the chart dependencies

The umbrella chart pulls the library chart and every service chart in as local `file://` dependencies. Package them once, starting from the leaves:

```bash
cd helm-kpgfinance

# each service chart depends on the library chart
for svc in kpgfinance-services/*/ ; do
  helm dependency build "$svc"
done

# the umbrella chart depends on everything
helm dependency build kpgfinance-platform
```

<details>
<summary>Windows PowerShell equivalent</summary>

```powershell
cd helm-kpgfinance
Get-ChildItem kpgfinance-services -Directory | ForEach-Object { helm dependency build $_.FullName }
helm dependency build kpgfinance-platform
```
</details>

### 3. Install the platform

One command installs the namespace, ConfigMap, Secrets, Redis, Kafka, Keycloak, Grafana LGTM, the discovery server, the config server, and all microservices:

```bash
helm install kpgfinance ./kpgfinance-platform \
  --namespace kpgfinance \
  --set namespace.create=false \
  --set secrets.keycloakAdminPassword="$KC_ADMIN_PASSWORD"
```

Preview the rendered manifests without installing:

```bash
helm template kpgfinance ./kpgfinance-platform | less
helm install kpgfinance ./kpgfinance-platform --dry-run --debug -n kpgfinance
```

### 4. Verify the rollout

```bash
kubectl get pods -n kpgfinance -w
kubectl get deploy,svc,cm,secret -n kpgfinance
helm status kpgfinance -n kpgfinance
```

Spring Boot services can take 1–3 minutes to pass their **startup probes** on a laptop-sized cluster. The config server must be ready before the business services finish booting, and the probes handle that ordering for you: pods simply stay `0/1 Ready` until their dependencies answer.

```bash
kubectl rollout status deploy/accounts-deployment -n kpgfinance
kubectl logs deploy/gatewayserver-deployment -n kpgfinance -f
```

### 5. Access the services

`gatewayserver`, `keycloak`, and `grafana-lgtm` are exposed as `LoadBalancer` Services. On Docker Desktop they're reachable on `localhost`. On minikube, run `minikube tunnel`. On a cloud cluster, use the external IP from `kubectl get svc`.

| What | URL |
|---|---|
| API gateway | http://localhost:8072 |
| Keycloak admin console | http://localhost:7080 |
| Grafana | http://localhost:3000 |

Without a LoadBalancer, use port-forwarding:

```bash
kubectl -n kpgfinance port-forward svc/gatewayserver 8072:8072
kubectl -n kpgfinance port-forward svc/keycloak 7080:7080
kubectl -n kpgfinance port-forward svc/grafana-lgtm 3000:3000
```

### Multiple environments (default / QA / prod)

Each environment is a separate Helm release in its own namespace. Its values file switches `SPRING_PROFILES_ACTIVE`, so the config server serves `accounts-qa.yml`, `accounts-prod.yml`, and so on.

```bash
# QA
helm install kpgfinance-qa ./kpgfinance-platform \
  -f kpgfinance-platform/values/values-qa.yaml \
  -n kpgfinance-qa --create-namespace \
  --set secrets.keycloakAdminPassword="$KC_ADMIN_PASSWORD"

# Prod
helm install kpgfinance-prod ./kpgfinance-platform \
  -f kpgfinance-platform/values/values-prod.yaml \
  -n kpgfinance-prod --create-namespace \
  --set secrets.keycloakAdminPassword="$KC_ADMIN_PASSWORD"
```

Per-service overrides are nested under the subchart name, for example scaling accounts in prod:

```yaml
# values-prod.yaml
accounts:
  replicaCount: 3
```

> **Helm 3 note:** with `--create-namespace`, also pass `--set namespace.create=false`. Otherwise Helm 3 fails with "namespace already exists". Helm 4 handles this case.

### Upgrade, rollback, uninstall

```bash
# change a value / roll out a new image tag
helm upgrade kpgfinance ./kpgfinance-platform -n kpgfinance \
  --reuse-values --set accounts.image.tag=s18

helm history  kpgfinance -n kpgfinance
helm rollback kpgfinance 1 -n kpgfinance

helm uninstall kpgfinance -n kpgfinance
```

---

## Helm chart design

```
kpgfinance-platform (umbrella, type: application)
 ├── templates/        ← infra owned by the platform: namespace, Secret, ConfigMap,
 │                       Redis, Kafka (KRaft), Keycloak, Grafana LGTM
 ├── kpgfinance-common (type: library)  ← _deployment.tpl, _service.tpl, _configmap.tpl, _labels.tpl
 └── kpgfinance-services/*  (one chart per microservice)
        each: values.yaml + 2-line templates that `include` the library
```

- **Library chart (`kpgfinance-common`).** A single parameterized Deployment template is shared by every service. Each service's `values.yaml` turns on only the env vars it needs through feature flags (`kafkaEnabled`, `redisEnabled`, `jwkEnabled`, `otelEnabled`, `discoveryEnabled`, ...). Adding a new service means writing a values file, with no new YAML templates.
- **`global` values.** Cluster-wide endpoints (config server URL, discovery URL, Kafka broker, Redis, Keycloak JWKS, OTLP endpoints) live once under `global:` in the umbrella chart, and Helm propagates them to every subchart automatically.
- **Fail-fast rendering.** The library templates call `fail` with a clear message if a service chart is installed standalone without the `global` block, instead of rendering a broken manifest.
- **Configuration vs. secrets.** Non-sensitive config goes into a `ConfigMap` consumed through `configMapKeyRef`. Credentials go into a `Secret` consumed through `secretKeyRef`.

---

## Kubernetes features used

| Feature | Where |
|---|---|
| Deployments + ClusterIP / LoadBalancer Services | every workload |
| Startup / readiness / liveness probes | Actuator `/actuator/health/{readiness,liveness}`, Keycloak `/health/*` |
| Resource requests & limits | every container, tunable per environment |
| ConfigMap → env vars | `kpgfinance-common/templates/_configmap.tpl` |
| Secret → env vars | `kpgfinance-platform/templates/01-secrets.yaml` |
| ServiceAccount, Role, RoleBinding | `kpgfinance-services/discoveryserver/templates/rbac.yaml` |
| Kubernetes-native service discovery | Spring Cloud Kubernetes discovery server + discovery client |
| Namespace isolation | one namespace per environment |
| Recommended labels (`app.kubernetes.io/*`) | `kpgfinance-common/templates/_labels.tpl` |

---

## Calling the APIs

All traffic goes through the gateway at `/kpgfinance/{accounts|loans|cards}/api/...`.

- **GET** requests are public.
- **POST / PUT / DELETE** require a JWT carrying the matching role: `ACCOUNTS`, `LOANS`, or `CARDS`.

**One-time Keycloak setup** (http://localhost:7080, admin console):
1. Create a client, e.g. `kpgfinance-callcenter-cc`, with **Client authentication ON** and only the **Service accounts roles** flow enabled.
2. Create realm roles `ACCOUNTS`, `LOANS`, `CARDS` and assign them to the client's service-account user.
3. Copy the client secret from the **Credentials** tab.

```bash
# 1. get a token (client-credentials grant)
TOKEN=$(curl -s -X POST \
  http://localhost:7080/realms/master/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=kpgfinance-callcenter-cc \
  -d client_secret="$CLIENT_SECRET" \
  -d scope="openid email profile" | jq -r .access_token)

# 2. create an account
curl -X POST http://localhost:8072/kpgfinance/accounts/api/create \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Jane Doe","email":"jane@example.com","mobileNumber":"1234567890"}'

# 3. create a loan and a card for that customer
curl -X POST "http://localhost:8072/kpgfinance/loans/api/create?mobileNumber=1234567890" -H "Authorization: Bearer $TOKEN"
curl -X POST "http://localhost:8072/kpgfinance/cards/api/create?mobileNumber=1234567890" -H "Authorization: Bearer $TOKEN"

# 4. aggregated read across 3 services (public GET)
curl "http://localhost:8072/kpgfinance/accounts/api/fetchCustomerDetails?mobileNumber=1234567890"
```

Every response includes a trace-id header. Paste the id into Grafana → Explore → Tempo to see the full cross-service trace.

---

## Observability

Every service exports **logs, metrics, and traces over OTLP** to the `grafana-lgtm` pod (Loki + Mimir/Prometheus + Tempo behind one Grafana instance).

- **Traces.** Gateway → accounts → loans/cards, plus the Kafka hops. Sampling is 100% in this demo.
- **Logs.** Every line carries `traceId` / `spanId`, so you can jump from a log line to its trace and back.
- **Metrics.** Micrometer → OTLP, tagged with `application`.
- **Dashboards.** Import the JSON files in [`grafana/dashboards`](grafana/dashboards).

---

## Building the container images

Images are built with **Jib**, so there's no Dockerfile and no local Docker build context. Jib produces layered, reproducible images. The target image is set in each `pom.xml` (`<to><image>`).

```bash
# build all images into the local Docker daemon
./build-scripts/build-images.sh --parallel

# build and push to Docker Hub (run `docker login` first)
./build-scripts/build-images.sh --parallel --push

# Windows
build-scripts\build-images.cmd -p --push
```

To use your own registry, change `<image>` in each `pom.xml` and `image.repository` in each `helm-kpgfinance/kpgfinance-services/<svc>/values.yaml`, or override it at install time:

```bash
helm install kpgfinance ./kpgfinance-platform -n kpgfinance \
  --set accounts.image.repository=<you>/accounts
```

See [`build-scripts/README.md`](build-scripts/README.md) for all options.

---

## Secrets management

No credentials are committed to this repository. All secrets are injected at runtime:

| Secret | How it is supplied |
|---|---|
| Keycloak admin password | `--set secrets.keycloakAdminPassword=...` → Kubernetes `Secret` |
| Config server encryption key | `ENCRYPT_KEY` env var from a Kubernetes `Secret` |
| Config Git repo token (if private) | `CONFIG_GIT_USERNAME` / `CONFIG_GIT_TOKEN` env vars from a Kubernetes `Secret` |
| OAuth2 client secret | Generated in Keycloak, never stored in the repo |

For a real deployment, replace plain `Secret` objects with one of these:
- **[Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)**: encrypt secrets so the encrypted YAML can be committed safely
- **[External Secrets Operator](https://external-secrets.io/)**: sync from AWS Secrets Manager / GCP Secret Manager / Azure Key Vault / HashiCorp Vault
- **[SOPS](https://github.com/getsops/sops) + helm-secrets**: encrypted values files

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Pods stuck `0/1 Running` for minutes | Normal while the JVM warms up. If they never become ready: `kubectl describe pod <pod>` and check the startup probe events, or raise `startupProbe.failureThreshold` |
| `OOMKilled` | Increase `resources.limits.memory` for that service in the values file |
| Services can't find each other | Check the discovery server pod and its RBAC: `kubectl auth can-i list endpoints --as=system:serviceaccount:kpgfinance:spring-cloud-kubernetes-discoveryserver -n kpgfinance` |
| Config server fails to start | Check `ENCRYPT_KEY` and the Git credentials in the `configserver-secrets` Secret, and confirm the config repo URL is reachable from the cluster |
| `401` / `403` from the gateway | Token missing or expired, or the client lacks the `ACCOUNTS` / `LOANS` / `CARDS` role |
| `429 Too Many Requests` on cards | The Redis rate limiter is working as intended |
| `namespace already exists` on install | Add `--set namespace.create=false` |

---

## Author

**Ansel Fernandes**: [GitHub](https://github.com/anselfernandes15) · [Docker Hub](https://hub.docker.com/u/anselfernandes7)
