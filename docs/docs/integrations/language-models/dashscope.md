---
sidebar_position: 5
---

# DashScope (Qwen)

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

You can use DashScope with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

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

### Spring Boot

:::note
Since `1.0.0-alpha1`, `langchain4j-dashscope-spring-boot-starter` has migrated to `langchain4j-community` and is renamed
to `langchain4j-community-dashscope-spring-boot-starter`.
:::

Before `1.0.0-alpha1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope-spring-boot-starter</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
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

`langchain4j-community-dashscope` has 4 models to use:

- `QwenChatModel`
- `QwenStreamingChatModel`
- `QwenLanguageModel`
- `QwenStreamingLanguageModel`

`langchain4j-dashscope` Provide text generation image model
- `WanxImageModel`

### `QwenChatModel`

`QwenChatModel` has following parameters to configure when you initialize it:

| Property          | Description                                                                                                                                                                                                                                                                  | Default Value                                                                                                                                                                                            |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| baseUrl           | The URL to connect to. You can use HTTP or websocket to connect to DashScope                                                                                                                                                                                                 | [Text Inference](https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation) and [Multi-Modal](https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation) |
| apiKey            | The API Key                                                                                                                                                                                                                                                                  |                                                                                                                                                                                                          |
| modelName         | The model to use.                                                                                                                                                                                                                                                            | qwen-plus                                                                                                                                                                                                |
| topP              | The probability threshold for kernel sampling controls the diversity of texts generated by the model. the higher the `top_p`, the more diverse the generated texts, and vice versa. Value range: (0, 1.0]. We generally recommend altering this or temperature but not both. |                                                                                                                                                                                                          |
| topK              | The size of the sampled candidate set during the generation process.                                                                                                                                                                                                         |                                                                                                                                                                                                          |
| enableSearch      | Whether the model uses Internet search results for reference when generating text or not.                                                                                                                                                                                    |                                                                                                                                                                                                          |
| seed              | Setting the seed parameter will make the text generation process more deterministic, and is typically used to make the results consistent.                                                                                                                                   |                                                                                                                                                                                                          |
| repetitionPenalty | Repetition in a continuous sequence during model generation. Increasing `repetition_penalty` reduces the repetition in model generation, 1.0 means no penalty. Value range: (0, +inf)                                                                                        |                                                                                                                                                                                                          |
| temperature       | Sampling temperature that controls the diversity of the text generated by the model. the higher the temperature, the more diverse the generated text, and vice versa. Value range: [0, 2)                                                                                    |                                                                                                                                                                                                          |
| stops             | With the stop parameter, the model will automatically stop generating text when it is about to contain the specified string or token_id.                                                                                                                                     |                                                                                                                                                                                                          |
| maxTokens         | The maximum number of tokens returned by this request.                                                                                                                                                                                                                       |                                                                                                                                                                                                          |
| listeners         | Listeners that listen for request, response and errors.                                                                                                                                                                                                                      |                                                                                                                                                                                                          |

### `QwenStreamingChatModel`

Same as `QwenChatModel`

### `QwenLanguageModel`

Same as `QwenChatModel`, except `listeners`.

### `QwenStreamingLanguageModel`

Same as `QwenChatModel`, except `listeners`.

## Examples

### Plain Java

You can initialize `QwenChatModel` by using following code:

```java
ChatModel qwenModel = QwenChatModel.builder()
                    .apiKey("You API key here")
                    .modelName("qwen-max")
                    .build();
```

Or more custom for other parameters:

```java
ChatModel qwenModel = QwenChatModel.builder()
                    .apiKey("You API key here")
                    .modelName("qwen-max")
                    .enableSearch(true)
                    .temperature(0.7)
                    .maxTokens(4096)
                    .stops(List.of("Hello"))
                    .build();
```


How to call text to generate pictures:

```java
WanxImageModel wanxImageModel = WanxImageModel.builder()
                    .modelName("wanx2.1-t2i-plus") 
                    .apiKey("阿里云百炼apikey")     
                    .build();
Response<Image> response = wanxImageModel.generate("美女");
System.out.println(response.content().url());

```

### Spring Boot

After introduce `langchain4j-community-dashscope-spring-boot-starter` dependency, you can simply register `QwenChatModel` bean by using below configuration:

```properties
langchain4j.community.dashscope.chat-model.api-key=<You API Key here>
langchain4j.community.dashscope.chat-model.model-name=qwen-max
# The properties are the same as `QwenChatModel`
# e.g.
# langchain4j.community.dashscope.chat-model.temperature=0.7
# langchain4j.community.dashscope.chat-model.max-tokens=4096
```

### More Examples

You can check more details in [LangChain4j Community](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-dashscope/src/test/java/dev/langchain4j/community/model/dashscope)
