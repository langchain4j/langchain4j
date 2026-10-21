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
    <version>1.22.0-beta32</version>
</dependency>
```

## Usage

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(System.getenv("TYPESAFE_API_KEY"))
        .modelName("jev-1.13.0")
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
        .modelName(...)           // required here or on each request (otherwise decide() fails), e.g. "jev-1.13.0"
        .baseUrl(...)             // defaults to "https://api.typesafe.ai"
        .apiPath(...)             // path of the System One endpoint, relative to baseUrl; defaults to "v1/systemone"
        .timeout(...)             // defaults to the HTTP client builder's timeouts, otherwise 15s connect / 60s read
        .maxRetries(...)          // retries after the first attempt; defaults to 2
        .customHeaders(...)       // additional HTTP headers, as a Map or a Supplier<Map>
        .customParameters(...)    // additional parameters in the JSON body, see "Custom parameters" below
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

The model supports the `YesNoQuestion`, `ChoiceQuestion` and `ScaleQuestion` question types, and the
TypeSafe-specific `TypeSafeBoxQuestion` (see [Box questions](#box-questions)).
In the TypeSafe documentation, yes/no questions are called "noul" questions, scale questions are called "score"
questions, and the input is called the "state". A scale level with a description (`level(label, description)`) is
sent as an object with a `label` and a `description`.
Text contents are sent as their text, and images as described in [Images](#images). A conversation
(`inputMessages(...)`) is sent as a [transcript](/tutorials/decision-models#conversations), as `{"messages": [...]}`;
images in it throw an `UnsupportedFeatureException`.
The confidence of choice and scale answers is computed by the server.

### Images

Jev 1.13 and later can judge images, as an [experimental feature](https://docs.typesafe.ai/go/images) of the API.
Images can be part of a list of contents (`inputContents(...)`), or values of named fields (`inputFields(...)`), so
that a question can refer to them by name:

```java
DecisionRequest request = DecisionRequest.builder()
        .inputFields(Map.of(
                "comment", "Arrived like this",
                "photo", ImageContent.from(base64Image, "image/jpeg")))
        .question("damaged", YesNoQuestion.of("Is the item in `photo` damaged?"))
        .build();
```

Each image is sent as an asset and replaced in the input by a reference such as `{{langchain4j-image-1}}`, which the
server replaces with the image. When the request contains images, the `TypeSafe-Danger-Zone-Images: true` header is
sent to enable the feature.
Only PNG and JPEG images given as base64 data are supported: other formats and images given by URL throw an
`UnsupportedFeatureException` without calling the API. Other limits are checked by the server, see the
[TypeSafe documentation](https://docs.typesafe.ai/go/images): for example, each side must be 256 to 1568 pixels and
an image must have at least 78,400 pixels in total (so 256 × 256 is too small), an image can be at most 16 MiB and
the request body at most 32 MiB.
Like all models, decision models answer less reliably about images than about text, so keep the questions simple.

### Box questions

Jev 1.14 and later can also find where something is in an image, with a
[Box question](https://docs.typesafe.ai/go/box), an experimental question type specific to TypeSafe.
`TypeSafeBoxQuestion` names the target and the image, and is answered with a `TypeSafeBoxAnswer`: the four edges
of the smallest box around the target, as fractions of the image's width (`left`, `right`) and height (`top`,
`bottom`), measured from the top-left corner, and a confidence.

```java
DecisionRequest request = DecisionRequest.builder()
        .inputFields(Map.of("photo", ImageContent.from(base64Image, "image/jpeg")))
        .question("mug_visible", YesNoQuestion.of("Is there a coffee mug in `photo`?"))
        .question("mug", TypeSafeBoxQuestion.of("the coffee mug in `photo`"))
        .build();

DecisionResponse response = decisionModel.decide(request);

if (response.yesNo("mug_visible").isYes(0.5)) {
    TypeSafeBoxAnswer mug = response.answer("mug", TypeSafeBoxAnswer.class);
    int x = (int) Math.round(mug.left() * imageWidth);
    int y = (int) Math.round(mug.top() * imageHeight);
    int width = (int) Math.round((mug.right() - mug.left()) * imageWidth);
    int height = (int) Math.round((mug.bottom() - mug.top()) * imageHeight);
}
```

A Box answer is always a box, even when the target is not in the image, so ask a `YesNoQuestion` in the same request
when the target may be absent, as above. Each Box question finds one target. Box questions need a model that supports
them, such as `jev-1.14.0` or `jev-preview`. When the request contains a Box question, the
`TypeSafe-Danger-Zone-Images: true` header is sent.

Answers are validated against the request: a missing answer, an answer of the wrong type, an option that was not
offered, a probability outside of 0 to 1, a scale mean outside of the levels, or a box edge outside of 0 to 1, or a
box whose `top` is greater than its `bottom` or whose `left` is greater than its `right` throws
`InvalidDecisionResponseException`. Levels of a scale answer without a reported probability get a probability of 0.

The input and the questions are sent to the server you configure. When using a hosted service, check its data
processing and retention terms, and see [Data protection](/tutorials/decision-models#data-protection).

## Custom parameters

To send a parameter that `TypeSafeDecisionModel` does not support yet, set it with `customParameters(...)`. It is
added to the JSON body of every request:

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(System.getenv("TYPESAFE_API_KEY"))
        .modelName("jev-latest")
        .customParameters(Map.of("parameter_name", "value"))
        .build();
```

To set custom parameters for a single request, use `TypeSafeDecisionRequestParameters`. If not empty, they
replace (are not merged with) the custom parameters set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input(...)
        .question(...)
        .parameters(TypeSafeDecisionRequestParameters.builder()
                .customParameters(Map.of("parameter_name", "another value"))
                .build())
        .build();
```

Values are written to JSON as they are, so a `Map` value becomes a JSON object and a `String` stays a string.
A custom parameter with the same name as a parameter that LangChain4j sends, such as `model`, replaces it. To choose
the model, use `modelName(...)`: a `model` custom parameter is not reflected in `modelName()` or in what listeners
receive. Answers are always read against the questions of the `DecisionRequest`, so do not replace `questions` or
`state`.

Passing the parameters class of another decision model to `TypeSafeDecisionModel` fails with an
`IllegalArgumentException`. `DecisionRequestParameters.builder()`, which has only the parameters common to all
decision models, works with every model.

## Other servers

Any server that implements the System One API can be used by setting the base URL. Requests are sent to the base URL
followed by the API path, which defaults to `v1/systemone`; if the server exposes the API at another path, set it with
`apiPath(...)`. For example, with OpenRouter:

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .baseUrl("https://openrouter.ai/api")
        .apiKey(System.getenv("OPENROUTER_API_KEY"))
        .modelName("typesafe/jev-1.13")
        .build();
```

For [Ollama](https://docs.ollama.com/capabilities/decision), which also implements this API, use
[`OllamaDecisionModel`](/integrations/decision-models/ollama) instead: it supports images, and reports Ollama as the
provider.

Local and self-hosted servers usually need no API key. Without a GPU, a request can take several seconds, so consider
a longer `timeout(...)`.

`TypeSafeDecisionModel` is tested with the TypeSafe API and OpenRouter.

Servers can differ in the limits they apply (for example, the maximum number of options),
in how they compute confidence and in the quality of their answers: small models and general-purpose language models
served as decision models are usually less accurate and less decisive than dedicated decision models.
Check the documentation of the server you use, and evaluate the answers on your own data.

Whatever the base URL, the model reports `ModelProvider.TYPESAFE` as its provider, for example in listeners and metrics.
