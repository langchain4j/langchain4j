---
sidebar_position: 22
---

# Redis

https://redis.io/


## Maven Dependency

You can use Redis with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

:::note
Since `1.0.0-beta1`, `langchain4j-redis` has migrated to `langchain4j-community` and is renamed to
`langchain4j-community-redis`.
:::

Before `1.0.0-beta1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-redis</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-beta1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-redis</artifactId>
    <version>${latest version here}</version>
</dependency>
```

### Spring Boot

:::note
Since `1.0.0-beta1`, `langchain4j-redis-spring-boot-starter` has migrated to `langchain4j-community` and is renamed
to `langchain4j-community-redis-spring-boot-starter`.
:::

Before `1.0.0-beta1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-redis-spring-boot-starter</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-beta1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-redis-spring-boot-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml

<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <typ>pom</typ>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```


## APIs

- `RedisEmbeddingStore`


## Examples

- [RedisEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/redis-example/src/main/java/RedisEmbeddingStoreExample.java)
