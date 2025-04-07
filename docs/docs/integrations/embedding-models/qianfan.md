---
sidebar_position: 18
---

# Qianfan

[百度智能云千帆大模型](https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application)
![image](https://github.com/langchain4j/langchain4j/assets/95265298/600f8006-4484-4a75-829c-c8c16a3130c2)

## Maven Dependency

You can use DashScope with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

:::note
Since `1.0.0-alpha1`, `langchain4j-qianfan` has migrated to `langchain4j-community` and is renamed to `langchain4j-community-qianfan`.
:::

Before `1.0.0-alpha1`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qianfan</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-qianfan</artifactId>
    <version>${latest version here}</version>
</dependency>
```

### Spring Boot

:::note
Since `1.0.0-alpha1`, `langchain4j-qianfan-spring-boot-starter` has migrated to `langchain4j-community` and is renamed
to `langchain4j-community-qianfan-spring-boot-starter`.
:::

Before `1.0.0-alpha1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qianfan-spring-boot-starter</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-qianfan-spring-boot-starter</artifactId>
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

- `QianfanEmbeddingModel`


## Examples

- [QianfanEmbeddingModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-qianfan/src/test/java/dev/langchain4j/community/model/qianfan/QianfanEmbeddingModelIT.java)
