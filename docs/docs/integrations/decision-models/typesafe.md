---
sidebar_position: 1
---

# TypeSafe

- [TypeSafe Documentation](https://docs.typesafe.ai)
- [System One API Reference](https://docs.typesafe.ai/api)

[TypeSafe](https://typesafe.ai) provides decision models, such as Jev, through its System One API.
`TypeSafeDecisionModel` implements the [`DecisionModel`](/tutorials/decision-models) API on top of it.

The same API is also implemented by other servers, for example
[OpenRouter](https://openrouter.ai/docs/guides/community/jev) and inference servers such as
[SGLang](https://docs.sglang.io/docs/supported-models/decision_models) running open models.
`TypeSafeDecisionModel` can be used with any of them by setting the base URL (see [below](#other-servers)).

:::note
This integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-typesafe</artifactId>
    <version>1.21.0-beta31</version>
</dependency>
```

## Usage

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(System.getenv("TYPESAFE_API_KEY"))
        .modelName("jev-latest")
        .build();

DecisionRequest request = DecisionRequest.builder()
        .input("Help! My payouts have been failing for 3 days.")
        .question("team", ChoiceQuestion.builder()
                .text("Which team should handle this ticket?")
                .option("billing", "Payments, payouts, invoices, refunds")
                .option("support", "Problems using the product")
                .build())
        .question("urgent", YesNoQuestion.of("Does this need attention today?"))
        .build();

DecisionResponse response = decisionModel.decide(request);

ChoiceAnswer team = response.choice("team");
YesNoAnswer urgent = response.yesNo("urgent");
```

See the [Decision Models](/tutorials/decision-models) tutorial for all question types and how to use the answers.

## Configuration

```java
TypeSafeDecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(...)              // required for api.typesafe.ai, optional for other servers
        .modelName(...)           // required here or on each request, for example "jev-1.13.0"
        .baseUrl(...)             // defaults to "https://api.typesafe.ai"
        .timeout(...)             // defaults to the HTTP client builder's timeouts, otherwise 15s connect / 60s read
        .maxRetries(...)          // retries after the first attempt; defaults to 2
        .customHeaders(...)       // additional HTTP headers, as a Map or a Supplier<Map>
        .listeners(...)           // DecisionModelListeners, see Observability in the Decision Models tutorial
        .logRequests(...)         // defaults to false
        .logResponses(...)        // defaults to false
        .logger(...)              // an alternate SLF4J logger for requests and responses
        .httpClientBuilder(...)   // see below
        .build();
```

The available models are listed in the [TypeSafe documentation](https://docs.typesafe.ai/models).
Aliases such as `jev-latest` can move to a new version at any time; in production, use a fixed version.

The HTTP client can be customized with `httpClientBuilder(...)`, see [Customizable HTTP Client](/tutorials/customizable-http-client).

Both `decide()` and `decideAsync()` are supported.

The model supports the `YesNoQuestion`, `ChoiceQuestion` and `ScaleQuestion` question types.
In the TypeSafe documentation, yes/no questions are called "noul" questions, scale questions are called "score"
questions, and the input is called the "state".
The confidence of choice and scale answers is computed by the server.

Answers are validated against the request: a missing answer, an answer of the wrong type, an option that was not
offered, a probability outside of 0 to 1, or a scale mean outside of the levels throws
`InvalidDecisionResponseException`. Levels of a scale answer without a reported probability get a probability of 0.

The input and the questions are sent to the server you configure. When using a hosted service, check its data
processing and retention terms, and see [Data protection](/tutorials/decision-models#data-protection).

## Other servers

Any server that implements the System One API (`POST /v1/systemone`) can be used by setting the base URL.
For example, with OpenRouter:

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .baseUrl("https://openrouter.ai/api")
        .apiKey(System.getenv("OPENROUTER_API_KEY"))
        .modelName("typesafe/jev-1.13")
        .build();
```

Or with a self-hosted server:

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .baseUrl("http://localhost:8000")
        .modelName("my-local-model")
        .build();
```

Servers can differ in the limits they apply (for example, the maximum number of options)
and in how they compute confidence, so check the documentation of the server you use.
