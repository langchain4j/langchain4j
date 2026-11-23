---
sidebar_position: 8
---

# DashScope (Qwen)

[DashScope](https://dashscope.aliyuncs.com/) is a platform developed by [Alibaba Cloud](https://www.alibabacloud.com/).
[Qwen](https://tongyi.aliyun.com/) models are a series of generative AI models developed by [Alibaba Cloud](https://www.alibabacloud.com/).

The `QwenScoringModel` uses the
[DashScope Text Rerank API](https://www.alibabacloud.com/help/en/model-studio/text-rerank-api)
to rank a list of documents (or text segments) based on their relevance to a user query.
LangChain4j integrates with DashScope using the
[DashScope Java SDK](https://help.aliyun.com/zh/dashscope/java-sdk-best-practices).

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

### Spring Boot

To use it in a Spring Boot application, add the appropriate starter
(`langchain4j-community-dashscope-spring-boot-starter` for Spring Boot 3, or
`langchain4j-community-dashscope-spring-boot4-starter` for Spring Boot 4) and configure the
`langchain4j.community.dashscope.scoring-model.*` properties.

## API Key

You can create a DashScope API key at
[https://bailian.console.aliyun.com/?apiKey=1](https://bailian.console.aliyun.com/?apiKey=1)
by following the
[Get an API key](https://www.alibabacloud.com/help/en/model-studio/get-api-key) guide.

Pass it to the builder via `.apiKey(...)`:

```java
QwenScoringModel.builder()
    .apiKey("your-api-key")
    .build();
```

## QwenScoringModel

The `QwenScoringModel` provides a LangChain4j implementation of a `ScoringModel` using the DashScope Text Rerank API.

Available rerank model IDs (defined in `QwenModelName`):

- `gte-rerank-v2` – large-scale text reranking (up to 30k documents)
- `qwen3-rerank` – text semantics / RAG, many languages
- `qwen3-vl-rerank` – multimodal (image/video) cross-modal reranking

> 🔗 [View the Text Rerank API reference](https://www.alibabacloud.com/help/en/model-studio/text-rerank-api)

### Configurable Parameters

| Property         | Description                                                                                          | Default Value                |
|------------------|------------------------------------------------------------------------------------------------------|------------------------------|
| baseUrl          | The base URL to connect to DashScope                                                                 | DashScope default            |
| apiKey           | The API Key                                                                                          |                              |
| modelName        | The rerank model to use                                                                              | gte-rerank-v2                |
| topN             | The number of most relevant documents to return                                                      | returns all documents        |
| returnDocuments  | Whether to return the original document text in the response metadata                                | false                        |
| instruct         | The instruction for reranking                                                                        |                              |

> **Note:** When `topN` is set, `scoreAll` returns only the top `topN` scores (not one per input
> `TextSegment`), which deviates from the 1:1 mapping described in `ScoringModel#scoreAll`.

### Example

```java
import dev.langchain4j.community.model.dashscope.QwenScoringModel;
import dev.langchain4j.community.model.dashscope.QwenScoringResponseMetadata;
import dev.langchain4j.community.model.dashscope.QwenModelName;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;

ScoringModel scoringModel = QwenScoringModel.builder()
    .apiKey("your-api-key")
    .modelName(QwenModelName.QWEN3_RERANK)
    .returnDocuments(true)
    .build();

var response = scoringModel.scoreAll(
    List.of(
        TextSegment.from("The Maine Coon is a large domesticated cat breed."),
        TextSegment.from("The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds.")
    ),
    "tell me about dogs"
);

// scores are returned in the order of the input TextSegments
System.out.println(response.content());

// the original request_id and output.results are always available via metadata,
// regardless of whether returnDocuments was enabled
QwenScoringResponseMetadata metadata = (QwenScoringResponseMetadata)
    response.metadata().get(QwenScoringResponseMetadata.DASHSCOPE_RESPONSE);
System.out.println(metadata.requestId());
System.out.println(metadata.results());
```

## Examples

- [QwenScoringModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-dashscope/src/test/java/dev/langchain4j/community/model/dashscope/QwenScoringModelIT.java)
