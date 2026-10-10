# 公开图片列表缓存 Redis 与 Caffeine

本文记录第 2 步 Redis 缓存和第 3 步 Caffeine 多级缓存的实现、配置与验收边界。前端接口不变；新增本地缓存用于减少 Redis 访问，沿用数据库版本号保证各实例不再选择已失效的列表。

## 范围

- 接入已有 `POST /api/picture/list/page/vo`，请求、分页响应结构及前端调用方式不变。
- 无论是否登录、是否管理员，都只查询 `uploadStatus=COMPLETED`、`reviewStatus=PASS`、未逻辑删除的图片。
- `/list/page`、`/list/page/review`、`/my/list/page/vo` 和图片详情不缓存。
- 缓存的是图片分页数据（records/current/size/total），不是图片二进制，也不是 Session。
- 返回之前仍使用现有 VO 转换和批量用户查询补齐最新作者资料，不缓存登录用户、Cookie 或用户资料快照。图片实体内部字段仍通过 VO 转换隔离。
- 第 3 步已在 Redis 前加入 Caffeine；第 4 步已完成原图与派生图字段设计，WebP、缩略图生成及前端加载优化仍未开始。

## 启用前：数据库迁移

先在应用使用的 MySQL 图库数据库中执行 `sql/alter_picture_list_revision.sql`，再启动新后端。
不要重复执行整个 `create_sql.sql` 初始化现有数据库。

迁移只创建 `picture_list_revision` 表及 `id=1` 的初始行，可重复执行，不重置已有版本号。
图片表和版本表必须使用支持事务的 InnoDB，并使用同一个主库数据源。

```sql
SELECT * FROM picture_list_revision;
-- 初次执行应为 id=1, revision=0；后续随着图片变更递增。
```

注意：关闭缓存开关也不停止版本号维护，防止重新开启时读到旧数据，因此版本表仍然是必要迁移。
缺少版本表时，公开读取会尝试直查图片表；图片写入会失败并回滚，而不是提交无法保证失效的变更。

## Key 和请求归一化

Key 格式：

```text
tuku1:picture:public:v1:<数据库版本号>:<归一化查询 JSON 的 SHA-256>
```

- namespace 用于隔离环境；`v1` 表示缓存数据结构版本。
- 哈希输入包含所有 DTO 查询字段：页码、页大小、搜索词、名称、简介、分类、标签、作者 ID、图片 ID、尺寸、格式、排序、固定的公开状态等。不包含请求者身份。
- 字符串去首尾空格，空字符串归为 null；标签去空、去重、排序。
- 页码默认 1，页大小默认 10；要求页码大于 0、页大小在 1～20 之间。
- 默认 `createTime desc`，主排序相同再按 `id desc`，保证稳定分页。
- 排序字段白名单：id、name、createTime、editTime、updateTime、picSize、picWidth、picHeight、picScale。
- 同一份归一化后的 DTO 同时用于 SQL 和 Key，避免“Key 相同但实际查询不同”。
- 查询内容和标签有长度/数量限制；默认只缓存前 50 页，更深页面仍可查询但直接回源。

## 读取流程与 TTL

1. 校验、归一化请求，强制公开状态。
2. 读取 MySQL 单行版本号，构造 Key。
3. 查询本机 Caffeine；命中则反序列化，校验页信息及公开状态后使用。
4. 本地未命中时查询 Redis；命中后使用数据，并在剩余 TTL 有效时回填 Caffeine。
5. 两级均未命中时查询 MySQL 分页和总数，序列化后写入 Redis 和 Caffeine。
6. 无论命中哪一级，使用现有逻辑补齐最新作者信息并返回 PictureVO。

Redis 默认普通结果缓存 **300～360 秒**；空 records 的结果缓存 **30～40 秒**。
每次写入随机增加 TTL，命中不续期。空列表也缓存，但数据库异常不能作为空列表缓存。
使用一次带过期参数的 SET，不采用 SET 后再 EXPIRE 的两次操作。

同进程使用固定 64 个分段锁合并相同 Key 的并发回源，进入锁后再次检查 Caffeine，不保存无限增长的锁映射。
不同后端实例仍可能同时回源；本阶段未引入分布式锁，也没有实现全局请求限流。

## 失效：同事务推进数据库版本号

上传（含 URL、批量、替换）、编辑、管理更新、审核、删除及草稿清理：

```text
开启 MySQL 事务
  → 更新图片
  → revision = revision + 1
提交事务
  → 新请求使用新版本 Key
  → 旧版本缓存不再被新请求使用，由 TTL 自动清理
```

- `PictureListRevision.advance()` 要求已经处于事务中。图片操作失败或事务回滚，版本更新也回滚。
- 新增记录会影响空列表、总数和页边界；所以统一失效所有查询条件，而不只删某一页。
- 数据库与 Redis 不做跨系统事务，也不执行 `KEYS`、全库清理或通配符删除。
- 不依赖“提交之后必须成功删除 Redis”这一操作，因此 Redis 离线时不会丢失失效通知，无须另建 Redis 删除重试队列。
- 并发旧查询始终回填最初读取版本对应的 Key，不能把旧数据写到新版本 Key 下。
- 在已开启的数据库事务里读取公开列表时，绕过缓存，避免把未提交数据写到 Redis。
- 与写事务重叠、已经开始的请求可能看到写入前的数据；不承诺召回已经返回的响应，也不是跨系统线性一致读。
- 通过现有业务 Service 方法写入才能维护版本。直接 SQL 改图片、未来新增绕过这些方法的写入口时，必须在同一事务推进版本。数据库恢复或环境切换时应更换 namespace。

这套策略每次列表请求仍有一次轻量的主键版本查询，且非空列表仍会查询作者信息。
它主要省去图片筛选、排序、分页和 COUNT 查询，不应宣称“命中后完全不访问 MySQL”。
版本行也会串行化图片写入；适用于当前读多写少项目，不宣称是高写入吞吐方案。

## 故障处理

- Redis GET、SET、删除损坏数据失败：告警并回源，不把缓存错误当业务失败；数据库成功结果仍可以填入本地缓存。
- 本进程默认冷却 5 秒，期间跳过 Redis，但在数据库版本可验证时仍可使用对应 Key 的本地缓存；冷却后在本地未命中时自动重试 Redis。
- Redis 数据读取成功但剩余 TTL 查询失败：本次仍可使用已读取且校验通过的数据，但不回填本地缓存，并进入 Redis 冷却。
- 第一笔 Redis 故障仍可能等待 `spring.redis.timeout`（当前 Session 共用配置为 3 秒）；本次没有调整 Session 超时。
- JSON 损坏、页信息不匹配、混入非公开图片：丢弃缓存并回源，不把坏数据返回给前端。
- 版本读取失败：两级缓存都不读，即使本机有热缓存也直接查询图片；图片查询本身失败仍向上传播，不伪装成功。
- Redis 恢复后仍根据数据库当前版本取 Key，旧的已通过列表不会因为 Redis 恢复而被重新选中。
- 缓存降级会增加数据库负载；没有无限重试或永久本地缓存，需要监控日志与数据库承载能力。

**边界：这只降级图片缓存，不降级 Redis Session。**
不读取会话的匿名公开列表可绕过缓存故障；需要认证的接口，以及实际触发 Session 读取/保存的请求，仍可能因 Session Redis 故障失败。不能宣称 Redis 宕机时整个应用无影响。

## 第三部分 Caffeine 本地缓存

- 使用 Spring Boot 2.7.6 BOM 管理的 Caffeine 2.9.3，保持 Java 8 源码目标；不照搬需要更高 Java 版本的 Caffeine 3.x。
- 每个后端进程独立保存 L1，默认 `maximumSize=1000`。Caffeine 通过维护和淘汰收敛到容量限制，不是 JVM 总内存的硬上限。
- 单条 JSON 默认最多 32768 个 UTF-16 字符，超过则不进入 L1，但仍允许正常查询及 Redis 缓存。条数和单条大小双重限制，避免大结果无节制占用内存；对象、Key、索引及并发维护仍有额外开销。
- 本地普通结果最多 30 秒，空结果最多 10 秒，读取不续期。
- 从 Redis 回填时读取毫秒级剩余 TTL，并保守扣除网络及处理耗时；本地有效期取该剩余时间与本地配置值的较小值。Redis Key 已消失、无过期时间、剩余时间非正或 TTL 查询异常时不回填。
- 数据库回源时使用本次 Redis TTL 作为本地存储的上限，扣除写入耗时；Redis 写入失败或处于冷却时仍可短暂缓存新查到的数据库数据。
- 本地保存不可变 JSON 字符串，每次命中还原新的 Page/Picture 对象，避免某次请求修改对象后污染其他请求。
- L1 与 L2 使用完全相同的版本化 Key。实例 A 提交审核/编辑/删除后，实例 B 下一次读取数据库版本也会改用新 Key；不依赖消息通知、Redis Pub/Sub 或广播删除。
- 旧版本 L1 条目由 TTL/容量策略回收，不会随查询条件无限增长；并发旧请求只能回填它原先读取的版本 Key。
- 缓存关闭、深分页、活动数据库事务等绕过规则同时作用于 L1/L2。关闭本地开关可以回到单级 Redis 模式。

这一步减少的是 Redis 网络访问，并未移除数据库版本查询、作者信息补全或 JSON 反序列化。
容量、命中和过期测试不等于实际负载下的性能结论；整体性能对比仍留在第 7 步。

## 配置

默认值位于 `application-picture-cache.yml`，由本机配置和示例配置导入：

```yaml
picture:
  public-cache:
    enabled: true
    namespace: tuku1:picture:public
    ttl-seconds: 300
    jitter-seconds: 60
    empty-ttl-seconds: 30
    failure-cooldown-seconds: 5
    max-cached-page: 50
    local-enabled: true
    local-max-size: 1000
    local-ttl-seconds: 30
    local-empty-ttl-seconds: 10
    local-max-entry-chars: 32768
```

可用 `PICTURE_PUBLIC_CACHE_ENABLED=false` 关闭缓存，用 `PICTURE_PUBLIC_CACHE_NAMESPACE` 隔离环境。
可用 `PICTURE_LOCAL_CACHE_ENABLED=false` 仅关闭本地缓存、保留 Redis；调整容量和 TTL 后重启后端生效。
其他参数可使用 Spring 配置或 `--picture.public-cache.ttl-seconds=300` 等启动参数覆盖。
应确保 namespace 非空、不同数据库/环境不共用；修改序列化格式时升级 Key 结构版本。

## 验证

- `PublicPicturePageCacheTest`：Key 全字段覆盖、归一化、校验、命中、空列表、TTL、故障回源、损坏数据、开关/深页/事务绕过和并发旧版本回填。
- `LocalPicturePageCacheTest`：容量淘汰、单条大小限制、本地/空结果 TTL、不续期、剩余 TTL 与耗时上限、关闭及无效配置。
- `MultiLevelPicturePageCacheTest`：本地命中不访问 Redis、L2 回填、两级过期、跨实例版本切换、对象隔离、故障冷却恢复、版本读取失败、并发回源合并及旧版本回填。使用可控时钟，不依赖长时间 sleep。
- `PublicPictureCacheIntegrationTest`：隔离的真实 Redis 子进程 + H2/MyBatis + Spring 事务；覆盖审核拒绝/通过、编辑、管理更新、删除、替换上传、草稿提交、批量 URL 导入、事务回滚、真实 TTL、Redis OOM 写失败及恢复。
- 第 3 步额外验证两个独立缓存管理器共享真实 Redis 和数据库版本后的审核失效，以及真实 Redis 剩余 TTL 对本地缓存的限制；这是模拟两个后端缓存实例，不是两台生产服务器压测。
- `PictureListCacheRoutingTest`：仅公开列表接入缓存，其他三种列表维持原路由。此用例只测试调用路由，不替代 AuthCheck 权限测试。
- 真实 Redis 用例需要设置 `REDIS_SERVER_BIN` 指向 redis-server；未设置会跳过，不能把跳过当作集成测试通过。

```bash
REDIS_SERVER_BIN=/absolute/path/to/redis-server mvn verify
```

测试使用独立 Redis 和内存测试数据库，不修改开发库、现有 Session 或 COS 文件。
2026-09-05 第 2 步验收含原有测试共 46 项通过。
2026-10-09 第 3 步新增 22 项用例后，全量 68 项，0 失败、0 跳过，`mvn verify` 和可执行 JAR 打包通过。
测试运行时为 JDK 24，仍有旧依赖/Caffeine Unsafe 及 Lettuce 测试上下文关闭警告；未声称在 Java 8 运行时执行过全套测试。
2026-10-09 本机 `localhost:3306/tuku` 只读连接检查仍被拒绝，实际数据库迁移及真实 MySQL + 浏览器验收仍为待办。
