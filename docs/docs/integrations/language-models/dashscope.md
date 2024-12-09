---
sidebar_position: 5
---

# DashScope (Qwen)

https://dashscope.aliyun.com/


## Maven Dependency

:::note
Since `0.37.0`, `langchain4j-dashscope` has migrated to `langchain4j-community` and is renamed to `langchain4j-community-dashscope`.
:::

`0.36.2` and previous:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
    <version>0.36.2</version>
</dependency>
```

`0.37.0` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>0.37.0</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>0.37.0</version>
        <typ>pom</typ>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

## APIs

- `QwenChatModel`
- `QwenStreamingChatModel`
- `QwenLanguageModel`
- `QwenStreamingLanguageModel`


## Examples

- [DashScope Examples](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-dashscope/src/test/java/dev/langchain4j/model/dashscope)
