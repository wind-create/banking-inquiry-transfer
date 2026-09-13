# Banking Inquiry & Transfer

Secure banking backend berbasis **Java 21 + Spring Boot 4 + Kubernetes** untuk kebutuhan account inquiry, balance inquiry, fund transfer, transaction history, OAuth2 authorization, rate limiting, gRPC inter-service communication, Redis cache, serta audit trail berbasis PostgreSQL CDC.

---

url video :  https://drive.google.com/file/d/1PHY3YhKFuZTpTbvZBd7IS0lhD9YBgqyo/view?usp=sharing

untuk load file docker image pakai command 
```text

docker load `
  -i .\docker-images\banking-services-1.0.0.tar

```
lalu 

```text

docker images | Select-String "banking-"

```

## 1. Fitur Utama

- Inquiry account berdasarkan `customerId`
- Inquiry balance berdasarkan `accountNumber`
- Fund transfer antar customer account
- Inquiry transaction history berdasarkan account
- OAuth2 Authorization Server
- JWT Resource Server
- Scope-based authorization
- Customer/account ownership validation
- API Gateway sebagai single entry point
- Rate limit per customer: **5 request / 5 menit**
- Redis-based sliding-window rate limiting
- Transaction history cache dengan TTL **1 menit**
- gRPC untuk komunikasi antar service
- Idempotency pada fund transfer
- Correlation ID end-to-end
- PostgreSQL logical replication / CDC
- Debezium PostgreSQL Connector
- Kafka event pipeline
- Audit trail terpisah pada `audit_db`
- Kubernetes deployment
- Health, readiness, dan liveness probes

---

## 2. Arsitektur

```text
                                   +----------------------+
                                   |     Auth Service     |
                                   |        :8081         |
                                   | OAuth2 / JWT / JWK   |
                                   +----------+-----------+
                                              |
                                              | JWK
                                              v
+----------------+                  +----------------------+
| Client/Postman | ---------------->|     API Gateway      |
+----------------+                  |        :8080         |
                                    +----------+-----------+
                                               |
                         +---------------------+---------------------+
                         |                                           |
                         v                                           v
             +----------------------+                    +----------------------+
             |   Account Service    |<------ gRPC ------| Transaction Service  |
             | REST :8082           |       :9092       | REST :8083           |
             | gRPC :9092           |                   +----------+-----------+
             +----------+-----------+                              |
                        |                                          |
                        v                                          v
                  +-----------+                              +---------------+
                  |account_db |                              |transaction_db |
                  +-----+-----+                              +-------+-------+
                        |                                            |
                        +---------------- WAL / CDC ------------------+
                                             |
                                             v
                                      +-------------+
                                      |  Debezium   |
                                      +------+------+ 
                                             |
                                             v
                                         +-------+
                                         | Kafka |
                                         +---+---+
                                             |
                                             v
                                      +-------------+
                                      |Audit Service|
                                      |    :8085    |
                                      +------+------+ 
                                             |
                                             v
                                        +----------+
                                        | audit_db |
                                        +----------+

Redis:
- API Gateway rate limiting
- Transaction history cache
```

---

## 3. Service

| Service | Port | Tanggung Jawab |
|---|---:|---|
| API Gateway | 8080 | Single entry point, JWT validation, routing, rate limiting, correlation ID |
| Auth Service | 8081 | OAuth2 Authorization Server, access token, JWK |
| Account Service | 8082 | Customer/account inquiry dan balance |
| Account Service gRPC | 9092 | Ownership validation dan atomic transfer posting |
| Transaction Service | 8083 | Transfer orchestration dan transaction history |
| Audit Service | 8085 | Kafka CDC consumer dan persistence audit trail |
| PostgreSQL | 5432 internal | Persistence |
| Redis | 6379 | Rate limiting dan cache |
| Kafka | 9092 | CDC event transport |
| Debezium Connect | 8083 internal | PostgreSQL CDC connector |

---

## 4. Technology Stack

### Backend

- Java 21
- Spring Boot 4.0.x
- Spring Security
- Spring Authorization Server
- Spring Cloud Gateway
- Spring Data JPA / JDBC
- Spring Data Redis Reactive
- Spring Kafka
- Flyway
- PostgreSQL 17
- Redis 7.4
- gRPC Java
- Protocol Buffers
- Debezium PostgreSQL Connector
- Apache Kafka
- Maven

### Infrastructure

- Docker
- Docker Desktop Kubernetes
- Kubernetes
- StatefulSet
- Deployment
- Service
- ConfigMap
- Secret
- PersistentVolumeClaim

### Testing

- Postman
- PowerShell
- kubectl
- psql
- redis-cli
- Kafka CLI

---

## 5. Struktur Project

```text
banking-inquiry-transfer/
|
+-- pom.xml
+-- Dockerfile.service
+-- docker-compose.yml
|
+-- common-proto/
|
+-- api-gateway/
|
+-- auth-service/
|
+-- account-service/
|
+-- transaction-service/
|
+-- audit-service/
|
+-- k8s/
|   +-- namespace.yaml
|   +-- configmap.yaml
|   |
|   +-- postgres/
|   +-- redis/
|   +-- kafka/
|   +-- debezium/
|   +-- auth-service/
|   +-- account-service/
|   +-- transaction-service/
|   +-- audit-service/
|   +-- api-gateway/
|
+-- postman/
|
+-- local-secrets/
|
+-- README.md
```

`local-secrets/` tidak boleh di-commit ke Git.

---

## 6. Database

Project menggunakan empat logical database PostgreSQL:

```text
auth_db
account_db
transaction_db
audit_db
```

### 6.1 auth_db

Digunakan Auth Service.

Tabel utama meliputi:

```text
oauth2_registered_client
oauth2_authorization
oauth2_authorization_consent
```

Client Postman yang digunakan:

```text
client_id: banking-postman
```

Scopes:

```text
account.read
transaction.read
transfer.write
```

---

### 6.2 account_db

Tabel:

```text
customers
accounts
account_transfers
flyway_schema_history
```

Relasi utama:

```text
customers
    1
    |
    | has many
    v
accounts
```

`account_transfers` menyimpan hasil posting transfer pada Account Service.

Migration yang telah diterapkan:

```text
V1 - create customer and account tables
V2 - rename and add audit columns
V3 - seed demo accounts
V4 - create account transfers
V5 - add correlation id to account transfers
```

Demo data:

```text
CUST001
  - 1000012345
  - 1000012346

CUST002
  - 2000012345
```

---

### 6.3 transaction_db

Tabel:

```text
transfer_requests
flyway_schema_history
```

Migration:

```text
V1 - create transfer requests
V2 - extend transfer request result
V3 - add correlation id to transfer requests
```

`transfer_requests` digunakan untuk:

- transfer orchestration
- idempotency
- transfer status
- correlation tracing
- transfer result persistence

---

### 6.4 audit_db

Tabel:

```text
audit_events
flyway_schema_history
```

Audit Service menerima CDC events dari Kafka dan menyimpannya ke `audit_events`.

Contoh event:

```text
transaction-service | transfer_requests | CREATE
account-service     | account_transfers | CREATE
account-service     | accounts          | UPDATE
account-service     | accounts          | UPDATE
transaction-service | transfer_requests | UPDATE
```

---

## 7. OAuth2 dan Security

### Authorization Flow

Project menggunakan:

```text
OAuth2 Authorization Code + PKCE
```

Untuk Postman:

```text
Authorization URL:
http://localhost:8081/oauth2/authorize

Token URL:
http://localhost:8081/oauth2/token

Callback URL:
https://oauth.pstmn.io/v1/browser-callback

Client ID:
banking-postman
```

Full scopes:

```text
account.read transaction.read transfer.write
```

JWT subject merepresentasikan customer:

```text
sub = CUST001
```

### Scope Mapping

| Scope | Akses |
|---|---|
| `account.read` | Account dan balance inquiry |
| `transaction.read` | Transaction history |
| `transfer.write` | Fund transfer |

JWT valid saja tidak cukup. Service juga melakukan ownership validation agar customer hanya dapat mengakses account miliknya sendiri.

Contoh:

```text
CUST001 token -> CUST001 account = allowed
CUST001 token -> CUST002 account = 403 Forbidden
```

---

## 8. API Gateway

Semua business API diakses melalui:

```text
http://localhost:8080
```

Downstream routing:

```text
/api/v1/accounts/*/transactions
    -> transaction-service:8083

/api/v1/transfers/**
    -> transaction-service:8083

/api/v1/customers/**
    -> account-service:8082

/api/v1/accounts/**
    -> account-service:8082
```

Gateway bertanggung jawab terhadap:

- JWT verification
- route forwarding
- rate limit
- correlation ID
- security entry point

---

## 9. API Specification

### 9.1 Inquiry Customer Accounts

```http
GET /api/v1/customers/{customerId}/accounts
```

Scope:

```text
account.read
```

Contoh:

```http
GET /api/v1/customers/CUST001/accounts
Authorization: Bearer <access_token>
```

---

### 9.2 Inquiry Account Balance

```http
GET /api/v1/accounts/{accountNumber}/balance
```

Scope:

```text
account.read
```

Contoh:

```http
GET /api/v1/accounts/1000012345/balance
```

---

### 9.3 Fund Transfer

```http
POST /api/v1/transfers
```

Scope:

```text
transfer.write
```

Headers:

```http
Authorization: Bearer <access_token>
Content-Type: application/json
Idempotency-Key: <unique-key>
X-Correlation-Id: <correlation-id>
```

Body:

```json
{
  "sourceAccountNumber": "1000012345",
  "destinationAccountNumber": "2000012345",
  "amount": "50000",
  "currency": "IDR"
}
```

Contoh response:

```json
{
  "transferId": "uuid",
  "status": "SUCCESS",
  "sourceAccountNumber": "1000012345",
  "destinationAccountNumber": "2000012345",
  "amount": 50000.00,
  "currency": "IDR",
  "sourceBalanceAfter": 14850000.00,
  "destinationBalanceAfter": 8150000.00,
  "processedOn": "2026-09-13T09:49:18Z",
  "replayed": false
}
```

Jika request dikirim ulang dengan `Idempotency-Key` yang sama:

```json
{
  "status": "SUCCESS",
  "replayed": true
}
```

Saldo tidak dipotong dua kali.

---

### 9.4 Transaction History

```http
GET /api/v1/accounts/{accountNumber}/transactions
```

Scope:

```text
transaction.read
```

Contoh:

```http
GET /api/v1/accounts/1000012345/transactions
```

Contoh response:

```json
{
  "accountNumber": "1000012345",
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "transactions": [
    {
      "transactionId": "uuid",
      "direction": "DEBIT",
      "counterpartyAccountNumber": "2000012345",
      "amount": 100000.00,
      "currency": "IDR",
      "processedOn": "2026-09-13T09:49:18Z"
    }
  ]
}
```

---

## 10. Rate Limiting

Requirement:

```text
5 requests / 5 minutes / customer
```

Implementasi menggunakan Redis Sorted Set + Lua script sehingga limit bersifat atomic.

Redis key:

```text
rate-limit:customer:<customerId>
```

Contoh:

```text
rate-limit:customer:CUST001
```

Expected:

```text
Request 1 -> 200
Request 2 -> 200
Request 3 -> 200
Request 4 -> 200
Request 5 -> 200
Request 6 -> 429 Too Many Requests
```

Response headers:

```text
X-RateLimit-Limit
X-RateLimit-Remaining
```

Jika Redis tidak tersedia, rate limiter menggunakan pendekatan fail-closed.

---

## 11. Transaction History Cache

Transaction history menggunakan Redis cache.

TTL:

```text
1 minute
```

Konfigurasi:

```properties
app.cache.transaction-history.ttl=1m
```

Flow:

```text
GET transaction history
        |
        v
Ownership Validation via gRPC
        |
        v
Check Redis
   |         |
 HIT        MISS
   |         |
   |         v
   |     Query DB
   |         |
   |         v
   +----- Save Redis
             |
             v
          Response
```

---

## 12. gRPC

Inter-service communication antara Transaction Service dan Account Service menggunakan gRPC.

Account Service gRPC endpoint:

```text
account-service:9092
```

Fungsi utama:

```text
GetAccount
ValidateOwnership
ExecuteTransfer
```

`ExecuteTransfer` melakukan posting transfer atomik dengan pessimistic database locking.

---

## 13. Idempotency

Request transfer harus membawa:

```http
Idempotency-Key: <unique-value>
```

Jika client retry menggunakan key yang sama:

```text
request pertama -> transfer diproses
request kedua   -> response lama direplay
```

Tidak terjadi double debit.

---

## 14. Correlation ID

Header:

```http
X-Correlation-Id
```

digunakan untuk tracing request melalui:

```text
API Gateway
  -> Transaction Service
  -> gRPC
  -> Account Service
  -> PostgreSQL
  -> Debezium
  -> Kafka
  -> Audit Service
```

Contoh:

```text
X-Correlation-Id: postman-transfer-1720000000000
```

Correlation ID disimpan pada:

```text
transaction_db.transfer_requests
account_db.account_transfers
audit_db.audit_events
```

---

## 15. CDC dan Audit Trail

PostgreSQL dikonfigurasi dengan:

```text
wal_level=logical
```

Debezium connectors:

```text
account-db-connector
transaction-db-connector
```

Replication slots:

```text
banking_account_slot
banking_transaction_slot
```

Publications:

```text
banking_account_publication
banking_transaction_publication
```

Kafka CDC topics:

```text
banking.account.public.accounts
banking.account.public.account_transfers
banking.transaction.public.transfer_requests
```

Audit Service subscribe ke tiga topic tersebut dan menulis event ke:

```text
audit_db.audit_events
```

---

## 16. Kubernetes

### Prerequisites

Pastikan tersedia:

```text
Java 21
Maven 3.9+
Docker Desktop
Docker Desktop Kubernetes
kubectl
Postman
```

Cek cluster:

```powershell
kubectl config current-context
kubectl get nodes
```

Expected context:

```text
docker-desktop
```

### Namespace

```powershell
kubectl apply -f .\k8s\namespace.yaml
```

### Infrastructure

Deploy:

```text
PostgreSQL
Redis
Kafka
Debezium
```

Verifikasi:

```powershell
kubectl get pods -n banking
```

### Application Images

Build dengan reusable Dockerfile:

```powershell
docker build --no-cache `
  -f Dockerfile.service `
  --build-arg MODULE=auth-service `
  -t banking-auth-service:1.0.0 `
  .
```

Lakukan pola yang sama untuk:

```text
account-service
transaction-service
audit-service
api-gateway
```

### Deploy Services

Contoh:

```powershell
kubectl apply `
  -f .\k8s\auth-service\service.yaml `
  -f .\k8s\auth-service\deployment.yaml
```

Lanjutkan untuk seluruh service.

Target:

```text
api-gateway           1/1 Running
auth-service          1/1 Running
account-service       1/1 Running
transaction-service   1/1 Running
audit-service         1/1 Running
debezium              1/1 Running
kafka-0               1/1 Running
postgres-0            1/1 Running
redis-0               1/1 Running
```

---

## 17. Local Access

Untuk demo lokal:

### Auth Service

```powershell
kubectl port-forward `
  -n banking `
  service/auth-service `
  8081:8081
```

### API Gateway

```powershell
kubectl port-forward `
  -n banking `
  service/api-gateway `
  8080:8080
```

Business API:

```text
http://localhost:8080
```

OAuth2:

```text
http://localhost:8081
```

---

## 18. Demo Postman

Urutan demo yang direkomendasikan:

```text
1. Kubernetes pods -> seluruhnya Running
2. Gateway health -> 200 / UP
3. Protected API tanpa token -> 401
4. OAuth2 Authorization Code + PKCE
5. Account inquiry -> 200
6. Balance inquiry -> 200
7. Transfer -> SUCCESS
8. Idempotency replay -> replayed=true
9. Transaction history -> transaksi muncul
10. CUST001 mengakses CUST002 -> 403
11. Scope tidak mencukupi -> 403
12. Audit CDC -> event muncul di audit_db
13. Rate limit -> request ke-6 = 429
```


kubectl exec `
  -n banking `
  postgres-0 `
  -- psql `
  -U banking_admin `
  -d audit_db `
  -c "SELECT source_service, table_name, operation, record_id, correlation_id, occurred_at FROM audit_events WHERE correlation_id = 'postman-flow-001' ORDER BY occurred_at;"
