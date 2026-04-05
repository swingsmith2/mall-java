# mall — 高并发商城示例

Spring Boot 3 + **MyBatis** + **PostgreSQL** + **Redis**（缓存 / 购物车 / 下单限流）+ **Spring Security（JWT）**。

## 快速启动

1. 启动依赖服务：

```bash
cd /home/ubuntu/JavaSpace/mall
docker compose up -d
```

2. 运行应用（需本机 JDK 17+）：

```bash
mvn spring-boot:run
```

3. 默认演示账号（密码均为 `demo123`）：

- `demo` — 普通用户  
- `admin` — 管理员（可 `POST /api/admin/products` 上架商品）

生产环境请修改 `application.yml` 中的 `mall.jwt.secret` 与数据库口令。

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/products` | 商品分页（上架） |
| GET | `/api/products/{id}` | 商品详情（Redis 缓存） |
| GET/POST/DELETE | `/api/cart` | 购物车（Redis Hash） |
| POST | `/api/orders` | 下单（原子扣库存 + 可选幂等键） |
| GET | `/api/orders/mine` | 我的订单 |
| POST | `/api/admin/products` | 上架商品（ROLE_ADMIN） |

请求头：`Authorization: Bearer <token>`（除公开接口外）。

## 中等并发 API 测试

### 1) JUnit + Testcontainers（集成测试）

类：`src/test/java/com/mall/MediumConcurrencyApiIT.java`  

- 约 **40** 路并发：公开 `GET /api/products`  
- 约 **35** 路：并发登录 `demo` + 带 JWT 请求 `GET /api/cart`  
- 约 **25** 路：各注册独立用户并下单（校验订单 ID 互不重复、原子扣库存）  
- 约 **30** 路：并发 `GET /api/products/{id}`（缓存预热场景）

需本机 **Docker** 可用；若无 Docker，测试会 **跳过**（`@Testcontainers(disabledWithoutDocker = true)`）。

```bash
cd /home/ubuntu/JavaSpace/mall
mvn test -Dtest=MediumConcurrencyApiIT
```

测试 profile：`application-test.yml`（提高 `orders-per-minute` 上限，避免压测误触限流）。

### 2) Shell 冒烟（对已在跑的进程）

不依赖 Testcontainers，先启动 `docker compose` + `mvn spring-boot:run`，再执行：

```bash
BASE_URL=http://127.0.0.1:8080 CONCURRENCY=40 ./scripts/concurrent-api-smoke.sh
```

默认并发 **35**，对商品列表与购物车各打一轮并行 `curl`。

## 高并发相关设计说明

- **读多写少**：商品详情 `@Cacheable`，下单后 `@CacheEvict` 清理缓存。  
- **库存**：`UPDATE ... WHERE stock >= ?`，以数据库行为为准，避免超卖。  
- **幂等**：请求体可选 `idempotentKey`，表唯一约束 `(user_id, idempotent_key)`；下单事务使用 **SERIALIZABLE**，减轻同键并发问题（高吞吐下可改为业务幂等表 + 消息最终一致）。  
- **限流**：Redis `INCR` + TTL，每用户每分钟下单次数见 `mall.rate-limit.orders-per-minute`。  
- **连接池 / Tomcat**：在 `application.yml` 中可调 Hikari 与 `server.tomcat.threads`。

若需 **Spring Cloud**（Nacos、Gateway、Sentinel 等），可在本仓库之上拆模块接入注册中心与网关，本示例为可运行的单体骨架，便于先跑通业务与压测。
