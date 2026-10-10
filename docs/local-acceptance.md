# 本地验收与剩余云端配置

## 2026-10-10 已完成

- 本机 MySQL 数据目录 `/opt/anaconda3/data`，端口 3306，仅监听回环地址。
- 迁移前备份：`~/.cache/znytk-backups/tuku-before-chapter6-20261010.sql`，权限 600，不在仓库内，含私有数据，请勿上传。
- `tuku` 已应用 uploadStatus、列表版本表、原图/缩略图字段迁移。两个 ALTER 脚本是一次性的，不要直接重复执行；先查 information_schema 核验字段。
- 40 条原有图片、2 个原有用户保留；本次临时注册用户已删除。
- H2 与真实 MySQL 两种模式各 82 项后端测试通过；前端 15 项单测、7 项 Chromium E2E 通过。
- 本次启动的 MySQL 保持运行，未注册开机服务。临时后端及验收 Redis 已停止；正式启动前仍需真实 COS 配置。

## 后端真实 MySQL 回归

从后端根目录运行。通过本机环境安全设置 `MYSQL_TEST_PASSWORD`，不要把值写进脚本或提交；普通开发启动使用的是 `DB_PASSWORD`，两者用途不同。

```sh
export MYSQL_TEST_SERVER_URL='jdbc:mysql://127.0.0.1:3306/?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_TEST_USERNAME=root
export REDIS_SERVER_BIN="$HOME/.cache/znytk-tools/redis-7.4.11/src/redis-server"
export JAVA_HOME='/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home'
RUN_CACHE_BENCHMARK=true \
  "$HOME/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn" -B -ntp verify
```

`MYSQL_TEST_SERVER_URL` 必须是本机服务器地址、路径为 `/`，不能指定业务数据库。测试创建随机数据库，运行实际项目建表 SQL，最后删除随机库；测试账号需要创建/删除库和触发器的权限。强制终止 JVM 时自动清理可能无法执行，应只核验并清理该次新建的测试库。

不设置 `MYSQL_TEST_SERVER_URL` 时沿用 H2。两种模式均使用自行启停的隔离 Redis，不清空开发 Redis。COS 调用及作者资料补全仍为测试替身，因此不是完整云端验收。

## 前端真实登录与列表回归

先在 8123 端口启动真实后端及其 MySQL/Redis。建议使用一次性数据库；若使用业务库，测试会新建一个普通用户，必须在结束后精确清理。

从前端根目录运行，先选一个从未使用过且符合 `chapter6check[a-z0-9]+` 的账号名称：

```sh
npm run test:unit -- --run
npm run build
npx tsc --noEmit -p e2e/tsconfig.json
CI=1 RUN_LIVE_E2E=true LIVE_TEST_ACCOUNT=chapter6checkuniquesuffix \
  npx playwright test --project=chromium --reporter=list --retries=0
```

测试随机生成密码，不记录密码；`created-test-account` 附件记录创建的账号及 ID，测试结束使其 Session 失效。数据库清理应同时匹配附件中的 ID、账号和普通用户角色，不使用批量前缀删除，也不要删除既有账号。未设置 RUN_LIVE_E2E 时仅跳过此用例，其他模拟浏览器用例照常运行。

## 仍缺少的云端配置

需要在本机运行后端的环境中配置：

- `COS_SECRET_ID`
- `COS_SECRET_KEY`
- `COS_REGION`
- `COS_BUCKET`

使用测试桶及最小必要权限；确认区域、桶名称和图片处理能力可用。不在聊天、Git、截图或日志中公开密钥。已有 `application.yml` 被 Git 忽略，配置模板只保留环境变量引用。旧密钥如曾泄露，应在腾讯云侧轮换，不从历史文件恢复使用。

配置后仍需实际执行：

1. 用代表性的 JPG、PNG、WebP、GIF 和小图上传，确认真实返回结构、原图保留、主图和缩略图落库及实际展示。
2. 仅针对本轮新建测试图片验证替换、审核、删除及草稿清理，检查 COS 文件与 MySQL 引用；不得用原有业务图片做破坏性测试。
3. 验证实际失败补偿，记录当前内存重试队列非持久、部分清理仅记日志的边界，不宣称可靠分布式事务。
4. 在同一素材、视口和网络、控制浏览器缓存的条件下，多轮比较实际字节数、画质、首屏/LCP 与请求数。

当前缺少这四项配置，因此没有云端压缩率、LCP 或完整上传/替换/删除验收结论。
