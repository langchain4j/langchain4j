---
sidebar_position: 2
---

# Nace (Drex)

[Drex](https://console.nace.ai/docs) provides typed decisions through a System One-compatible API.
Use the existing [TypeSafe integration](/integrations/decision-models/typesafe) to call it directly with a Nace API key.

:::note
This recipe uses the experimental `langchain4j-typesafe` integration. It supports text, structured fields and
text conversations. It does not add image support or call the specialized Drex Router `/v1/route` endpoint.
:::

## Maven dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-typesafe</artifactId>
    <version>1.24.0-beta34</version>
</dependency>
```

## Typed decisions

Set `NACE_API_KEY` to a key from [Nace Console](https://console.nace.ai).
This complete example asks a yes/no, choice and scale question in one request:

```java
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import java.time.Duration;
import java.util.Map;

public class DrexExample {

    public static DecisionModel decisionModel() {
        String apiKey = System.getenv("NACE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set NACE_API_KEY");
        }

        return new TypeSafeDecisionModel(TypeSafeDecisionModel.builder()
                .baseUrl("https://console.nace.ai")
                .apiKey(apiKey)
                .modelName("drex-max-v1.1")
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .customHeaders(Map.of(
                        "X-Nace-Client", "langchain4j-typesafe-nace-example/1.0",
                        "User-Agent", "langchain4j-typesafe-nace-example/1.0"))) {
            @Override
            public ModelProvider provider() {
                return ModelProvider.OTHER;
            }
        };
    }

    public static void main(String[] args) {
        var request = DecisionRequest.builder()
                .input("I was charged twice. Please refund one charge.")
                .question("refund", YesNoQuestion.from("Is the customer asking for a refund?"))
                .question("team", ChoiceQuestion.builder()
                        .text("Which team should handle this?")
                        .option("billing", "Payments and refunds")
                        .option("support", "Technical problems")
                        .build())
                .question("urgency", ScaleQuestion.builder()
                        .text("How urgent is this?")
                        .level("low")
                        .level("medium")
                        .level("high")
                        .build())
                .build();

        var response = decisionModel().decide(request);
        System.out.println(response.yesNo("refund").probability());
        System.out.println(response.choice("team").value());
        System.out.println(response.choice("team").probabilities());
        System.out.println(response.scale("urgency").mean());
        System.out.println(response.scale("urgency").probabilities());
        System.out.println(response.tokenUsage());
    }
}
```

The base URL does not include `/v1`: the client appends its default `v1/systemone` path.
Use an explicit model ID from the current [Drex model catalog](https://console.nace.ai/docs/drex/reference/models).
Keep the API key associated with the configured destination.

The small subclass changes only `provider()`. Without it, `TypeSafeDecisionModel` reports `TYPESAFE` even for
a different server. `OTHER` prevents listeners and metrics from attributing Nace requests to TypeSafe; the
`X-Nace-Client` header identifies this integration to Nace. Request/response mapping, async execution and listeners
still use the existing implementation. No new provider package is needed.

`yesNo(...).probability()` is the probability of “yes”; it is not confidence in either outcome.
Choice and scale answers retain their distributions. Choose application thresholds using your own evaluation data.
For non-blocking calls, use `decisionModel.decideAsync(request)` and attach a non-blocking continuation.
This example disables automatic retries; configure a bounded retry policy appropriate for your application.

## Model selection with an AI Service

An existing `DecisionModelChatModelRouter` can use this decision model to choose among configured chat models:

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.router.DecisionModelChatModelRouter;
import dev.langchain4j.model.chat.router.RoutingChatModel;
import dev.langchain4j.service.AiServices;

interface Assistant {
    String chat(String message);
}

// Supply configured ChatModel instances, each with its own provider credentials.
ChatModel routed = RoutingChatModel.builder()
        .route("simple", "Simple support questions and factual lookups", smallModel)
        .route("complex", "Multi-step troubleshooting and detailed analysis", largeModel)
        .router(new DecisionModelChatModelRouter(DrexExample.decisionModel()))
        .defaultRoute("complex")
        .build();

Assistant assistant = AiServices.create(Assistant.class, routed);
```

AI Services requires the `langchain4j` dependency in addition to `langchain4j-typesafe`.
Configure `smallModel` and `largeModel` using their respective integration guides.
This sends a choice question to `/v1/systemone`; it does not invoke the specialized
[Drex Router](https://console.nace.ai/docs/drex/guides/router).
The selected chat model receives the original generation request. The decision router uses a text window of the
conversation, with markers for non-text content; it does not evaluate image pixels.

The default route is used when decision selection fails. This is a routing fallback, not a retry of failed
generation on another model. Choose routes compatible with the request's tools, output format, context and input
modalities; do not assume a textual description enforces every capability requirement.
See [Model Routing](/tutorials/model-routing) for async/streaming APIs, tool-loop behavior, context projection and
fallback controls.

## Limits and verification

Drex Max can accept images through the Nace API, but this client rejects image inputs before sending a request.
Do not remove or silently discard images to make a request pass.
Nace-specific metadata such as `request_id` and `evaluation_time_ms` is not exposed by this recipe; it returns the
standard decision answers, model name and token usage supported by `TypeSafeDecisionModel`.

See Nace's current [limits](https://console.nace.ai/docs/drex/reference/limits) and
[errors](https://console.nace.ai/docs/drex/reference/errors).
The recipe has local HTTP-contract tests for typed decisions, async calls, authentication, invalid answers, image
rejection and model-selection fallback. These tests do not establish live service availability or benchmark gains.
Measure task quality, routing plus generation cost and end-to-end latency on your workload before choosing a policy.
