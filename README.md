# ShopStream – Distributed E-Commerce Platform

A small e-commerce platform built from Spring Boot microservices. Each step of the
assignment adds one technology to the same system: **Docker → Kafka → Elasticsearch →
Redis → Spring Cloud**. The whole stack starts with a single command and only the
gateway is reachable from the host.

```
                        Client
                          │  :8080 (only published port)
                    ┌─────▼─────┐
                    │api-gateway│  Spring Cloud Gateway + X-Request-Id filter
                    └─────┬─────┘
        ┌─────────────┬───┴────┬──────────────┬───────────────┐
   product-service  order-service  cart-service  search-service  notification-service
   MySQL + Redis    MySQL          Redis         Elasticsearch   logs only
        ▲  │            ▲  │          │
        └──┴──── Kafka (KRaft, 1 broker) ──────────────────────┘
              order-events · inventory-events · product-events

  discovery-server (Eureka :8761)   config-server (Spring Cloud Config :8888)
  every service registers with Eureka and loads its settings from config-server
```

| Service | Port (internal) | Data | Talks to |
|---|---|---|---|
| api-gateway | 8080 | – | routes by path to `lb://` services |
| product-service (2 instances) | 8081 | MySQL `productdb`, Redis cache | Kafka producer/consumer, Redis |
| order-service | 8082 | MySQL `orderdb` | Feign → product-service, Kafka |
| cart-service | 8083 | Redis | Feign → order-service |
| search-service | 8084 | Elasticsearch | Kafka consumer |
| notification-service | 8085 | none (logs only) | Kafka consumer |
| discovery-server | 8761 | – | Eureka registry |
| config-server | 8888 | `config-repo/` folder | native config backend |

No service reads another service's database. Cross-service calls are REST (Feign)
or Kafka events, exactly as the diagram in the assignment requires.

---

## 1. Run it

```bash
git clone <your-repo-url> shopstream
cd shopstream
docker compose up --build
```

* First build takes a while: every service image is built with a multi-stage
  `maven:3.9-eclipse-temurin-21` build, so Maven downloads its dependencies once per
  service image.
* After startup:
  * gateway → http://localhost:8080
  * Eureka dashboard → not published (internal only). Use `docker compose logs discovery-server`
    or `docker compose exec` if you want to inspect it.
* Tear down **with** volumes (fresh databases): `docker compose down -v`
* Local build without Docker (needs JDK 21 + Maven 3.9):
  ```bash
  mvn -f product-service/pom.xml package
  ```

Wait ~90 s after `up` before the first request: MySQL, Kafka and Elasticsearch must
report healthy and every service must finish registering with Eureka.

**Startup order matters and is enforced by the compose file.** `discovery-server`,
`config-server`, `kafka`, MySQL, Redis and Elasticsearch all have health checks, and
every business service waits for `condition: service_healthy` on them. Without this,
a service could start while config-server is still booting, skip the
`optional:configserver:` import silently and fall back to `localhost:9092` — which
breaks the Kafka listeners. Check with:

```bash
docker compose ps          # everything that matters shows "(healthy)"
```

**Smoke test** (walks the full asynchronous chain, plus the rejection path):

```powershell
pwsh -File scripts/smoke-test.ps1
```

---

## 2. Step by step: what was implemented

### Step 1 – Docker

* `product-service`: full CRUD (`GET/POST/PUT/DELETE /products`, `GET /products?category=`).
* `order-service`: `POST /orders` (body = list of `productId` + `quantity`), `GET /orders/{id}`,
  `GET /orders?userId=`. It reads prices by calling product-service over HTTP.
* Every service has its own multi-stage `Dockerfile`.
* `docker-compose.yml`: one MySQL container **per** service (`mysql-product`, `mysql-order`),
  named volumes (`product-db-data`, `order-db-data`, …), environment variables and
  `depends_on` with health checks.
* **Nothing points to localhost inside a container.** All connection settings are
  environment variables (`PRODUCT_DB_URL`, `ORDER_DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`,
  `REDIS_HOST`, `ELASTICSEARCH_URIS`, `CONFIG_SERVER_URL`, `EUREKA_URL`), and the
  compose file fills them with container names.

### Step 2 – Kafka (KRaft, 1 broker)

Topics (declared in code with `TopicBuilder`, **3 partitions**, replication factor 1):

| Topic | Key | Events | Producer | Consumers |
|---|---|---|---|---|
| `order-events` | orderId | OrderPlaced, OrderConfirmed, OrderRejected | order-service | product-service, notification-service |
| `inventory-events` | orderId | StockReserved, StockRejected | product-service | order-service, notification-service |
| `product-events` | productId | ProductCreated, ProductUpdated, ProductDeleted | product-service | search-service |

Flow for one order:

1. `POST /api/orders` → order-service stores the order as **PENDING**, then (after the
   transaction commits) publishes **OrderPlaced** keyed by the orderId.
2. product-service (`@KafkaListener`, group `product-service`) reserves stock in **one
   database transaction** and publishes **StockReserved** or **StockRejected**.
3. order-service consumes `inventory-events` and sets **CONFIRMED** / **REJECTED**, then
   publishes OrderConfirmed / OrderRejected.
4. notification-service (group `notification-service`) listens to **both** topics and logs
   one "email" line per final status.

Serialization: `JsonSerializer` / `JsonDeserializer`, payload includes a `type` field
(`OrderPlaced`, `StockReserved`, …) which is the contract between services. Class-name
type headers are switched off (`spring.json.add.type.headers: false`) because each
service owns its own DTO classes; the consumers read the payload into a map and convert it
to their own event class. **The key is always orderId or productId**, so all events of one
order stay in one partition and arrive in order.

**Idempotent stock reservation** (Step 2, task 8): product-service keeps a
`processed_orders` table whose primary key is the orderId. Inside the same transaction
the reservation first checks the key; a redelivered `OrderPlaced` finds it and is skipped,
so stock is never subtracted twice. The outcome (`RESERVED` / `REJECTED`) is stored too,
so a duplicate cannot flip a decision either.

### Step 3 – Elasticsearch

* Single node, `discovery.type=single-node`, security disabled, memory capped with
  `ES_JAVA_OPTS=-Xms512m -Xmx512m`.
* product-service publishes ProductCreated / ProductUpdated / ProductDeleted.
* search-service consumes `product-events` and maintains the index through
  `ElasticsearchRepository` (`ProductSearchRepository`).
* Explicit mapping with `@Document(indexName = "products")` + `@Field`:
  `name`/`description` = `Text`, `category` = `Keyword`, `price` = `Double`, `stock` = `Integer`.
* `GET /search?q=&category=&minPrice=&maxPrice=&page=&size=`:
  * text only → derived repository method
    (`findByNameContainingOrDescriptionContaining`);
  * any filter/combination → `ElasticsearchOperations` with one bool query (text in
    `must` on `name^3` + `description`, category as `term` filter, price as `range` filter).

### Step 4 – Redis

* Redis container with `requirepass redis-secret`; all services read
  `spring.data.redis.host/port/password` from central config.
* cart-service: `@RedisHash("carts")` entity + `CrudRepository`, keyed by `userId`.
  API: get cart, add item, change quantity, remove item, empty cart.
* **TTL decision:** carts expire after **3600 seconds (1 hour)**. It is configured in
  `config-repo/cart-service.yml` (`app.cart.ttl-seconds`) and implemented with
  `@TimeToLive` on the `Cart` entity. Reading a cart refreshes the TTL (sliding expiry),
  which is why an active shopper never loses work but an abandoned cart disappears after
  an hour.
* `POST /api/carts/{userId}/checkout` sends the cart to order-service through Feign and
  clears it **only if the order was accepted** (2xx and status ≠ REJECTED). Note that
  "accepted" means the order was created (PENDING); the stock decision happens later on
  Kafka, so a cart can be cleared for an order that is rejected a moment later — the
  order and its rejection reason stay visible through `GET /api/orders/{id}`. Clearing
  only after confirmation would require order-service to call back into cart-service,
  which the assignment's diagram does not allow.
* product-service caches `GET /products/{id}` with `@Cacheable("products")` (TTL 600 s,
  `RedisCacheManager` in `CacheConfig`) and evicts it with `@CacheEvict` on update,
  delete and whenever stock is reserved.

### Step 5 – Spring Cloud

1. **Discovery**: `discovery-server` with `@EnableEurekaServer`; all services use the
   Eureka client. `docker compose` runs **two** product-service instances
   (`product-service-1`, `product-service-2`) — both register under the same name.
2. **Central configuration**: `config-server` with `@EnableConfigServer`, native profile
   backed by the `config-repo/` folder in this repository (mounted read-only into the
   container). Shared values live in `config-repo/application.yml` (Kafka connection,
   serializers, topic names, logging pattern); per-service values (database URLs, Redis,
   TTLs) live in `<service>.yml`. Services load them with
   `spring.config.import: optional:configserver:`.
3. **Client-side load balancing**: order-service → product-service and
   cart-service → order-service are `@FeignClient(name = "...")` interfaces; no host or
   port is hard-coded anywhere. With two product instances, requests are spread by
   Spring Cloud LoadBalancer.
4. **Server-side load balancing / single entry point**: `api-gateway` (Spring Cloud
   Gateway) routes `/api/products/**`, `/api/orders/**`, `/api/carts/**`,
   `/api/search/**` to `lb://` URIs with `StripPrefix=1`.
5. **Custom global filter**: `RequestIdGlobalFilter` guarantees an `X-Request-Id` on every
   request, logs it at the edge and forwards it. Each service has a small
   `RequestIdFilter` that puts the id into the logging MDC (`%X{requestId}`), and the Feign
   interceptors forward the header on outgoing calls — so one id is visible in the logs of
   gateway → order → product.
6. Only `8080:8080` is published in `docker-compose.yml`; every other container is
   internal-only.

---

## 3. API cheat sheet (through the gateway)

```bash
# products
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"name":"Mechanical Keyboard","description":"Hot-swap, ISO layout","category":"input","price":89.90,"stock":20}'
curl localhost:8080/api/products
curl localhost:8080/api/products/1

# search (Elasticsearch, fed by product-events)
curl "localhost:8080/api/search?q=keyboard&category=input&minPrice=50&maxPrice=100&page=0&size=5"

# cart (Redis)
curl -X POST localhost:8080/api/carts/alice/items -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}'
curl localhost:8080/api/carts/alice
curl -X PUT localhost:8080/api/carts/alice/items/1 -H 'Content-Type: application/json' -d '{"quantity":3}'
curl -X DELETE localhost:8080/api/carts/alice/items/1
curl -X DELETE localhost:8080/api/carts/alice

# checkout -> order-service -> Kafka -> product-service -> back to order-service
curl -X POST localhost:8080/api/carts/alice/checkout
curl localhost:8080/api/orders/<orderId>          # PENDING, then CONFIRMED after a moment
```

Watch the asynchronous part:

```bash
docker compose logs -f order-service product-service notification-service search-service
```

---

## 4. Verification results (actual run)

`docker compose up -d --build` on Windows + Docker Desktop 29.9.3, then `scripts/smoke-test.ps1`:

| Check | Observed |
|---|---|
| 14 containers | all `Up`, 7 infra containers `(healthy)` |
| Eureka registry | `PRODUCT-SERVICE` shows **2 instances UP** (172.19.0.10, 172.19.0.13) |
| Central config | all 7 services logged zero `Could not locate PropertySource`; `app.kafka.topics.order-events=order-events` served from `config-repo` |
| Product CRUD | `POST /api/products` → id 2, `GET` returns the same document |
| Cache | Redis keys `products::2`, `TTL ≈ 586 s` (configured 600) |
| Kafka → Elasticsearch | `GET /api/search?q=keyboard&category=input&minPrice=50&maxPrice=100` → `total: 2` |
| Cart | `TTL = 3600 s` on `carts:bob`, quantity update works |
| Checkout happy path | `CO PENDING` → polling → `CONFIRMED`, total 269.70, stock 20 → 17, cart emptied |
| Checkout sad path | stock 1, cart 5 → order `REJECTED`, reason `insufficient stock for product 3 (available 1, requested 5)`, stock stayed 1 |
| Idempotency | same `OrderPlaced` published twice to `order-events` → stock 17 → 17, logs: `already processed - duplicate OrderPlaced ignored` (×2) |
| Notification "emails" | `OrderPlaced`, `StockReserved`, `OrderConfirmed` / `OrderRejected` lines all emitted |
| Request correlation | one `X-Request-Id` (e.g. `1f050417-…`) appears in gateway, cart-service and order-service log lines |

## 5. Design decisions

* **Events carry intents, not prices.** `OrderPlaced` sends `productId` + `quantity`;
  product-service owns the stock and order-service asks product-service for the price over
  Feign when the order is created. That keeps a single source of truth for money.
* **Publish after commit.** order-service and product-service register a
  `TransactionSynchronization` and publish the Kafka event in `afterCommit`, so a rollback
  can never leak an event.
* **Map-based JSON consumption.** Because services are independent Maven projects with
  their own DTOs, the consumers deserialize to a map and convert to their own event class
  instead of relying on shared class names in Kafka type headers. The `type` field inside
  the payload is the documented contract.
* **Route table lives next to the gateway** (`api-gateway/src/main/resources/application.yml`)
  so the gateway still routes if config-server is not up yet; central config supplies the
  tuning (timeouts, header name).
* **Only the gateway is exposed.** Databases, Redis, Kafka and Elasticsearch are not
  reachable from the host, which matches the "single entry point" requirement and avoids
  leaking infrastructure ports.
* **Cart TTL = 1 hour (sliding).** Chosen so a demo session never loses a cart, while
  abandoned carts are garbage-collected by Redis without extra jobs.

## 6. Challenges and how they were solved

| Challenge | Solution |
|---|---|
| Events arriving twice (Kafka is at-least-once) reducing stock twice | `processed_orders` primary-key ledger written inside the same transaction as the stock update |
| Emitting an event for an order that was rolled back | publish in `TransactionSynchronization.afterCommit()` |
| Consumers needing different DTOs per service | plain JSON + `type` field, type headers disabled, `Map` → typed record conversion |
| Two product instances but one image | `docker compose` starts the same image twice; both register in Eureka under `product-service` |
| Correlating requests across four services | gateway filter creates `X-Request-Id`, MDC filter logs it, Feign interceptor forwards it |
| Slow first start (MySQL/Kafka/ES cold) | health checks + `depends_on: condition: service_healthy`; Kafka topics created by the services themselves |
| Cached product after stock change | `@CacheEvict(allEntries = true)` on stock reservation, targeted evict on update/delete |

## 7. Repository layout

```
.
├── docker-compose.yml          # the whole system, one command
├── config-repo/                # central configuration (native config backend)
│   ├── application.yml         # shared: Kafka, topics, logging pattern
│   ├── product-service.yml
│   ├── order-service.yml
│   ├── cart-service.yml
│   ├── search-service.yml
│   ├── notification-service.yml
│   └── api-gateway.yml
├── api-gateway/                # Spring Cloud Gateway + X-Request-Id filter
├── discovery-server/           # Eureka
├── config-server/              # Spring Cloud Config
├── scripts/smoke-test.ps1      # end-to-end check of the running stack
├── product-service/            # MySQL CRUD + stock + Redis cache + Kafka
├── order-service/              # orders + Feign + Kafka state machine
├── cart-service/               # Redis carts + checkout
├── search-service/             # Elasticsearch search + Kafka sync
└── notification-service/       # Kafka listeners, logs only
```

Each service folder contains `pom.xml`, `Dockerfile`, `src/main/java`, `src/main/resources/application.yml`.

## 8. Hand-in

* GitHub repository with **regular commits** (one commit per step is a good rhythm:
  `step1: docker`, `step2: kafka`, …).
* Add **Fadi-97** as collaborator and send the link by email.
* Deadline: **4 December**, demo: **9 December** (15 minutes).
