# AI Memory

## Project Structure Map

### Backend Main Source

`src/main/java/learn/gjx/tuku1/`

```text
Tuku1Application.java        # Spring Boot application entry.
controller/                  # HTTP API layer; returns BaseResponse.
  PictureController.java     # Picture upload, URL import, batch import, review, list, edit, process APIs.
  UserController.java        # Register, login, logout, user admin, and user VO query APIs.
service/                     # Business contracts; implementations live under impl/.
  impl/                      # UserServiceImpl and PictureServiceImpl hold core workflows.
manager/                     # External resource orchestration and lower-level file/COS helpers.
  FileManager.java           # Picture validation, URL import, temp files, COS URL/key, image processing.
  CosManager.java            # Tencent COS object upload, download, and delete wrapper.
aop/                         # AuthCheck login and role validation.
common/                      # BaseResponse and ResultUtils response helpers.
exception/                   # ErrorCode, BusinessException, ThrowUtils, and global exception handling.
model/                       # entity, dto, vo, and enum types.
mapper/                      # MyBatis-Plus mapper interfaces.
```

### Config And Database

`src/main/resources/`, `sql/`

```text
application.yml              # Port, /api context path, datasource, MyBatis-Plus, Knife4j, and COS config.
generator/mapper/            # XML mappers for the generator package.
static/index.html            # Static placeholder page.
sql/create_sql.sql           # Base user and picture tables.
sql/alter_picture_review.sql # Picture review-field migration.
sql/alter_picture_source_url.sql # Picture sourceUrl migration.
```

### Generated And Demo Code

`src/main/java/`

```text
generator/                   # MyBatis generator output; avoid placing main business changes here.
learn/gjx/tuku1/demos/web/   # Spring Boot demo controllers; not the main business API surface.
```

## Project Conventions

- Follow `AGENTS.md` and `CLAUDE.md`: keep Java code clear, robust, and aligned with the existing Controller/Service/Mapper/MyBatis-Plus structure.
- APIs use `BaseResponse`/`ResultUtils`; business failures should use `BusinessException`, `ErrorCode`, and `ThrowUtils`.
- Use `@AuthCheck` for login/admin protection. Login state is session-based via `UserConstant.USER_LOGIN_STATE`; admin role is `UserRoleEnum.ADMIN`.
- Substantive repository edits require `logs/<month>/<week>.md`; pure Q&A or read-only checks require `logs/questions/<month>/<week>.md`.

## User Preferences

- For project inspection requests, start with a read-only structure/config/risk scan before suggesting edits.
- Keep answers concrete and operational; follow the repository logging rules automatically.

## Current Feature State

- Backend is Spring Boot 2.7.6 targeting Java 8, with MyBatis-Plus, Knife4j, MySQL, Hutool, Jsoup, Lombok, and Tencent COS.
- Server config uses port 8123 and context path `/api`.
- User flows include register, login/logout, current user, admin user CRUD, and user VO listing.
- Picture flows include local upload, URL import, admin batch import, review, public approved listing, personal listing, edit/update, delete, and image processing.
- Picture table and entity include `sourceUrl` plus review status/message/reviewer/time fields.
- `FileManager` enforces picture size/suffix checks, rejects localhost/private-network URL imports, handles redirects/download temp files, and delegates COS operations.

## Known Constraints

- `application.yml` contains datasource and COS sensitive configuration values; do not copy concrete secrets into memory, logs, issues, or shared docs.
- This checkout may lack Git metadata; do not depend on `git status` for project orientation.
- This Mac has OpenJDK 24 and IDEA-bundled Maven available; the project still targets Java 8. `pom.xml` pins Lombok 1.18.38 and annotationProcessorPaths so Lombok-heavy compile errors are no longer expected from JDK 24 annotation processing.
- A shared IDEA Application run config exists at `.run/Tuku1Application.run.xml`; if runtime compatibility issues appear, prefer switching the IDEA project SDK to JDK 17 before changing business code.

## Known Risks

- `UserQueryRequest.sortField` and `PictureQueryRequest.sortField` are passed into MyBatis-Plus `orderBy`; future security work should add a whitelist mapping.
- `GlobalExceptionHandler` currently handles `BusinessException`; consider a generic fallback for unexpected exceptions.
- Some comments contain encoding artifacts; fix nearby when touching those files.

## Related Projects

- Frontend per `AGENTS.md`: `E:/JSProjects/qd_tuku`, source in `E:/JSProjects/qd_tuku/src`, generated API in `E:/JSProjects/qd_tuku/src/service/api`, request wrapper in `E:/JSProjects/qd_tuku/src/request.ts`.
- macOS local frontend checkout for this machine: `/Users/gjx/Desktop/git/qd_tuku_source_20260608`; use this path for future frontend verification.
- Before frontend/backend joint debugging, read the frontend `AGENTS.md`.

## Last Updated

2026-06-15 15:24:00 Asia/Shanghai
