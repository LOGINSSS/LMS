# lms-common 公共模块（library）

> 所有服务共享的公共底座：统一响应 / 异常体系、分页、工具类、JWT、用户上下文、MyBatis-Plus / Swagger / MVC 自动配置。以依赖方式被其它模块引用，**不独立启动**。

## 职责定位

- 分层：**公共层**（无端口、无数据库、无独立运行）
- 依赖：无（其它模块依赖它）

## 核心能力

| 包 | 内容 |
|---|---|
| `domain` | `R`（统一响应）、`dto/PageDTO`（分页结果）、`po/BaseEntity`（id/时间/逻辑删除）、`query/PageQuery`（分页入参） |
| `enums` | `BaseEnum`（枚举接口）、`CommonError`（通用错误码）、`UserType`（1 学生 / 2 教师） |
| `exceptions` | `CommonException` 异常体系：`BizIllegalException` / `BadRequestException` / `UnauthorizedException` / `ForbiddenException` / `DbException` |
| `utils` | `AssertUtils`（断言）、`BeanUtils`（拷贝）、`CollUtils` / `StringUtils` / `DateUtils` / `JsonUtils`（hutool 封装）、`JwtUtils`（签发/校验）、`UserContext`（当前用户 ThreadLocal） |
| `autoconfigure/mvc` | `CommonExceptionAdvice`（全局异常 → JSON）、`UserInfoInterceptor`（解析网关 `user-info` 头 → `UserContext`） |
| `autoconfigure/mybatis` | `BaseMetaObjectHandler`（create_time / update_time 自动填充）、`MybatisAutoConfiguration` |
| `autoconfigure/swagger` | `OpenApiAutoConfiguration`（Knife4j / springdoc 自动配置） |
| `constants` | `Constant`、`ErrorInfo`（错误信息常量） |

## 关键约定

- 所有 Controller 返回 `R<T>`；异常统一由 `CommonExceptionAdvice` 转成 `{code, msg, data}` JSON
- 业务服务配置 `lms.mvc.user-header-enabled: true` 后，`UserInfoInterceptor` 把网关透传的 `user-info` 头解析进 `UserContext`（ThreadLocal），Controller 直接取当前用户
- 分页查询入参继承 `PageQuery`（pageNo / pageSize / sortBy / isAsc），结果统一返回 `PageDTO<T>`

## 目录结构

```
src/main/java/com/lms/common/
├── autoconfigure/   # mvc / mybatis / swagger 三组自动配置（spring.factories 装配）
├── constants/       # 常量
├── domain/          # R / dto / po / query
├── enums/           # 枚举与错误码
├── exceptions/      # 异常体系
└── utils/           # 工具类
```

## 引用方式

```xml
<dependency>
    <groupId>com.lms</groupId>
    <artifactId>lms-common</artifactId>
    <version>${project.version}</version>
</dependency>
```
