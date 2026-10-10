# Redis Session 运行与验收

## 已实现

- 保持 Spring Boot 2.7.6，由其 BOM 管理 Redis 和 Spring Session 依赖版本。
- `application.yml` 和配置模板通过 `spring.config.import` 导入 `application-session.yml`。
- 使用 Redis 保存会话，不改成 JWT，不改变现有用户接口。
- Cookie 显式命名为 `JSESSIONID`，Path 为 `/api`，HttpOnly 开启，SameSite 为 Lax。
- 默认空闲 30 分钟过期；这不是永久登录。有效会话访问会更新最后访问时间。
- 登录成功更新会话 ID；退出使整个会话失效；未登录查询不创建空会话。
- Redis 中保存脱敏的 `LoginUserVO`，不保存密码；权限判断仍查询数据库中的最新用户信息。
- Redis 故障时不自动退回进程内 Session，以免不同实例出现相互矛盾的登录态。

## 配置

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | 端口 |
| REDIS_USERNAME | 空 | ACL 用户名 |
| REDIS_PASSWORD | 空 | 密码，本地回环开发实例可为空 |
| REDIS_DATABASE | 0 | 数据库编号 |
| SESSION_REDIS_NAMESPACE | tuku1:session | 会话 Key 前缀，不同环境应隔离 |
| SESSION_TIMEOUT | 30m | 空闲过期时间，如 2h |

连接与命令超时均为 3 秒。配置采用 Boot 2.7 的 `spring.redis.*`，不要照搬新版本的 `spring.data.redis.*`。

`application.yml` 被 Git 忽略，但本机文件已同步导入。其他机器需要使用配置模板，并配置自己的 MySQL、COS 和 Redis 连接信息。

迁移前的进程内会话不会自动转入 Redis，首次切换后可能需要重新登录。Cookie 值是 opaque 标识，前端无需解析，也无需自行读取 HttpOnly Cookie。

## 本机 Redis

2026-09-05 已从 Redis 官方 GitHub 的 7.4.11 标签编译本地开发实例，不安装系统服务、不暴露到局域网。

- 可执行目录：`/Users/gjx/.cache/znytk-tools/redis-7.4.11/src`
- 数据和日志：`/Users/gjx/.local/share/znytk/redis`
- 监听：`127.0.0.1:6379`
- AOF：开启；内存上限：128 MB；淘汰策略：noeviction

检查连接：

```sh
/Users/gjx/.cache/znytk-tools/redis-7.4.11/src/redis-cli -h 127.0.0.1 ping
```

本机重启后，确认 6379 未被其他服务占用，再启动：

```sh
/Users/gjx/.cache/znytk-tools/redis-7.4.11/src/redis-server \
  --bind 127.0.0.1 --port 6379 --protected-mode yes \
  --appendonly yes --maxmemory 128mb --maxmemory-policy noeviction \
  --dir /Users/gjx/.local/share/znytk/redis --daemonize yes \
  --pidfile /Users/gjx/.local/share/znytk/redis/redis.pid \
  --logfile /Users/gjx/.local/share/znytk/redis/redis.log
```

停止本项目的开发 Redis（会中断依赖它的会话请求）：

```sh
/Users/gjx/.cache/znytk-tools/redis-7.4.11/src/redis-cli -h 127.0.0.1 shutdown
```

其他机器如有 Docker，也可在后端根目录执行 `docker compose -f compose.redis.yml up -d`。不要与上述进程同时占用 6379。Compose 配置同样仅用于本地开发。

## 后端与前端

先启动 Redis，再通过 IDE 启动后端或执行 `mvn spring-boot:run`。本机 Maven 未加入 PATH，可使用：

```sh
/Users/gjx/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn spring-boot:run
```

前端继续使用 Vite `/api` 代理和 Axios `withCredentials: true`，本次无需修改业务页面。完整业务登录仍需要 MySQL 可用且存在有效用户。

部署 HTTPS 时应配置安全 Cookie，例如 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`，并正确配置可信反向代理。跨站部署还需要另行评估 SameSite、CORS 和 CSRF，当前配置面向同源代理。

## 自动化验收

普通 `mvn test` 执行单元测试；没有 `REDIS_SERVER_BIN` 时，真实 Redis 集成测试会明确跳过。完整验收必须提供 Redis 可执行文件：

```sh
REDIS_SERVER_BIN=/Users/gjx/.cache/znytk-tools/redis-7.4.11/src/redis-server \
  /Users/gjx/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn -B -ntp verify
```

集成测试启动独立 Redis 子进程，使用随机端口、临时目录和独立命名空间，结束时停止，不使用开发实例，也不使用真实用户数据库。

已验证：

- Cookie 名称、Path、HttpOnly、SameSite 和 30 分钟默认空闲时间。
- 登录状态实际存入 Redis，关闭后端并重新启动后可恢复。
- 过期后返回未登录；退出删除会话并清除 Cookie。
- 重新登录更换会话 ID，旧 Cookie 不再有效。
- 匿名请求和失败登录不会无故创建会话。
- 读取当前用户时仍查询数据库中的最新权限。
- 2026-09-05：全部 21 项测试通过，0 失败、0 跳过，JAR 打包通过。

范围说明：数据库查询由 Mockito 替代，Redis、HTTP、Controller、用户会话业务和 Spring Session 都实际运行。完整 JAR 另做了本地 Redis 启动及匿名接口检查；尚未执行真实账号浏览器联调，也未模拟 Redis 自身崩溃恢复。当前 JDK 24 对旧依赖有兼容性警告，集成测试关闭上下文时也有 Lettuce 线程关闭顺序警告，本次未升级原有框架。

## 持久化和安全边界

后端重启不再丢失尚未过期的 Redis 会话，但 Redis 数据丢失、会话过期或浏览器 Cookie 消失仍会导致重新登录。当前 Cookie 没有设置持久化 Max-Age，不承诺关闭浏览器后继续登录。

AOF 减少 Redis 重启造成的数据丢失风险，但不代表零丢失。生产环境应另外配置备份、访问控制和监控；会话不能承受与图片缓存相同的随意淘汰策略，后续第二步需评估资源隔离。不同 Redis 数据库编号不代表内存淘汰隔离。

Spring Session 默认会配置 Redis Keyspace 通知；如果托管 Redis 禁止 CONFIG 命令，应由运维配置所需通知，再按框架要求调整配置动作，不应直接关闭通知而不评估过期事件。
