---
sidebar_position: 23
---

# Cohere

:::note

This is the documentation for the community `Cohere` chat model integration.

It is implemented based on [Cohere's V2 Chat API](https://docs.cohere.com/reference/chat).
:::

## Maven Dependency

`1.0.0-alpha1` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-cohere</artifactId>
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

## Chat Model Support

You can instantiate `CohereChatModel` using the following code:

```java
ChatModel model = CohereChatModel.builder()
        .apiKey(System.getenv("CO_API_KEY"))
        .modelName("command-r7b-12-2024")
        .logRequests(true)
        .logResponses(true)
        .build();
```

For streamed responses, use `CohereStreamingChatModel`:

```java
StreamingChatModel streamingModel = CohereStreamingChatModel.builder()
        .apiKey(System.getenv("CO_API_KEY"))
        .modelName("command-r7b-12-2024")
        .logRequests(true)
        .logResponses(true)
        .build();
```

## Configurable Parameters

`CohereChatModel` and `CohereStreamingChatModel` accept the following parameters:

| Property                   | Description                                                                                     | Default Value              |
|----------------------------|-------------------------------------------------------------------------------------------------|----------------------------|
| `baseUrl`                  | The URL to connect to the Cohere API.                                                           | https://api.cohere.com/v2/ |
| `apiKey`                   | The API Key.                                                                                    |                            |
| `modelName`                | The model to use, e.g. `command-r7b-12-2024` or `command-r-plus`.                               |                            |
| `timeout`                  | HTTP client timeout for requests.                                                               |                            |
| `maxRetries`               | Maximum number of retries per request. Only available on `CohereChatModel`.                     | 3                          |
| `temperature`              | Sampling temperature.                                                                           |                            |
| `topP`                     | Nucleus sampling threshold.                                                                     |                            |
| `topK`                     | Limits sampling to the `topK` most likely tokens at each step.                                  |                            |
| `frequencyPenalty`         | Penalty for tokens based on how often they have appeared.                                       |                            |
| `presencePenalty`          | Penalty for tokens that have appeared at least once.                                            |                            |
| `maxTokens`                | The maximum number of tokens returned by this request.                                          |                            |
| `stopSequences`            | Sequences that cause the model to stop generating further text.                                 |                            |
| `toolSpecifications`       | Tool (function) definitions the model can call.                                                 |                            |
| `toolChoice`               | A `ToolChoice` controlling how the model selects tools. Possible values: `AUTO`, `REQUIRED`.    |                            |
| `responseFormat`           | The response format, e.g. `TEXT` or `JSON`.                                                     |                            |
| `thinkingType`             | A `CohereThinkingType` enabling or disabling extended thinking for reasoning-capable models.    |                            |
| `thinkingTokenBudget`      | Maximum tokens the model may spend on internal thinking.                                        |                            |
| `safetyMode`               | A `CohereSafetyMode` inserted into the prompt. Possible values: `CONTEXTUAL`, `STRICT`, `OFF`.  |                            |
| `priority`                 | Request priority when the Cohere API is under load.                                             |                            |
| `seed`                     | If set, the model samples tokens deterministically.                                             |                            |
| `logprobs`                 | Whether to include token log probabilities in the response.                                     |                            |
| `strictTools`              | Whether to enforce strict adherence to tool definitions.                                        |                            |
| `defaultRequestParameters` | Default `ChatRequestParameters` applied to every request.                                       |                            |
| `listeners`                | Listeners that listen for request, response and errors.                                         |                            |
| `logRequests`              | Whether to log request or not.                                                                  | `false`                    |
| `logResponses`             | Whether to log response or not.                                                                 | `false`                    |

## Response Metadata

You can access Cohere-specific response metadata:

```java
ChatResponse response = model.chat(UserMessage.from("Hello"));
CohereChatResponseMetadata metadata = (CohereChatResponseMetadata) response.metadata();

List<CohereLogprobs> logprobs = metadata.logprobs();
CohereBilledUnits billedUnits = metadata.billedUnits();
Integer cachedTokens = metadata.cachedTokens();
```

| Property       | Description                                                                                     |
|----------------|-------------------------------------------------------------------------------------------------|
| `logprobs`     | Log probabilities for generated tokens. Returned when `logprobs` is enabled.                    |
| `billedUnits`  | Billing breakdown for the request (input tokens, output tokens, search units, classifications). |
| `cachedTokens` | Number of tokens served from Cohere's prompt cache.                                             |

## Examples

- [CohereChatModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-cohere/src/test/java/dev/langchain4j/community/model/cohere/common/CohereChatModelIT.java)
- [CohereStreamingChatModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-cohere/src/test/java/dev/langchain4j/community/model/cohere/common/CohereStreamingChatModelIT.java)
