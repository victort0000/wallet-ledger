# Wallet Ledger Backend Service

A production-grade, concurrency-safe Wallet Ledger backend service built with **Java 17**, **Spring Boot 3.3.5**, and **PostgreSQL**. Designed for high throughput, double-spend prevention, idempotent execution, and full financial auditability.

## 1. Project Overview & Capabilities

### Core Features

-   **Wallet Operations**: Credit, debit, balance inquiry, and paginated transaction history retrieval.
    
-   **Overdraft Protection**: Automatic rejection of debit operations when available funds are insufficient.
    
-   **Append-Only Immutable Ledger**: Every balance mutation produces a permanent, non-updatable record tagged with transaction reasons (`MISSION_REWARD`, `PURCHASE`, `ADMIN_ADJUSTMENT`, `PLAYER_TRANSFER`, etc.).
    
-   **Concurrency & Double-Spend Safety**: Pessimistic database locking (`SELECT FOR UPDATE`) combined with database check constraints prevents race conditions and over-spending.
    
-   **Strict Idempotency**: Prevents double-processing of duplicate incoming HTTP requests using unique `Idempotency-Key` headers.
    
-   **Automated Testing Suite**: Includes multi-threaded concurrency tests verifying zero overdrafts under heavy parallel load.
    

### Extended / Bonus Capabilities

-   **Player Currency Transfer**: Atomic player-to-player funds transfer with deterministic lock ordering to prevent database deadlocks.
    
-   **Auto-Provisioned Wallets**: Player registration automatically provisions a default `0.0000` wallet within a single atomic database transaction.
    
-   **OpenAPI / Swagger UI Documentation**: Interactive API documentation generated dynamically via Springdoc OpenAPI 2.8.5.
    

## 2. Technology Stack

-   **Language & JDK**: Java 17 (Eclipse Temurin)
    
-   **Framework**: Spring Boot 3.3.5 / Spring Web / Spring Data JPA
    
-   **Database**: PostgreSQL 15 (Docker) / H2 (In-memory testing)
    
-   **Migrations & Schema**: Spring SQL Initialization (`schema.sql`) / Flyway
    
-   **Documentation**: Springdoc OpenAPI (`springdoc-openapi-starter-webmvc-ui:2.8.5`)
    
-   **Build System**: Apache Maven (`./mvnw`)
    
-   **Containerization**: Docker & Docker Compose
    

## 3. How to Run — Project Setup & Database

### 3.1 Prerequisites

Ensure you have the following tools installed locally:

-   **JDK 17 or 21**: Verify with `java -version`
    
-   **Docker & Docker Compose**: Verify with `docker --version` and `docker compose version`
    
-   **Git**: Verify with `git --version`
    

### 3.2 Database Setup (PostgreSQL)

The application includes a `docker-compose.yml` file configured for PostgreSQL 15.

1.  **Start PostgreSQL Container**:
    
    ```
    docker compose up -d
    
    
    ```
    
_(Or `docker-compose up -d` on older Docker Compose versions)_

2.  **Verify Container Status**:
    

Bash

```
   docker ps
   

```

You should see `wallet_postgres` running on port `5432`.

3.  **Database Credentials** (Default in `application.yml`):
    
    -   **Host**: `localhost:5432`
        
    -   **Database**: `wallet_db`
        
    -   **Username**: `wallet_user`
        
    -   **Password**: `wallet_password`
        

### 3.3 Building & Running the Application

1.  **Clone the Repository**:
    

Bash

```
   git clone https://github.com/victort0000/wallet-ledger.git
   cd wallet-ledger
```

2.  **Build the Application**:
    

Bash

```
   ./mvnw clean package -DskipTests
```

3.  **Run Spring Boot Application**:
    

Bash

```
   ./mvnw spring-boot:run
```

4.  **Verify Application Health & Interactive OpenAPI UI**:
    
    -   **API Base URL**: `http://localhost:8080/api/v1`
        
    -   **Swagger UI Documentation**: `http://localhost:8080/swagger-ui.html`
        
    -   **OpenAPI Specs**: `http://localhost:8080/v3/api-docs`
        

### 3.4 Executing the Test Suite

The project features a comprehensive unit, integration, and multi-threaded concurrency test suite.

1.  **Execute All Tests**:
    

Bash

```
   ./mvnw clean test
```

2.  **Execute Only the Concurrency Safety Test**:
    

Bash

```
   ./mvnw test -Dtest=WalletConcurrencyTest
```

3.  **Execute Only the Mandatory Test**:
    

Bash

```
   ./mvnw test -Dtest=WalletMandatoryTest
   
```
4.  **Execute Only the Idempotency Test**:
    

Bash

```
   ./mvnw test -Dtest=WalletIdempotencyTest
   
```

## 4. Design Decisions & Architectural Trade-offs

Building a production-grade financial ledger requires making conscious trade-offs between speed, consistency, strict safety, and operational complexity. Below is the architectural rationale behind key design choices.

### 4.1 Chosen Ledger Approach: Dual-State Hybrid Model

_(Append-Only Immutable Ledger + Materialized Balance Snapshot)_

The architecture combines two complementary financial patterns:

1.  **Append-Only Immutable Ledger (`wallet_transactions`)**: Every credit, debit, or transfer appends a permanent, non-modifiable entry. Rows are never edited (`UPDATE`) or deleted (`DELETE`), preserving a complete audit trail.
    
2.  **Aggregated Snapshot Balance (`wallets.balance`)**: A cached, atomic snapshot column on the parent wallet row holds the current real-time balance.
    

#### Alternatives Considered:

-   **Pure Event-Sourced / Ledger-Only Model (No Snapshot Balance)**:
    
    -   _How it works_: Calculate balance dynamically on every query using `SELECT SUM(amount) FROM wallet_transactions WHERE wallet_id = :id`.
        
    -   _Why rejected_: Degrades to $O(N)$ query time as historical transactions grow into millions of rows. It also complicates strict $O(1)$ overdraft checking during high-frequency debits.
### 4.2 Architectural Trade-offs Matrix
| Design Choice | Primary Advantages | Main Trade-offs / Limitations | Mitigation Strategy |
|------|------|-------------|-------------|
| Pessimistic Locking (SELECT FOR UPDATE) | Guarantees strict linearizability; eliminates lost updates and race conditions during debits. | Limits throughput for a single wallet to sequential execution speeds. | Scales horizontally across different wallets; lock holding time is kept strictly short ($< 5\text{ms}$) within transactions. |
| Dual-State Hybrid (Snapshot + Ledger) | Provides $O(1)$ fast balance lookups and instant debit checks while retaining full audit logs. | Risk of data divergence between snapshot balance and historical transaction sum if logic breaks.| Real-time /audit endpoint comparing snapshot balance against SUM(ledger.amount) for automated detection. |
| Database Check Constraints | Unbreakable last line of defense against negative balances at the DB storage layer. | Relies on relational DB engine capabilities; tight coupling to SQL DB engine semantics. | Standardized ANSI SQL CHECK syntax used; unit and integration tests enforce application-level guards. |
| Deterministic Lock Ordering (Transfers) | Prevents database deadlocks when two users simultaneously send money to each other. | Adds minor logic overhead to compare player IDs before acquiring locks. | Lock ordering sorted by playerId lexicographically (fromId.compareTo(toId)) before acquiring locks. |

## 5. Concurrency & Idempotency
### 5.1 Concurrency & Double-Spend Prevention Strategy

_(Pessimistic Locking `SELECT FOR UPDATE` + DB Constraints)_

To guarantee zero negative balances under intense parallel traffic (e.g., flash sales or rapid API retries), the service enforces double-spend prevention at two layers:

1.  **DB-Level Pessimistic Row Locking (`SELECT FOR UPDATE`)**:
    
    -   When processing a credit, debit, or transfer, the application explicitly requests a row-level write lock via JPA/SQL.
        
    -   Concurrent requests attempting to touch the same wallet are serialized at the database level, ensuring isolated read-modify-write execution.
        
2.  **Database Check Constraint Enforcement**:
    
    -   PostgreSQL enforces `CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)`.
        
    -   Even if application-level checks were bypassed or bugged, the database engine aborts any transaction violating non-negativity.
        

#### Alternatives & Trade-offs Evaluated:

-   **Optimistic Locking (`@Version` column)**:
    
    -   _Trade-off_: Performs better under low-contention scenarios, but causes high retry rates, `OptimisticLockException` storms, and dropped transactions under extreme concurrent load on a single wallet.
        
-   **Distributed Locks (e.g., Redis Redlock)**:
    
    -   _Trade-off_: Increases network latency and adds external infrastructure complexity. In-memory locks can fail during network partitions or Redis primary failovers, risking non-atomic double debits.
        

### 5.2 Strict Request Idempotency Mechanism

_(Transaction-Aware Idempotency Service + Unique DB Constraint)_

Network dropouts and automated client retries can result in duplicated financial operations. The system implements request idempotency using the `Idempotency-Key` HTTP header:

1.  Requests enter a dedicated `@Transactional(propagation = Propagation.REQUIRES_NEW)` service phase.
    
2.  An initial check inserts an `IN_PROGRESS` record into `idempotency_records`.
    
3.  Subsequent concurrent duplicate requests receive a `409 Conflict`.
    
4.  Upon successful completion of the business transaction, the record is updated to `SUCCESS` with the stored HTTP status and response payload. Future replayed requests immediately receive the cached response (`200 OK`) without re-executing business logic.
5.  
## 5. Database Schema

```
-- 1. Create Wallets Table
CREATE TABLE IF NOT EXISTS wallets
(
    id UUID PRIMARY KEY,
    player_id VARCHAR(50) NOT NULL UNIQUE,
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- 2. Create Wallet Transactions Table (Append-Only Ledger)
CREATE TABLE IF NOT EXISTS wallet_transactions
(
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL,
    balance_after NUMERIC(18, 4) NOT NULL,
    type VARCHAR(30) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Index for fast paginated ledger lookups per wallet
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_created
    ON wallet_transactions(wallet_id, created_at DESC);

-- 3. Idempotency Records Table
CREATE TABLE IF NOT EXISTS idempotency_records
(
    key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL, -- IN_PROGRESS, SUCCESS, FAILED
    response_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);


```
## 6. Testing Approach & Verification

The testing suite combines unit tests, integration tests, and multi-threaded stress tests to verify financial correctness, idempotency, and concurrency safety.

### 6.1 Overview of Test Layers

1.  **Unit Tests**: Test domain validation rules (e.g., negative amounts, invalid player IDs, insufficient funds logic) using JUnit 5 and Mockito.  
    
2.  **Integration Tests (`@SpringBootTest`)**: Test end-to-end service and REST API flows with simulated HTTP requests and database persistence.

### 6.2 Concurrent Debit Test Strategy

The primary challenge in financial ledger systems is avoiding double-spend vulnerabilities when multiple parallel requests attempt to debit the same account simultaneously.

#### Test Execution Design (`WalletConcurrencyTest.java`)

To simulate real-world race conditions, the test leverages Java's `ExecutorService` and `CountDownLatch`:

1.  **Initial Setup**: A single wallet is seeded with an exact balance of **$100.00**.
    
2.  **Parallel Spike Simulation**: 10 worker threads are spawned, each attempting to debit **$20.00** from the wallet at the exact same instant (total requested debit: **$200.00**).
    
3.  **Synchronization Barrier (`CountDownLatch`)**:
    
    -   A `startLatch` holds back all 10 threads until they are all initialized.
        
    -   `startLatch.countDown()` releases all 10 threads simultaneously to trigger maximum contention on row-level database locks.
        
    -   A `doneLatch` blocks the test thread until all 10 parallel operations complete or fail.
        

#### Verification Invariants

After all threads complete, the test asserts four non-negotiable financial invariants:
| Metric | Expected Result | Reason |
|------|------|-------------|
| `Successful Debits` | 5 | Only 5 debits of $20.00 can be fulfilled by a $100.00 initial balance. |
| `Failed Debits` | 5 | 5 debits must be safely rejected with `InsufficientBalanceException`. |
| `Final Wallet Balance` | $0.0000 | Current balance must never drop below zero. |
| `Ledger Row Count` | 5 | Exactly 5 transaction entries must exist in `wallet_transactions`. |

### 6.2 Strict Idempotency Test (Deduplication & Replay Prevention)

The idempotency test suite verifies that repeating a request with the same `Idempotency-Key` header guarantees exactly one balance mutation while safely returning the original cached response payload.

**Test Scenarios**:

1.  **Single Execution Guarantee**: A `$50.00` credit sent twice with `Idempotency-Key: test-key-1` results in a total balance of `$50.00` (not `$100.00`).
    
2.  **Identical Response Replay**: The second response matches the exact transaction ID and status code returned by the first call.
    
3.  **In-Progress Lock Conflict**: A second concurrent request received while status is `IN_PROGRESS` returns HTTP `409 Conflict`.

## 7. Assumptions & Limitations

### 7.1 System Assumptions

1. **Single Base Currency**: All wallets and transactions in the core MVP assume a unified base currency (e.g., `USD`). Cross-currency transfers or automatic FX conversions are outside the current scope.
2. **Client-Provided Idempotency Keys**: Clients calling state-changing endpoints (`/credit`, `/debit`, `/transfer`) are expected to supply unique `Idempotency-Key` headers (e.g., UUID v4).
3. **Single Database Instance / Primary Node**: Pessimistic row locking (`SELECT FOR UPDATE`) relies on database transaction isolation guaranteed by PostgreSQL primary instances.

### 7.2 Known Gaps & Future Improvements

1. **Distributed Locking (Redis / Redlock)**:
   * *Current Limitation*: Pessimistic DB locks hold database connections open for the duration of the transaction.
   * *Future Improvement*: Integrate Redis-based distributed locks (e.g., Redisson) for ultra-high-throughput lock acquisition before entering the database transaction.

2. **Idempotency Record Archiving & TTL**:
   * *Current Limitation*: `idempotency_records` entries persist indefinitely in PostgreSQL.
   * *Future Improvement*: Add a scheduled background worker or TTL policy to purge or archive idempotency records older than 30–90 days.

3. **Event-Driven Architecture (Transactional Outbox Pattern)**:
   * *Current Limitation*: Downstream notifications (e.g., player email receipts, analytics telemetry) occur synchronously or are not yet emitted.
   * *Future Improvement*: Implement the Transactional Outbox Pattern with Kafka or RabbitMQ to publish `WalletCredited` / `WalletDebited` events asynchronously without impacting API performance.

4. **Two-Phase Authorization Holds (Auth / Capture Pattern)**:
   * *Current Limitation*: Debits are settled immediately in a single atomic transaction.
   * *Future Improvement*: Support pending balance holds (`RESERVATION_HOLD` / `RESERVATION_RELEASE`) for e-commerce or gaming workflows requiring delayed settlement.
  
   * 
## 8. Endpoints
**Base URL:** `/api/v1/wallets/`

### POST `/api/v1/wallets/{playerId}/debit`

Debits funds from a player's wallet

**Headers**

| Header | Value |
|--------|-------|
| `Content-Type` | Required. application/json |

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `playerId` | String | Required. Unique source player ID (e.g., bruce001) |
| `Idempotency-Key` | String | Optional. For state-changing money movement operations (CREDIT, DEBIT, TRANSFER) |

**Request Body**

```json
{
  "amount": 0.0001,
  "reason": "PURCHASE",
  "referenceId": "string",
  "description": "string"
}
```

**Responses 200(OK)**

```json
{
  "id": "30edad0d-75c6-4932-8020-68c6fde20f1c",
  "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
  "amount": -0.0001,
  "balanceAfter": 0,
  "type": "DEBIT",
  "reason": "PURCHASE",
  "referenceId": "string",
  "description": "string",
  "createdAt": "2026-08-30T06:30:23.291143800Z"
}
```
### POST `/api/v1/wallets/{playerId}/credit`

Credits funds to a player's wallet. Create the player's wallet if they don't exist.

**Headers**

| Header | Value |
|--------|-------|
| `Content-Type` | Required. application/json |
| `Idempotency-Key` | String | Optional. For state-changing money movement operations (CREDIT, DEBIT, TRANSFER) |

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `playerId` | String | Required. Unique source player ID (e.g., bruce001) |

**Request Body**

| Field | Type | Description |
|------|------|-------------|
| `amount` | Number | Required. Amount to credit (must be > 0.0000) |
| `reason` | String | Required. Enum (MISSION_REWARD, PURCHASE, ADMIN_ADJUSTMENT, PLAYER_TRANSFER, LOGIN_STREAK, REFUND) |
| `referenceId` | String | Optional. External transaction or reference UUID |
| `description` | String | Optional. Human-readable memo or note |

```json
{
  "amount": 0.0001,
  "reason": "MISSION_REWARD",
  "referenceId": "Mission_1",
  "description": "Mission 1 clear"
}
```

**Responses 200(OK)**

```json
{
  "id": "360024e6-9f74-49ad-ba95-1f3d0ec5b54d",
  "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
  "amount": 0.0001,
  "balanceAfter": 0.0001,
  "type": "CREDIT",
  "reason": "MISSION_REWARD",
  "referenceId": "string",
  "description": "string",
  "createdAt": "2026-08-30T06:29:36.288667500Z"
}
```

### POST `/api/v1/wallets/transfer`

Atomically transfers funds from one player to another.

**Headers**

| Header | Value |
|--------|-------|
| `Content-Type` | Required. application/json |
| `Idempotency-Key` | String | Optional. For state-changing money movement operations (CREDIT, DEBIT, TRANSFER) |

**Request Body**

| Field | Type | Description |
|------|------|-------------|
| `fromPlayerId` | String | Required. Sender player ID  |
| `toPlayerId` | String | Required. Recipient player |
| `amount` | Number | Required. Amount to credit (must be > 0.0000) |
| `description` | String | Optional. Human-readable memo or note |

```json
{
  "fromPlayerId": "Test001",
  "toPlayerId": "Test002",
  "amount": 0.0001,
  "description": "transfer"
}
```

**Responses 200(OK)**

```
Transfer completed successfully
```

### GET `/api/v1/wallets/{playerId}/transactions`

Retrieves a paginated list of transaction ledger entries for a player's wallet.

**Path Parameters**

| Name | Type | Default | Description |
|------|------|------|-------------|
| `playerId` | String | | Required. Unique source player ID (e.g., bruce001) |
| `page` | integer($int32) | 0 | Zero-indexed page number |
| `size` | integer($int32) | 20 | Number of items per page |
| `sortBy` | string | createdAt | Field name to sort by |

**Responses 200(OK)**

```json
{
  "content": [
    {
      "id": "1458c343-388e-44ab-a7b8-84bdf85f0520",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": 0.0001,
      "balanceAfter": 0.0004,
      "type": "CREDIT",
      "reason": "MISSION_REWARD",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:58:47.646093Z"
    },
    {
      "id": "7b7c90f2-7568-4dd5-8ab2-cffac47c8706",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": 0.0001,
      "balanceAfter": 0.0003,
      "type": "CREDIT",
      "reason": "MISSION_REWARD",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:58:45.480212Z"
    },
    {
      "id": "5d7127d7-33dd-4299-b5aa-46cff3ef38a6",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": 0.0001,
      "balanceAfter": 0.0002,
      "type": "CREDIT",
      "reason": "MISSION_REWARD",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:58:41.853213Z"
    },
    {
      "id": "b893af9c-96f3-45e5-8ac4-6625669d1067",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": 0.0001,
      "balanceAfter": 0.0001,
      "type": "CREDIT",
      "reason": "MISSION_REWARD",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:58:39.593369Z"
    },
    {
      "id": "30edad0d-75c6-4932-8020-68c6fde20f1c",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": -0.0001,
      "balanceAfter": 0,
      "type": "DEBIT",
      "reason": "PURCHASE",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:30:23.291144Z"
    },
    {
      "id": "360024e6-9f74-49ad-ba95-1f3d0ec5b54d",
      "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
      "amount": 0.0001,
      "balanceAfter": 0.0001,
      "type": "CREDIT",
      "reason": "MISSION_REWARD",
      "referenceId": "string",
      "description": "string",
      "createdAt": "2026-08-30T06:29:36.288668Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "last": true,
  "totalPages": 1,
  "totalElements": 6,
  "first": true,
  "size": 20,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "numberOfElements": 6,
  "empty": false
}
```

### GET `/api/v1/wallets/{playerId}/balance`

Retrieves current snapshot balance and currency details for a player's wallet.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `playerId` | String | Required. Unique source player ID (e.g., bruce001) |

**Responses 200(OK)**

```json
{
  "walletId": "8123ecb2-b3c6-4f98-a59e-a91690c0ef4b",
  "playerId": "Test0830",
  "balance": 0.0004,
  "reservedBalance": 0,
  "currency": "USD",
  "updatedAt": "2026-08-30T06:58:47.646093Z"
}
```


## Global Error Codes

| Code | Name | Description |
|------|------|-------------|
| 400 | Bad Request | Invalid or non-positive amount |
| 404 | Not Found | Wallet or player ID does not exist |
| 409 | Conflict | Concurrent request currently processing |
| 422 | Unprocessable Content | Idempotency-Key payload mismatch |
| 500 | Internal Server Error | - |
