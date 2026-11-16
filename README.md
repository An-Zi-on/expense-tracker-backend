# 记账程序后端（expense-tracker-backend）

个人记账系统的后端服务，基于通用后台模板扩展而来：在保留用户 / 角色 / 菜单权限体系的基础上，
新增账单记录、预算、多维统计与移动端采集接口。

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 21 · Spring Boot 4.1.2 |
| 持久层 | MyBatis-Plus 3.5.x · MySQL 9.x |
| 鉴权 | JWT（auth0 java-jwt）+ 自定义 `@AuthCheck` AOP |
| 文档 | Knife4j（`/api/doc.html`） |
| 构建 | Maven |

## 主要功能

- **账单管理**：增删改查、按月份 / 分类 / 关键词筛选、逻辑删除与恢复、CSV 批量导入（按外部单号去重）
- **预算**：按月设置总预算，超支与临界（≥90%）分级提示
- **统计**：月度汇总、每日热力、分类占比、近 12 个月趋势，统计全部下推 SQL，不把明细拉回内存
- **权限体系**：用户 - 角色 - 菜单三级，菜单驱动前端动态路由；`@AuthCheck` 支持 mustRole / anyRole
- **安全**：JWT 登出黑名单（`TokenBlacklist`）、排序字段白名单防注入、密码加盐摘要

## 目录结构

```
src/main/java/anzihe/com/common_template
├── aop/            # 鉴权拦截器
├── common/         # 常量与公共定义（ExpenseConstants 等）
├── config/         # 跨域、Jackson、Knife4j 等配置
├── controller/     # 接口层（expense / system / user）
├── mapper/         # MyBatis-Plus Mapper
├── model/          # entity / DTO / VO
├── service/        # 业务层与实现
└── utils/          # TokenUtil、TokenBlacklist 等
```

## 本地运行

1. 建库并导入表结构：

   ```bash
   mysql -uroot -p an_project < sql/expense.sql
   mysql -uroot -p an_project < sql/role-init.sql
   mysql -uroot -p an_project < sql/menu-expense-system.sql
   mysql -uroot -p an_project < sql/menu-expense-mobile.sql
   ```

2. 修改 `src/main/resources/application.yml` 中的数据库连接。

3. 启动：

   ```bash
   mvn spring-boot:run
   ```

   默认端口 `8123`，context-path `/api`，接口文档 `http://localhost:8123/api/doc.html`。

## 约定

- 金额统一用 `Long` 存「分」，避免浮点误差
- 时间字段由 Jackson 统一格式化为 `yyyy-MM-dd HH:mm:ss`（GMT+8）
- 新增页面需要同时在 `sys_menu` 插入菜单记录，前端按菜单动态注册路由

## 说明

`application.yml` 中保留了本地开发用的数据库连接，仓库仅用于个人学习与技术演示。
部署到公网前请改为环境变量注入，并替换 `jwt.secret`。
