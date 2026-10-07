---
sidebar_position: 2
---

# OpenAI

- [OpenAI Decisions API guide](https://developers.openai.com/api/docs/guides/decisions)

The OpenAI Decisions API (`POST /v1/decisions`) answers yes/no, choice and scale questions about a text or image
input, with probabilities. It implements the [`DecisionModel`](/tutorials/decision-models) API in two modules:

- `OpenAiDecisionModel` in `langchain4j-open-ai`, which uses LangChain4j's HTTP client
- `OpenAiOfficialDecisionModel` in `langchain4j-open-ai-official`, which uses the official OpenAI Java SDK

:::note
The Decisions API is in public beta, and this integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.21.0</version>
</dependency>
```

or, for the official SDK:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-official</artifactId>
    <version>1.21.0-beta31</version>
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
        .question("urgent", YesNoQuestion.of("Does this need attention today?"))
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

## Spring Boot

Add the starter of the module you use:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot4-starter</artifactId>
    <version>1.21.0-beta31</version>
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

With `langchain4j-open-ai`, the starter uses Spring's `RestClient`, which does not support non-blocking calls:
`decideAsync(...)` then fails with an `AsyncNotSupportedException`. To decide asynchronously, provide an
`HttpClientBuilder` bean named `openAiDecisionModelHttpClientBuilder` that supports them, for example
`JdkHttpClient.builder()`.

## Input

The input can be:
- text;
- a `Map` of named values: each value is sent as a text part labeled with its name, with the value as JSON
  (`comment: "..."`), and each image right after a text part holding its name (`photo:`);
- a list of `TextContent`s and `ImageContent`s, for example a photo together with a description:

```java
DecisionRequest request = DecisionRequest.builder()
        .input(List.of(
                TextContent.from("The customer says the package arrived like this."),
                ImageContent.from(base64Image, "image/jpeg")))
        .question("damaged", YesNoQuestion.of("Is the item visibly damaged?"))
        .build();
```

Images must be inline: base64 data, or a `data:` URL. Images referenced by an `http` or `https` URL are not supported
by the API, and are rejected with an `UnsupportedFeatureException` before calling it. The detail levels `LOW`, `HIGH`
and `AUTO` are supported; `MEDIUM` and `ULTRA_HIGH` are rejected with an `UnsupportedFeatureException`.

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

`response.tokenUsage()` is an `OpenAiTokenUsage` (or `OpenAiOfficialTokenUsage`), which includes the number of
cached input tokens.
