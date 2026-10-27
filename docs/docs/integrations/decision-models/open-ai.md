---
sidebar_position: 2
---

# OpenAI

- [OpenAI Decisions API guide](https://developers.openai.com/api/docs/guides/decisions)

The OpenAI Decisions API (`POST /v1/decisions`) answers yes/no, choice and scale questions about a text, image or
conversation input, with probabilities. It implements the [`DecisionModel`](/tutorials/decision-models) API in two modules:

- `OpenAiDecisionModel` in `langchain4j-open-ai`, which uses LangChain4j's HTTP client
- `OpenAiOfficialDecisionModel` in `langchain4j-open-ai-official`, which uses the official OpenAI Java SDK

:::note
This integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.22.0</version>
</dependency>
```

or, for the official SDK:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-official</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

## Usage

```java
DecisionModel decisionModel = OpenAiDecisionModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
        .build();

DecisionRequest request = DecisionRequest.builder()
        .input("Help! My payouts have been failing for 3 days and nobody answers my emails.")
        .question("team", ChoiceQuestion.builder()
                .text("Which team should handle this ticket?")
                .option("billing", "Payments, payouts, invoices, refunds")
                .option("support", "Problems using the product")
                .option("sales", "Pricing, upgrades, new accounts")
                .build())
        .question("urgent", YesNoQuestion.from("Does this need attention today?"))
        .build();

DecisionResponse response = decisionModel.decide(request);

response.choice("team").value();          // "billing"
response.yesNo("urgent").probability();   // 0.93
```

With the official SDK, use `OpenAiOfficialDecisionModel`, which takes the model name as a `String`:

```java
DecisionModel decisionModel = OpenAiOfficialDecisionModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-6-luna")
        .build();
```

It has the same client settings as the other models of the module, for example `baseUrl`, `timeout`, `maxRetries`
and `openAIClient`.

## Prompt caching

OpenAI caches the shared input of a request automatically. Later requests that start with the same input (at least
1,024 tokens) read it from the cache at a lower price; writing to the cache is charged too. The questions are not part
of the cached input, so you can ask different questions about the same long document, image or conversation and still
reuse the cache. See "Reuse shared input with prompt caching" in the
[OpenAI Decisions API guide](https://developers.openai.com/api/docs/guides/decisions).

To keep cache reuse separate between groups of requests, for example per customer, set a prompt cache key (sent as
`prompt_cache_key`). Only requests with the same key reuse each other's cached input:

```java
DecisionModel decisionModel = OpenAiDecisionModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
        .promptCacheKey("customer-123")
        .build();
```

To use another key for a single request, set it with `OpenAiDecisionRequestParameters`
(`OpenAiOfficialDecisionRequestParameters` for `OpenAiOfficialDecisionModel`, which supports `promptCacheKey(...)` the
same way). It replaces the key set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input("Help! My payouts have been failing for 3 days.")
        .question("urgent", YesNoQuestion.from("Does this need attention today?"))
        .parameters(OpenAiDecisionRequestParameters.builder()
                .promptCacheKey("customer-456")
                .build())
        .build();
```

`response.tokenUsage()` shows how many input tokens were read from and written to the cache (see
[Token usage](#token-usage)).

## Custom parameters

To send a parameter that `OpenAiDecisionModel` does not support yet, set it with `customParameters(...)`. It is added to
the JSON body of every request:

```java
DecisionModel decisionModel = OpenAiDecisionModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
        .customParameters(Map.of("parameter_name", "value"))
        .build();
```

To set custom parameters for a single request, use `OpenAiDecisionRequestParameters`
(`OpenAiOfficialDecisionRequestParameters` for `OpenAiOfficialDecisionModel`, which supports custom parameters the
same way). If not empty, they replace (are not merged with) the custom parameters set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input(...)
        .question(...)
        .parameters(OpenAiDecisionRequestParameters.builder()
                .customParameters(Map.of("parameter_name", "another value"))
                .build())
        .build();
```

Values are written to JSON as they are, so a `Map` value becomes a JSON object and a `String` stays a string.
A custom parameter with the same name as a parameter that LangChain4j sends, such as `model`, replaces it. To choose
the model, use `modelName(...)`: a `model` custom parameter is not reflected in `modelName()` or in what listeners
receive. Answers are always read against the questions of the `DecisionRequest`, so do not replace `questions` or
`input`.

Use the parameters class of the model you call. For example, passing `OpenAiDecisionRequestParameters` to
`OpenAiOfficialDecisionModel` fails with an `IllegalArgumentException`. `DecisionRequestParameters.builder()`, which
has only the parameters common to all decision models, works with every model.

## Spring Boot

Add the starter of the module you use:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot4-starter</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

or `langchain4j-open-ai-official-spring-boot4-starter` for the official SDK. On Spring Boot 3, use
`langchain4j-open-ai-spring-boot-starter` or `langchain4j-open-ai-official-spring-boot-starter` instead
(see [Spring Boot Integration](/tutorials/spring-boot-integration#supported-versions)).

An `OpenAiDecisionModel` bean is created when its API key is set in `application.properties`:

```properties
# Mandatory properties:
langchain4j.open-ai.decision-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.decision-model.model-name=gpt-6-luna

# Optional properties:
langchain4j.open-ai.decision-model.base-url=...
langchain4j.open-ai.decision-model.organization-id=...
langchain4j.open-ai.decision-model.project-id=...
langchain4j.open-ai.decision-model.timeout=...
langchain4j.open-ai.decision-model.max-retries=...
langchain4j.open-ai.decision-model.log-requests=...
langchain4j.open-ai.decision-model.log-responses=...
langchain4j.open-ai.decision-model.custom-headers...=...
langchain4j.open-ai.decision-model.custom-query-params...=...
```

With the official SDK starter, an `OpenAiOfficialDecisionModel` bean is created instead:

```properties
# Mandatory properties:
langchain4j.open-ai-official.decision-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai-official.decision-model.model-name=gpt-6-luna

# Optional properties:
langchain4j.open-ai-official.decision-model.base-url=...
langchain4j.open-ai-official.decision-model.organization-id=...
langchain4j.open-ai-official.decision-model.timeout=...
langchain4j.open-ai-official.decision-model.max-retries=...
langchain4j.open-ai-official.decision-model.custom-headers...=...
```

`DecisionModelListener` beans are registered on the decision model automatically.

With `langchain4j-open-ai`, the starter sends blocking calls with Spring's `RestClient` and non-blocking calls, such
as `decideAsync(...)`, with Spring's `WebClient`, which needs `spring-webflux` on the classpath: see
[Spring Boot Integration](/tutorials/spring-boot-integration#http-client). Without it, `decideAsync(...)` fails with
an `AsyncNotSupportedException`.

## Input

The input can be:
- text (`input(...)`);
- named fields (`inputFields(...)`): each field is sent as a text part labeled with its name, with the value as JSON
  (`comment: "..."`), and each image right after a text part holding its name (`photo:`);
- a conversation (`inputMessages(...)`, see below);
- a list of `TextContent`s and `ImageContent`s (`inputContents(...)`), for example a photo together with a
  description:

```java
DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(
                TextContent.from("The customer says the package arrived like this."),
                ImageContent.from(base64Image, "image/jpeg")))
        .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
        .build();
```

Images can be base64 data, a `data:` URL, or a publicly accessible `http` or `https` URL, which OpenAI downloads
itself, for example `ImageContent.from("https://example.com/package.jpg")`. The detail levels `LOW`, `HIGH` and `AUTO`
are supported; `MEDIUM` and `ULTRA_HIGH` are rejected with an `UnsupportedFeatureException`.

The input can also be a conversation, a list of `ChatMessage`s set with `inputMessages(...)`. It is sent as a
[transcript in JSON](/tutorials/decision-models#conversations) in a user message, followed by the images of the
conversation, each after a text part with its marker (`[image 1]:`). It is not sent as OpenAI `system`, `user` and
`assistant` messages, so that a system message is judged as a part of the conversation instead of being followed as
an instruction.

An `AiMessage` with neither text nor tool execution requests is skipped, and a conversation with nothing left to send
is rejected with an `IllegalArgumentException` before calling the API. Contents other than text and images, and a
`CustomMessage`, are rejected with an `UnsupportedFeatureException`.

## Questions and answers

| `DecisionModel` | OpenAI | Notes |
|---|---|---|
| `YesNoQuestion` | `predicate` | `yesWhen` and `noWhen` are appended to the question |
| `ChoiceQuestion` | `choice` | option names are sent as values, with their descriptions |
| `ScaleQuestion` | `score` | levels are sent as labels |

Choice and scale answers include the probability of each option or level, and a confidence.

The API can refuse to answer a single question, for example because it goes against the usage policies of OpenAI.
The answers to the other questions are still returned, and `response.isRefused(name)` returns `true` for the refused
question (see [Refusals](/tutorials/decision-models#refusals)).

## Token usage

`response.tokenUsage()` is an `OpenAiTokenUsage` (or `OpenAiOfficialTokenUsage`), which includes the number of input
tokens read from the prompt cache and written to it. Both are already counted in the input tokens:

```java
OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) response.tokenUsage();
Integer cachedTokens = tokenUsage.inputTokensDetails().cachedTokens();
Integer cacheWriteTokens = tokenUsage.inputTokensDetails().cacheWriteTokens();
```
