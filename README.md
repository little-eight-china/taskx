# TaskX

开源分布式任务调度与编排平台。触发、执行和工作流运行态已接入 MySQL + Redis；Admin REST 与 React 管理台可用来测配置与观测。

调度配置落在 MySQL，到期索引落在 Redis Sorted Set。执行者进程独占若干 **slot**，每秒拉取到期任务并执行。不是嵌入式 `Spring @Scheduled`，也不是 Quartz 的薄封装。

| 项 | 取值 |
|----|------|
| 产品名 | TaskX |
| 仓库目录 | `schedule-task` |
| Maven artifact | `taskx`（`groupId` 未定） |
| 语言 | Java 21 |
| 许可证 | MIT |
| 管理台 | React + Admin REST |
| 存储 | MySQL + Redis |

设计拍板以 [docs/00-decisions.md](docs/00-decisions.md) 为准。若其它文档与它冲突，以 00 为准。

## 它解决什么

- **统一触发**：Cron、固定频率（`FIXED_RATE`）、固定延迟（`FIXED_DELAY`）、延时（`DELAY`）、一次性（`ONCE`）走同一套配置和同一套 ZSET。
- **水平加机器不搬任务数据**：Task 用 `CRC32(UTF-8(taskId)) % N` 进固定 N 个 slot（默认 32）。加执行者只改「谁拥有哪个 slot」，ZSET 里的 member 不用迁。
- **执行者身份稳定**：每个进程必须配置稳定 `executor_id`（写在任务行上，**不用** 当 Redis key）。没配 ID 则启动失败。
- **core 可单测**：`taskx-core` 不绑定 Spring；Admin 与 Starter 再接 Spring Boot。
- **版本化编排**：草稿发布为不可变 JSON 快照；运行态使用 Instance / Activation / Attempt / Token / Outbox。

第一版不做：亚秒级触发、ZooKeeper / K8s Operator、自动把挂掉的 slot 抢给其它进程，以及工作流的并行汇聚、循环、回调等待和输入映射。

## 架构

```text
  React 管理台 ──► Admin REST
                      │
                      ▼
              MySQL（配置、任务/工作流运行态、slot 归属）
                      │
              Redis（trigger/workflow ready ZSET + slot 锁）
                      ▲
                      │ 每秒拉取自己拥有的 slot
              执行者进程（Handler 线程池）
```

更完整的模块图和时序见 [docs/02-architecture.md](docs/02-architecture.md)。

**仓库模块**

```text
taskx/                            Maven 父工程（Java 21，groupId 占位 io.taskx）
  taskx-common                    通用能力（分布式锁 SPI，无 Spring / 存储绑定）
  taskx-core                      模型、下次时间、拉取循环（无 Spring，可单测）
  taskx-workflow                  版本化定义、节点 SPI、Token/Outbox 编排引擎
  taskx-meta                      MySQL + Redis（JDBC、Redisson 锁 / ZSET / TIME）
  taskx-admin                     Spring Boot REST（无鉴权）
  taskx-executor                  执行者进程
  taskx-spring-boot-starter       可选：在业务 Spring Boot 应用里嵌入执行者
  taskx-admin-ui                  React 管理台（Vite，不进 Maven reactor）
```

## 触发怎么走

1. 创建/修改配置：管理端先抢对应 **slot 锁**，MySQL 事务未提交时写 Redis（启用则 `ZADD` 下次时间，停用则 `ZREM`）。Redis 失败则回滚库。
2. Redis ZSET：key 为 `trigger:slot:{n}`，**member = 配置 ID**，**score = Unix 秒**。是否到期用 **Redis TIME**（换成秒），不用各机器 JVM 时钟。
3. 执行者约每秒、对每个自己的 slot：抢槽锁（TTL + 看门狗，Redision实现）→ 核对归属表 → `ZRANGEBYSCORE` 到期项。
4. 持锁同步处理：停用/删除则 `ZREM`；否则记下本次 score 作为 `fireTime`，**立刻按「现在」计算下次时间并 `ZADD`**，然后 **异步** 插任务行并 `submit` 线程池。本轮改完 ZSET 再解锁。
5. 异步侧：`(taskId, fireTime)` 唯一插入；`PENDING → RUNNING` 的 CAS 成功才跑 Handler。允许同一 Task 重叠执行。`submit` 失败则该次记 `FAILED`。

`FIXED_DELAY` 在领取时先让 member 不再到期，等任务终态再写下次时间。`ONCE` / `DELAY` 一般跑完即 `ZREM`。

## 一致性（已接受的语义）

- 默认 **至少一次**；若 `ZADD` 已成功、异步插库尚未发生就进程崩溃，**该次会漏跑**。
- 错过的触发 **不补跑**：下次时间从 Redis「现在」往后算，避免停机一小时后把每一分钟都打出来。捞到的那一个过期点仍会执行一次。
- 执行者宕机：它名下的 slot **停止触发**，直到原进程带着同一 ID 重启，或人工改归属。
- `RUNNING` 时崩溃：**不自动重跑**，走管理接口人工处理。`PENDING` 只有产生该行的执行者会自动恢复。
- 两台执行者配了同一 slot：归属表唯一（第二台启动失败）+ Redis 槽锁。改配置与拉取抢同一把锁，避免互相覆盖 score。
- 活执行者数量原则上不超过 N（默认 32）。**改 N 必须停机**：清空全部 ZSET，走全量重建接口再启动。

业务 Handler 应尽量幂等。

## 扩容与维护

- **加机器**：`PUT /api/slots/{n}` 持锁改归属（或先停旧进程）。不要改 N。
- **改 N / Redis 丢数据**：停所有执行者 → 清空触发 ZSET → 全量接口按启用中的配置重算下次时间并写入 → 改归属 → 再启动。
- **misfire**：全局不补跑，与「下次从现在算」一致。

细节见 [docs/05-scale.md](docs/05-scale.md)。

## 文档

| 文档 | 内容 |
|------|------|
| [docs/00-decisions.md](docs/00-decisions.md) | 已拍板结论（事实来源） |
| [docs/01-overview.md](docs/01-overview.md) | 目标、非目标、与竞品的对比维度 |
| [docs/02-architecture.md](docs/02-architecture.md) | 模块、存储、领取时序、slot 锁 |
| [docs/03-domain-model.md](docs/03-domain-model.md) | Task / Trigger / Execution 状态 |
| [docs/04-workflow.md](docs/04-workflow.md) | 编排定义、状态、节点 SPI 与恢复语义 |
| [docs/05-scale.md](docs/05-scale.md) | 规模、改 N、恢复 |
| [docs/06-roadmap.md](docs/06-roadmap.md) | 实现阶段 |
| [docs/07-open-questions.md](docs/07-open-questions.md) | 尚未拍板的边角 |
| [docs/08-schema.md](docs/08-schema.md) | MySQL 表、Redis key、初始化 SQL |
| [docs/09-api.md](docs/09-api.md) | Admin REST 与 curl 示例 |
| [docs/10-testing.md](docs/10-testing.md) | 单元测试与本地联调步骤 |

## 实现路线

0. 设计文档 + 多模块骨架
1. `taskx-core` 可单测
2. 单执行者跑通创建配置 → 到期执行
3. 多执行者、迁 slot、全量重建（迁 slot / 重建接口已随 Admin REST 提供）
4. Admin REST + React 管理台，人工处理滞留任务
5. Spring Boot Starter 嵌入执行者
6. 版本化 DAG 编排基础（当前）

## 本地跑单执行者

需要 JDK 21、Maven、MySQL 8、Redis。逐步操作（含期望结果、Redis/SQL 对照、常见坑）见 [docs/10-testing.md](docs/10-testing.md)。

```bash
docker compose up -d
mysql -h 127.0.0.1 -uroot -ptaskx < docs/sql/schema.sql
mvn -q -pl taskx-core,taskx-common,taskx-meta,taskx-executor -am test package

export TASKX_EXECUTOR_ID=ex-1
export TASKX_SLOTS=0
export TASKX_SEED_DEMO=true
java -jar taskx-executor/target/taskx-executor-0.1.0-SNAPSHOT.jar
```

`TASKX_SEED_DEMO=true` 会写入一条 id 为 `demo` 的 Task（`FIXED_RATE` 60 秒，slot 0）。默认 MySQL 密码 `taskx`，Redis `redis://127.0.0.1:6379`。

## 本地跑 Admin REST

不启执行者也可以先 CRUD。与执行者共用同一套库和 Redis。无鉴权。逐步场景见 [docs/10-testing.md](docs/10-testing.md)。

```bash
mvn -q -pl taskx-admin -am package
java -jar taskx-admin/target/taskx-admin-0.1.0-SNAPSHOT.jar
curl -s localhost:8080/api/health
curl -s -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{"targetType":"HANDLER","targetRef":"demo","enabled":true,"trigger":{"type":"FIXED_RATE","intervalSeconds":60}}'
```

完整接口见 [docs/09-api.md](docs/09-api.md)。重建触发索引前请先停执行者。

## 本地跑管理台 UI

需要 Node 22+。先按上一节起好 Admin REST。

```bash
cd taskx-admin-ui
npm install
npm run dev
```

浏览器打开 [http://localhost:5173](http://localhost:5173)。Vite 把 `/api` 代理到 8080。页面：总览、任务 CRUD、执行记录（requeue/cancel）、集群（迁 slot / 重建）。逐步点法见 [docs/10-testing.md](docs/10-testing.md) 第 10 节。

## 在业务应用里嵌入执行者

依赖 `taskx-spring-boot-starter`（`groupId` 占位 `io.taskx`），设置 `taskx.executor.id`（未设置则整套自动配置不生效）。普通 TaskHandler 与工作流 WorkflowHandler 都可用 `@TaskxHandler("名字")` 或 Bean 名注册。TaskX 使用自己的 MySQL / Redis 连接，不会替代应用原有的 `DataSource`。

```yaml
taskx:
  slot-count: 32
  executor:
    id: ex-1
    slots: [0]
    worker-groups: [default]
  mysql:
    url: jdbc:mysql://127.0.0.1:3306/taskx?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8
    user: root
    password: taskx
  redis:
    address: redis://127.0.0.1:6379
```

```java
import io.taskx.core.handler.TaskHandler;
import io.taskx.spring.TaskxHandler;
import org.springframework.context.annotation.Bean;

@Bean
@TaskxHandler("demo")
TaskHandler demoHandler() {
    return (task, execution) -> { /* 业务 */ };
}
```

工作流 HANDLER 节点使用可返回 JSON 的独立接口：

```java
import io.taskx.workflow.node.WorkflowHandler;

@Bean
@TaskxHandler("score")
WorkflowHandler scoreHandler() {
    return context -> context.nodeInput();
}
```

独立进程仍用 `taskx-executor`。不要把 Admin 和 Starter 塞进同一个进程（第一版未按这个场景测）。

## 尚未拍板

见 [docs/07-open-questions.md](docs/07-open-questions.md)，主要包括：Maven `groupId`、Admin 鉴权和高级编排能力。

## 许可证

MIT。
