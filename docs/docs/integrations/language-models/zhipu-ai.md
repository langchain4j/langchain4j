---
sidebar_position: 18
---

# Zhipu AI

- https://www.zhipuai.cn/
- https://open.bigmodel.cn/


## Maven Dependency

:::note
Since `0.37.0`, `langchain4j-zhipu-ai` has migrated to `langchain4j-community` and is renamed to `langchain4j-community-zhipu-ai`
:::

`0.36.2` and previous:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-zhipu-ai</artifactId>
    <version>0.36.2</version>
</dependency>
```

`0.37.0` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-zhipu-ai</artifactId>
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

- `ZhipuAiChatModel`
- `ZhipuAiStreamingChatModel`


## Examples

- [ZhipuAiChatModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-zhipu-ai/src/test/java/dev/langchain4j/model/zhipu/ZhipuAiChatModelIT.java)
- [ZhipuAiStreamingChatModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-zhipu-ai/src/test/java/dev/langchain4j/model/zhipu/ZhipuAiStreamingChatModelIT.java)
