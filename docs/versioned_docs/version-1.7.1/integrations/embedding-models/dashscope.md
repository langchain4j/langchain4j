---
sidebar_position: 5
---

# DashScope

[DashScope](https://dashscope.aliyun.com/) is a platform developed by [Alibaba Cloud](https://www.alibabacloud.com/).
It provides an interface for model visualization, monitoring, and debugging, particularly when working with AI/ML
models in production environments. The platform allows users to visualize performance metrics, track model behavior, and
identify potential issues early on in the deployment cycle.

[Qwen](https://tongyi.aliyun.com/) models are a series of generative AI models developed
by [Alibaba Cloud](https://www.alibabacloud.com/). The Qwen family of models are specifically designed for tasks like
text generation, summarization, question answering, and various NLP tasks.

You can refer
to [DashScope Document](https://help.aliyun.com/zh/model-studio/getting-started/?spm=a2c4g.11186623.help-menu-2400256.d_0.6655453aLIyxGp)
for more details. LangChain4j integrates with DashScope by
Using [DashScope Java SDK](https://help.aliyun.com/zh/dashscope/java-sdk-best-practices?spm=a2c4g.11186623.0.0.272a1507Ne69ja)

## Maven Dependency

:::note
Since `1.0.0-alpha1`, `langchain4j-dashscope` has migrated to `langchain4j-community` and is renamed to
`langchain4j-community-dashscope`.
:::

Before `1.0.0-alpha1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
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
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

## Configurable Parameters

`QwenEmbeddingModel` has following parameters to configure when you initialize it:

| Property  | Description                                                                  | Default Value                                                                           |
|-----------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| baseUrl   | The URL to connect to. You can use HTTP or websocket to connect to DashScope | https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding |
| apiKey    | The API Key                                                                  |                                                                                         |
| modelName | The model to use.                                                            | text-embedding-v2                                                                       |

## Examples

- [QwenEmbeddingModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-dashscope/src/test/java/dev/langchain4j/community/model/dashscope/QwenEmbeddingModelIT.java)
