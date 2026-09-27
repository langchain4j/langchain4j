---
sidebar_position: 1
---

# TypeSafe

- [TypeSafe Documentation](https://docs.typesafe.ai)
- [System One API Reference](https://docs.typesafe.ai/api)

[TypeSafe](https://typesafe.ai) provides decision models, such as Jev, through its System One API.
`TypeSafeDecisionModel` implements the [`DecisionModel`](/tutorials/decision-models) API on top of it.

The same API is also implemented by other servers, for example [OpenRouter](https://openrouter.ai)
and several self-hosted open models. `TypeSafeDecisionModel` can be used with any of them
by setting the base URL (see [below](#other-servers)).

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
        .state("Help! My payouts have been failing for 3 days.")
        .question("team", ChoiceQuestion.builder()
                .instructions("Which team should handle this ticket?")
                .option("billing", "Payments, payouts, invoices, refunds")
                .option("support", "Problems using the product")
                .build())
        .question("urgent", NoulQuestion.builder()
                .instructions("Does this need attention today?")
                .build())
        .build();

DecisionResponse response = decisionModel.decide(request);

ChoiceAnswer team = (ChoiceAnswer) response.answers().get("team");
NoulAnswer urgent = (NoulAnswer) response.answers().get("urgent");
```

See the [Decision Models](/tutorials/decision-models) tutorial for all question types and how to use the answers.

## Configuration

```java
TypeSafeDecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(...)              // required
        .modelName(...)           // required here or on each request, for example "jev-latest"
        .baseUrl(...)             // defaults to "https://api.typesafe.ai"
        .timeout(...)             // defaults to 60 seconds
        .maxRetries(...)          // defaults to 2
        .logRequests(...)         // defaults to false
        .logResponses(...)        // defaults to false
        .logger(...)              // an alternate SLF4J logger for requests and responses
        .httpClientBuilder(...)   // see below
        .build();
```

The available models are listed in the [TypeSafe documentation](https://docs.typesafe.ai/models).

The HTTP client can be customized with `httpClientBuilder(...)`, see [Customizable HTTP Client](/tutorials/customizable-http-client).

Both `decide()` and `decideAsync()` are supported.

The model supports the `NoulQuestion`, `ChoiceQuestion` and `ScoreQuestion` question types.
The confidence of choice and score answers is computed by the server.

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
        .apiKey("not-used")
        .modelName("my-local-model")
        .build();
```

Servers can differ in the limits they apply (for example, the maximum number of options)
and in how they compute confidence, so check the documentation of the server you use.
