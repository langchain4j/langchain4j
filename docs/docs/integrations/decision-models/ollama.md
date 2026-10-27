---
sidebar_position: 3
---

# Ollama

- [Ollama decision guide](https://docs.ollama.com/capabilities/decision)
- [System One API reference](https://docs.ollama.com/api/systemone)
- [Decision models available on Ollama](https://ollama.com/search?c=decision)

[Ollama](https://ollama.com) runs decision models, such as Nimble, Tev1 and Clef, locally on your machine, with no
API key and no cost per request. `OllamaDecisionModel` implements the [`DecisionModel`](/tutorials/decision-models)
API on top of Ollama's System One API (`POST /v1/systemone`), available in Ollama v0.35.0 or later.

:::note
This integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>1.23.0</version>
</dependency>
```

## Usage

Download a decision model first:

```shell
ollama pull nimble
```

Then:

```java
DecisionModel decisionModel = OllamaDecisionModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("nimble")
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
response.yesNo("urgent").probability();   // 0.97
```

The model name must be set, either on the builder or on each request. A model that has not been downloaded is
rejected by Ollama, and the request fails with a `ModelNotFoundException`.

Without a GPU, or while Ollama loads the model, a request can take several seconds, so consider a longer
`timeout(...)`. Ollama unloads a model after 5 minutes without requests (unless configured otherwise on the server):
to keep it loaded longer, set `keepAlive(...)` in seconds, or a negative value to keep it loaded. The other settings
are `httpClientBuilder`, `maxRetries`, `logRequests`, `logResponses`, `logger`, `customHeaders`, `customParameters`
(see [Custom parameters](#custom-parameters)) and `listeners`.

Both `decide()` and `decideAsync()` are supported.

## Custom parameters

To send a parameter that `OllamaDecisionModel` does not support yet, set it with `customParameters(...)`. It is added to
the JSON body of every request:

```java
DecisionModel decisionModel = OllamaDecisionModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("nimble")
        .customParameters(Map.of("parameter_name", "value"))
        .build();
```

To set custom parameters for a single request, use `OllamaDecisionRequestParameters`. If not empty, they
replace (are not merged with) the custom parameters set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input(...)
        .question(...)
        .parameters(OllamaDecisionRequestParameters.builder()
                .customParameters(Map.of("parameter_name", "another value"))
                .build())
        .build();
```

Values are written to JSON as they are, so a `Map` value becomes a JSON object and a `String` stays a string.
A custom parameter with the same name as a parameter that LangChain4j sends, such as `model`, replaces it. To choose
the model, use `modelName(...)`: a `model` custom parameter is not reflected in `modelName()` or in what listeners
receive. Answers are always read against the questions of the `DecisionRequest`, so do not replace `questions` or
`state`.

Passing the parameters class of another decision model to `OllamaDecisionModel` fails with an
`IllegalArgumentException`. `DecisionRequestParameters.builder()`, which has only the parameters common to all
decision models, works with every model.

## Input

The input can be:
- text (`input(...)`);
- named fields (`inputFields(...)`), which are sent as a JSON object;
- a list of `TextContent`s and `ImageContent`s (`inputContents(...)`), whose texts are joined with blank lines.

A conversation (`inputMessages(...)`) is sent as a [transcript](/tutorials/decision-models#conversations), as
`{"messages": [...]}`, with its images sent like the other images. The output guardrail sends the last 50 messages of the conversation by default:
with Ollama, set `maxMessages(...)` and `maxMessageLength(...)` on it, for example `maxMessages(4)` and
`maxMessageLength(4000)`, to help keep its requests under the 64 KiB limit. The chat model router, the query router
and tool filtering send up to 10 messages of up to 2,000 characters each. In all of them, the system message is sent
in full: with a long system message, or with text in scripts that take several bytes per character, lower these
limits or leave out messages with `messageFilter(...)`.

The whole input must fit in the context window the model is loaded with (it depends on the model and on the Ollama
server's configuration): Ollama never truncates it. The request body must also be at most 64 KiB, or 32 MiB with
images.

### Images

Models with vision, such as [Clef](https://ollama.com/library/clef) and [Clef Flash](https://ollama.com/library/clef-flash)
(Ollama v0.35.1 or later), can judge PNG, JPEG and WebP images:

```java
DecisionModel decisionModel = OllamaDecisionModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("clef-flash")
        .build();

DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(
                TextContent.from("The customer says the package arrived like this."),
                ImageContent.from(base64Image, "image/jpeg")))
        .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
        .build();
```

Ollama receives the images separately from the rest of the input, so each image is replaced in the input by its
position among the images: the input above is sent as
`The customer says the package arrived like this.` followed by `[image 1]`.
In named fields, an image is replaced the same way, so that its name says what it is:
`Map.of("comment", comment, "photo", ImageContent.from(...))` is sent as
`{"comment": "...", "photo": "[image 1]"}`. Images are numbered from 1 in the order they appear in the input, across
all fields, so questions can refer to them, for example "Does [image 2] show the same item as
[image 1]?". This `[image N]` convention comes from LangChain4j, not from Ollama.

Images can be given as base64 data, or by an `http`, `https` or `file` URL, in which case they are downloaded or read
by your application, on the calling thread (also for `decideAsync()`), before the request is sent. `data:` URLs are
not supported. Other contents, such as audio, are rejected with an `UnsupportedFeatureException` before calling
Ollama.

:::caution
Do not pass image URLs that come from untrusted sources, such as users, tools or retrieved content: your application
would download any `http` URL and read any local `file` URL, and send the content to Ollama. Download and check such
images yourself, and pass them as base64 data.
:::

## Questions and answers

| `DecisionModel` | Ollama | Notes |
|---|---|---|
| `YesNoQuestion` | `noul` | `yesWhen` and `noWhen` are sent as the criteria when both are set; a single one is appended to the question instead (for example, `Answer yes when: ...`) |
| `ChoiceQuestion` | `choice` | options are sent with their descriptions |
| `ScaleQuestion` | `score` | levels are sent as text, as `label: description` when they have a description |

Yes/no answers include the probability of "yes". Choice and scale answers include the probability of each option or
level, and a confidence.

A request can have at most 64 questions. A scale question can have 2 to 10 levels, and a choice question needs at least
2 options; the maximum number of options depends on the model (for example, 26 for Tev1).

Because of these limits, set `maxToolsPerDecisionRequest(64)` on the
[tool selection](/tutorials/decision-models#selecting-tools) components when there are more than 64 tools, and for
[re-ranking](/tutorials/decision-models#re-ranking-retrieved-content), lower `maxSegmentsPerRequest(...)` so that the
segments of one request fit in the context window.

`response.tokenUsage()` reports the input and output tokens counted by Ollama.

Whatever the model, `OllamaDecisionModel` reports `ModelProvider.OLLAMA` as its provider, for example in listeners
and metrics.

## Switching from `TypeSafeDecisionModel`

Ollama's decision models can also be called with [`TypeSafeDecisionModel`](/integrations/decision-models/typesafe)
and a custom base URL, but Ollama rejects some of its requests, for example scale levels with descriptions, and it
does not support images. To switch to `OllamaDecisionModel`, replace the class name: the base URL and the model name
stay the same. Listeners and metrics then report `OLLAMA` instead of `TYPESAFE` as the provider.
