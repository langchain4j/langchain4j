---
sidebar_position: 40
---

# Model Routing

:::note
Model routing is experimental and may change in future releases.
:::

Different requests need different models: a greeting or a short factual question can be answered by a small, fast
and cheap model, while writing code or a detailed analysis needs a larger one. With model routing, each request is
sent to the model that fits it, without changing the rest of the application.

`RoutingChatModel` is a `ChatModel` that sends each request to one of several chat models (routes), as decided by a
`ChatModelRouter`. Since it is a `ChatModel`, it can be used everywhere a chat model is used: AI Services, agents,
RAG, etc.

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel, "Greetings, small talk and short factual questions")
        .route("complex", largeModel, "Writing or debugging code, multi-step reasoning, detailed analysis")
        .router(new DecisionModelChatModelRouter(decisionModel))
        .defaultRoute("complex")
        .build();

Assistant assistant = AiServices.create(Assistant.class, chatModel);
```

For streaming, use `RoutingStreamingChatModel` with `StreamingChatModel`s; it works the same way.

## Routers

A `ChatModelRouter` returns the name of the route for a request, or `null` to use the default route.

### Decision model router

`DecisionModelChatModelRouter` uses a [decision model](/tutorials/decision-models) to choose the route whose
description fits the last user message best. Decision models are typically much faster and cheaper than chat
models, so routing adds little latency and cost compared to the chat model call. Every route needs a description.

It returns `null` (so the default route is used) when the request has no user message, when the decision model
fails, and when the probability of the chosen route is below `minProbability`, which is useful to send the requests
the decision model is not sure about to the larger model:

```java
ChatModelRouter router = DecisionModelChatModelRouter.builder()
        .decisionModel(decisionModel)
        .minProbability(0.7)
        .build();
```

### Custom routers

A router can also be a simple rule:

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel)
        .route("complex", largeModel)
        .router(request -> request.chatRequest().messages().size() > 20 ? "complex" : "simple")
        .build();
```

The router receives the `ChatRequest` and the available routes (`ChatModelRoute`: name and description).

## How requests are routed

- The selected model handles the request as if it was called directly: its default parameters and listeners apply.
- The requests can go to models of different providers, so set only common request parameters
  (`ChatRequestParameters`), not provider-specific ones.
- The rounds of a tool-calling loop stay on the same model: a request that ends with tool results goes to the model
  that requested the tools, without asking the router.
- `supportedCapabilities()` returns the capabilities supported by all routes.
- If the router returns an unknown route, or no route when there is no default route, the request fails with an
  `IllegalStateException`.
