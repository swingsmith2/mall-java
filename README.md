# mall — 高并发商城示例

Spring Boot 3 + **MyBatis** + **PostgreSQL** + **Redis**（缓存 / 购物车 / 限流 / 秒杀预扣）+ **Spring Security（JWT）**。

下单只创建 `CREATED` 订单并预扣库存。支付通过带 HMAC 的回调进入 `PAID`。超时或用户取消进入 `CANCELLED` 并回补库存。通知写在同一事务的 outbox 表里，由调度器投递。秒杀请求先在 Redis 里预扣并入队，再异步落成订单。

本机需要 **JDK 17+**、**Maven**、**Docker**。生成本地密钥时还需要 **python3**。

## 要不要 .env？

不需要手写，也不要提交 `.env`。

| 场景 | 要不要环境文件 |
|------|----------------|
| `docker compose up -d` | 不要。数据库口令默认 `mall`，Redis 口令默认 `mall-redis-dev` |
| `./scripts/run.sh` | 不要准备。没有 `.env.local` 时脚本会生成，并在启动前加载 |
| 直接 `mvn spring-boot:run` | 要自己导出 `JWT_SECRET` 和 `PAY_CALLBACK_SECRET`，否则进程拒绝启动 |
| `mvn test` | 不要。测试使用 `src/test/resources/application-test.yml`，数据库和 Redis 由 Testcontainers 临时拉起 |

`.env.local` 已在 `.gitignore` 里，内容类似：

```bash
JWT_SECRET=...        # 至少 32 字节，启动时校验
PAY_CALLBACK_SECRET=...
REDIS_PASSWORD=mall-redis-dev
```

Spring Boot 不会自动读取 `.env` 或 `.env.local`。`scripts/run.sh` 会先 `source .env.local`，再执行 `mvn spring-boot:run`。

Docker Compose 只会自动读取项目根目录的 `.env`（不是 `.env.local`）来替换 compose 文件里的变量。不创建 `.env` 时，就用上面的默认口令。应用和 Redis 的 `REDIS_PASSWORD` 必须一致；改了口令后执行 `docker compose up -d --force-recreate redis`。

密钥短于 32 字节，或仍是 `change-me` 这类占位符时，应用会在启动阶段失败。

## 怎么运行

```bash
cd /home/ubuntu/JavaSpace/mall
docker compose up -d
./scripts/run.sh
```

应用监听 `http://127.0.0.1:8080`。首次启动会跑 Flyway，并写入演示账号（密码均为 `demo123`）：

- `demo` — 普通用户
- `admin` — 管理员（上架商品、创建秒杀、查看告警）

从旧的无密码 Redis 升级时，重建 Redis：

```bash
docker compose up -d --force-recreate redis
```

支付回调要带 HMAC。在另一个终端：

```bash
set -a && source .env.local && set +a
ORDER_ID=1 AMOUNT_CENT=59900 ./scripts/pay-callback.sh
```

签名字符串是 `orderId|paymentNo|amountCent|status` 的 HMAC-SHA256（十六进制），请求头 `X-Mall-Signature`。

## 怎么测试

### 自动化测试

```bash
cd /home/ubuntu/JavaSpace/mall
mvn test
```

不需要先启动 compose，也不需要 `.env.local`。集成测试会自己起 PostgreSQL 和 Redis。本机 Docker 不可用时，带 `IT` 后缀的测试会跳过；`SecretGuardTest` 和 `PaymentSignerTest` 仍会跑。

当前 Docker Engine 29 要求 API ≥ 1.44。仓库里的 `src/test/resources/docker-java.properties` 已写上 `api.version=1.44`，一般不用再改。

| 测试 | 覆盖 |
|------|------|
| `MediumConcurrencyApiIT` | 约 40 路商品列表、35 路登录加购物车、25 路并发下单、30 路商品详情 |
| `OrderLifecycleIT` | 幂等下单、支付回调、重复回调、取消回补库存、超时关单、outbox 投递 |
| `SeckillIT` | 商家自建商品后 8 人抢 5 件、重复请求不双扣、取消后 Redis 库存可再抢、异步落单 |
| `PlatformFlowIT` | 商家入驻开店、购物车与下单、支付宝/微信/银行卡模拟支付、支付回调、纠纷退款与驳回、超管强制关店 |
| `SecretGuardTest` / `PaymentSignerTest` | 密钥强度、HMAC |

测试 profile 关闭后台定时任务（关单、outbox、秒杀落单由测试方法直接调用），并放宽下单和登录限流。`OrderLifecycleIT` 把支付超时改成 3 秒，所以会多等几秒。

只跑某一类：

```bash
mvn test -Dtest=SeckillIT
mvn test -Dtest=OrderLifecycleIT,MediumConcurrencyApiIT
```

### 对已启动进程做冒烟

先完成上一节的 compose 和 `./scripts/run.sh`，再开一个终端：

```bash
BASE_URL=http://127.0.0.1:8080 CONCURRENCY=40 ./scripts/concurrent-api-smoke.sh
```

默认并发 35，对商品列表和购物车各打一轮。

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 普通用户注册 |
| POST | `/api/auth/login` | 登录，返回 JWT；按用户名限流 |
| POST | `/api/merchant/register` | 商家入驻并开店，无需审核，直接返回 JWT 和店铺 |
| GET | `/api/merchant/shop` | 当前商家的店铺（ROLE_MERCHANT） |
| POST | `/api/merchant/products` | 本店上架商品 |
| POST | `/api/merchant/seckill/activities` | 为本店商品划出秒杀库存 |
| GET | `/api/products` | 商品分页（上架且店铺营业；无店铺的种子商品仍可售） |
| GET | `/api/products/{id}` | 商品详情（Redis 缓存） |
| GET/POST/DELETE | `/api/cart` | 购物车（Redis Hash） |
| POST | `/api/orders` | 下单，状态 `CREATED`，预扣库存；可选幂等键 |
| POST | `/api/orders/{id}/pay` | 模拟支付，渠道 `ALIPAY` / `WECHAT` / `BANK_CARD`，调用后订单直接变为 `PAID` |
| POST | `/api/orders/{id}/cancel` | 待支付订单取消并回补库存 |
| POST | `/api/orders/{id}/disputes` | 买家或该店商家对已支付订单发起纠纷 |
| GET | `/api/orders/mine` | 我的订单 |
| GET | `/api/seckill/activities` | 进行中的秒杀活动 |
| POST | `/api/seckill/activities/{id}/orders` | 秒杀：Redis 预扣后返回排队 token |
| GET | `/api/seckill/orders/{token}` | 查询秒杀是否已落成订单 |
| POST | `/api/payments/callback` | 支付回调（`X-Mall-Signature`，无需登录） |
| GET | `/api/cs/disputes` | 客服查看纠纷（ROLE_CS，种子账号 `cs` / `demo123`） |
| POST | `/api/cs/disputes/{id}/resolve` | 客服处理：`REFUND` 退款并回补库存，`REJECT` 维持已支付 |
| GET | `/api/admin/shops` | 超级管理员查看店铺（ROLE_SUPER_ADMIN，种子账号 `admin`） |
| POST | `/api/admin/shops/{id}/force-close` | 强制关闭店铺，之后不能再售卖或上架 |
| GET | `/api/admin/ops/alerts` | outbox 失败 / 滞留告警 |

角色：`USER` 购买；`MERCHANT` 开店和上架；`CS` 只在纠纷时介入；`SUPER_ADMIN` 只做平台级操作（强制关店、告警）。被强制关闭的店铺不能自行恢复。

订单状态：`CREATED` → `PAID`，`CREATED` → `CANCELLED`，`PAID` → `REFUNDED`。支付截止时间默认 15 分钟（`mall.order.pay-timeout`）。模拟支付的 `payload` 分别按支付宝、微信、银行卡的成功报文返回。

请求头：`Authorization: Bearer <token>`（除公开接口外）。

同一 `paymentNo` 重复回调返回原订单。金额不符、签名错误、订单已关闭都会拒绝。

## 备份与告警

应用已在跑、compose 里的 Postgres 也在跑时：

```bash
./scripts/backup-postgres.sh
./scripts/check-alerts.sh
```

备份写到 `backups/*.dump`（不入库）。告警看 outbox 的 `FAILED` 数量，以及超过 `mall.alert.pending-stale`（默认 60 秒）仍为 `PENDING` 的事件。有告警时接口字段 `open=true`，日志打出 `ALERT mall ...`。Prometheus 抓取 `/actuator/prometheus`（需超级管理员 JWT）。这两条脚本都不读 `.env`。

## 交易与可靠性

- **读多写少**：商品详情 `@Cacheable`，库存变更在事务提交后清缓存。
- **库存**：普通下单 `UPDATE ... WHERE stock >= ?`；取消或超时关单时加回库存。
- **秒杀**：创建活动时从商品库存划出活动库存。抢购请求只走 Redis Lua（库存、每人限购、幂等、入队）。后台把队列落成 `CREATED` 订单，不再次扣商品库存。活动未结束时取消，件数回到 Redis；活动结束后未售出的件数回到商品库存。Redis 已开启 AOF，避免重启后把活动库存装成初始值。
- **状态机**：下单为 `CREATED`。模拟支付或签名通过且金额一致的回调都能变成 `PAID`。客服退款后变为 `REFUNDED` 并回补库存。
- **幂等**：可选 `idempotentKey`，唯一约束 `(user_id, idempotent_key)`，冲突时 `ON CONFLICT DO NOTHING` 后返回已有订单。
- **隔离级别**：默认 `READ COMMITTED`。防超卖靠条件更新。
- **outbox**：`ORDER_CREATED` / `ORDER_PAID` / `ORDER_CANCELLED` / `ORDER_REFUNDED` 与订单写在同一事务。调度器领取后投递，失败按次数退避，超过 `mall.outbox.max-attempts` 标为 `FAILED`。
- **限流**：Redis `INCR` + TTL。下单见 `mall.rate-limit.orders-per-minute`，登录见 `mall.rate-limit.logins-per-minute`。
- **连接池 / Tomcat**：在 `application.yml` 中可调 Hikari 与 `server.tomcat.threads`。
