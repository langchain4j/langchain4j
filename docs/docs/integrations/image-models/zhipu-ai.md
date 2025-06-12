---
sidebar_position: 6
---

# ZhiPu AI

[ZhiPu AI](https://www.zhipuai.cn/) is a platform to provide model service including text generation, text embedding,
image generation and so on. You can refer to [ZhiPu AI Open Platform](https://open.bigmodel.cn/) for more details.
LangChain4j integrates with ZhiPu AI by using [HTTP endpoint](https://bigmodel.cn/dev/api/normal-model/glm-4). We are
consider migrating it from HTTP endpoint to official SDK and are appreciated of any help!

## Maven Dependency

You can use ZhiPu AI with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

:::note
Since `1.0.0-alpha1`, `langchain4j-zhipu-ai` has migrated to `langchain4j-community` and is renamed to
`langchain4j-community-zhipu-ai`
:::

Before `1.0.0-alpha1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-zhipu-ai</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-zhipu-ai</artifactId>
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

- `ZhipuAiImageModel`


## Examples

- [ZhipuAiImageModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-zhipu-ai/src/test/java/dev/langchain4j/community/model/zhipu/ZhipuAiImageModelIT.java)
