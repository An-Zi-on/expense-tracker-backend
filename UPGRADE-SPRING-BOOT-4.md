# 升级到 Spring Boot 4.1.2 / Java 21（为 Spring AI 2.x 做准备）

本次升级把模板从 **Spring Boot 2.6.13 + Java 8 字节码** 迁到 **Spring Boot 4.1.2 + Java 21**。
原因是 Spring AI 2.0 GA（2026-06 发布）基于 Spring Boot 4.0/4.1 + Spring Framework 7，并明确要求 Java 21+，
Boot 2.x 无法承载。

分支：`upgrade/spring-boot-4`

## 版本矩阵

| 组件 | 升级前 | 升级后 |
|---|---|---|
| Spring Boot | 2.6.13 | 4.1.2 |
| Spring Framework | 5.3.x | 7.0.10 |
| Java | 8（字节码） | 21 |
| Jakarta EE | 8（`javax.*`） | 11（`jakarta.*`） |
| Jackson | 2.x | 3.x（`tools.jackson.*`） |
| MyBatis-Plus | 3.5.17（boot2 starter） | 3.5.18（boot4 starter） |
| 接口文档 | knife4j-openapi2 4.4.0（springfox） | knife4j-openapi3-boot4 5.7.10（springdoc 3.x） |
| java-jwt | 3.9.0 | 4.5.0 |

## 一、pom.xml

1. `spring-boot.version` → `4.1.2`，`java.version` → `21`，`maven-compiler-plugin` 用 `<release>21</release>`。
2. **`spring-boot-starter-aop` 改名 `spring-boot-starter-aspectj`**。Boot 4 把 AOP 启动器换了名字，
   不改会直接报 `'dependencies.dependency.version' ... is missing`（BOM 里没有这个坐标了）。
3. `spring-boot-starter-web` → `spring-boot-starter-webmvc`（Boot 4 模块化后的新名字）。
4. MyBatis-Plus 换成 **`mybatis-plus-spring-boot4-starter`**：boot3 starter 在 Boot 4 下自动配置不生效，
   表现为 `@Mapper` 不被扫描、启动报 `No qualifying bean of type 'XxxMapper'`。3.5.15+ 才正式兼容。
5. 分页插件依赖 `mybatis-plus-jsqlparser` 单独引入，版本与 starter 对齐（3.5.9 起从核心包拆出）。
6. knife4j 换成 Boot4 分支 `com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.7.10`
   （上游 `com.github.xiaoymin` 停在 4.5.0，只支持 Boot 3；旧 openapi2 版基于 springfox，无法用于 Boot 4）。

## 二、代码迁移

- `javax.annotation.*` → `jakarta.annotation.*`
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.validation.*` → `jakarta.validation.*`

以上是机械替换，已全量完成（`grep javax.` 无残留）。

## 三、Jackson 3（最容易踩的地方）

Boot 4 默认 Jackson 3，包名从 `com.fasterxml.jackson.databind` 变为 `tools.jackson.databind`。
`JacksonConfiguration` 原实现依赖的两个东西都不存在了：

| 原 API | 现状 |
|---|---|
| `Jackson2ObjectMapperBuilderCustomizer` | 换成 `JsonMapperBuilderCustomizer`（`org.springframework.boot.jackson.autoconfigure`） |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` | 迁到 `tools.jackson.databind.cfg.DateTimeFeature` |
| `com.fasterxml.jackson.datatype.jsr310.*` | 内置到 `tools.jackson.databind.ext.javatime.*` |

新实现只做「配置属性表达不了」的部分：用 `SimpleModule` 注册 `LocalDateTime`/`LocalDate`/`LocalTime`
的全局格式；时区与 Locale 仍由 `application.yml` 的 `spring.jackson.*` 提供。
对外行为不变——接口返回的时间仍是 `2026-11-15 00:00:48` 这种格式，前端无需改动。

另外 `application.yml` 里 **必须删掉** 这段，否则 Boot 4 绑定失败：

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false   # ← 已不是 SerializationFeature 的常量
```

## 四、application.yml

knife4j 的 openapi2 自动 Docket 配置（`knife4j.openapi.title/group/api-rule`）在 5.x 不再生效，
改为 springdoc 风格：

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  group-configs:
    - group: default
      packages-to-scan: anzihe.com.common_template.controller
knife4j:
  enable: true
  setting:
    language: zh_cn
```

## 五、顺带修掉的既有 bug

`UserServiceImpl.register()` 里查询重复账号用的是 `eq("userAccount", ...)`，
而表字段是 `user_account`（`QueryWrapper` 的字符串列名不会做驼峰转换），导致注册必然 500。
登录处写的是 `user_account`，所以只有注册接口坏。已统一改为 `user_account`。

## 六、本地运行

要求：**JDK 21**、MySQL 8+。

```bash
# 1. 建库并导入表结构
mysql -uroot -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS an_project DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p --default-character-set=utf8mb4 an_project < sql/sql.sql

# 2. 启动（注意显式指定端口）
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8123
```

**为什么要显式传 `--server.port`**：如果环境里存在 `SERVER__PORT` 之类的变量，Spring 的宽松绑定会把它
当成 `server.port`，覆盖 yml 里的 8123。命令行参数优先级最高，可以压住它。

访问：
- 接口文档 http://localhost:8123/api/doc.html
- OpenAPI JSON http://localhost:8123/api/v3/api-docs

## 七、接入 Spring AI 2.x

pom 里已留好注释块，放开即可（已确认 `spring-ai-bom:2.0.0` 与
`spring-ai-starter-model-openai:2.0.0` 可正常解析）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

配置与用法：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

```java
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("你是一个简洁的中文助手").build();
    }

    @GetMapping("/chat")
    public BaseResponse<String> chat(@RequestParam String q) {
        return ResultUtils.success(chatClient.prompt().user(q).call().content());
    }
}
```

换国产模型只需替换 starter（如 `spring-ai-starter-model-deepseek`），业务代码不用动。

## 八、已验证

- `mvn clean compile` 通过（JDK 21）
- 应用启动成功：Tomcat 8123，context path `/api`
- `/api/doc.html` 200、`/api/v3/api-docs` 200
- `/api/user/register` 200、`/api/user/login` 返回 345 字符 token
- `/api/user/current` 正确返回用户信息，`createTime` 格式为 `yyyy-MM-dd HH:mm:ss`
- `spring-ai-bom:2.0.0`、`spring-ai-starter-model-openai:2.0.0` 依赖可解析

## 九、还没做的

- 单元测试未补（模板原本也没有）
- 安全加固：`jwt.secret` 仍写在 yml 里，生产应改为环境变量；CORS 目前是 `allowedOriginPatterns("*")`，生产应收窄
- 前端模板尚未跟着升级（前端是 Vue 3，不受 Boot 版本影响，主要是接口字段对齐）
