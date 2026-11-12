---
sidebar_position: 5
---

# Google Gen AI

- [Gemini Decisions API documentation](https://aistudio.google.com/docs/decisions-api)
- [Google Gen AI Java SDK](https://github.com/googleapis/java-genai)

The Gemini Decisions API (`POST /v1beta/decisions`) evaluates yes/no, choice and scale questions against a shared
text, structured, media or conversation input in a single call, returning calibrated probabilities.
`GoogleGenAiDecisionModel` in `langchain4j-google-genai` implements the [`DecisionModel`](/tutorials/decision-models)
API using the official Google Gen AI Java SDK (`com.google.genai:google-genai`). The Gemini Decisions API is available
on the Gemini Developer API only, not on Vertex AI.

:::note
This integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-genai</artifactId>
    <version>1.23.0-beta33</version>
</dependency>
```

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Usage

```java
DecisionModel decisionModel = GoogleGenAiDecisionModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-decision-1")
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

The model name must be set, either on the builder or on each request (with `GoogleGenAiDecisionRequestParameters` or
`DecisionRequestParameters`). An unknown model fails with a `ModelNotFoundException`.

You can also pass a preconfigured `com.google.genai.Client` with `.client(client)` (set
`HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of())` on it, or `.maxRetries(0)` on the model builder,
to avoid stacking SDK and LangChain4j retries), or customize
`timeout`, `maxRetries`, `customHeaders`, `customParameters` (see [Custom parameters](#custom-parameters)),
`apiEndpoint`, `apiVersion`, `logRequests`, `logResponses`, and `listeners`. By default, each call is limited to
20 minutes (`timeout(...)`), and each retry gets the full timeout.
Both `decide()` and `decideAsync()` are supported.

## Input

The input can be:
- text (`input(...)`);
- named fields (`inputFields(...)`): each field is sent as a text block labeled with its name and JSON value
  (`comment: "..."`), and media contents right after a text block holding their name (`photo:`);
- a list of multimodal contents (`inputContents(...)`), supporting `TextContent`, `ImageContent`, `AudioContent`,
  `VideoContent`, and `PdfFileContent`;
- a conversation (`inputMessages(...)`, see [Conversations](#conversations)).

```java
DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(
                TextContent.from("The customer says the package arrived like this."),
                ImageContent.from(base64Image, "image/jpeg", ImageContent.DetailLevel.LOW)))
        .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
        .build();
```

Images, audio, video and PDF files can be given as:
- base64 data (or binary data on `Audio`);
- a `data:` URL, such as `data:image/png;base64,...`;
- a URL that Gemini reads itself: a publicly accessible `https` URL, or the URI of a file uploaded with
  `GoogleGenAiFiles`. LangChain4j does not download URLs or read local files: URLs are sent to Gemini as they are.

YouTube URLs, `gs://` URIs and local `file:` URIs are not accepted. Upload such a file with `GoogleGenAiFiles`, or give
it as a public or signed `https` URL, or as base64 data.

The MIME type is taken from the content, or guessed from the file extension of the URL when not set (`PdfFile` defaults
to `application/pdf`). Set the MIME type explicitly when the URL has no file extension, such as Files API URIs.

The detail level of an `ImageContent` is sent as its resolution (`low`, `medium`, `high`, `ultra_high`). An image
created without a detail level, or with `DetailLevel.AUTO`, uses Gemini's default resolution. Note that an
`ImageContent` deserialized from JSON (for example from persistent chat memory) keeps the stored detail level (`LOW`
when none was originally specified); create it with `DetailLevel.AUTO` to use Gemini's default resolution.

Gemini checks the limits of the API (such as the number of questions, options and levels, and the size of the input)
and rejects requests that exceed them with an `InvalidRequestException` carrying Gemini's validation message.

### Conversations

A conversation (a list of `ChatMessage`s set with `inputMessages(...)`) is sent as a
[JSON transcript](/tutorials/decision-models#conversations), followed by the images, audio, video and PDF files of the
conversation. The transcript refers to each media item by its kind and 1-based position among items of that kind (such
as `[image 1]`, `[audio 1]`, `[video 1]`, or `[pdf 1]`), and each item is sent right after a text block with that
marker (`[audio 1]:`).

An `AiMessage` with neither text nor tool execution requests is skipped, and a conversation with nothing left to send
is rejected with an `IllegalArgumentException` before calling the API. A `CustomMessage` is rejected with an
`UnsupportedFeatureException`.

## Questions and answers

| `DecisionModel` | Gemini Decisions API | Notes |
|---|---|---|
| `YesNoQuestion` | `noul` | `yesWhen` and `noWhen` map to `criteria.true_description` and `criteria.false_description` |
| `ChoiceQuestion` | `choice` | option names and descriptions map to `criteria` |
| `ScaleQuestion` | `score` | levels are sent as `label`, or `label: description` when they have a description |

Yes/no answers include the probability of "yes" (Gemini reports `noul` between `0.01` and `0.99`, so
`isYes(threshold)` with a threshold above `0.99` never returns `true`). Choice and scale answers include calibrated
probabilities for each option or level, as well as `confidence()`.

Gemini sorts the options of a choice question by their names (in UTF-8 byte order) before evaluating them, so the order
of `option(...)` calls does not affect the result.

Question names can be any text: each question is sent under a positional key (`q0`, `q1`, ...) and its answer is
mapped back to its original name.

## Blocked input

Gemini can block the whole input when it violates safety or content policies. When that happens, no question is
answered: the answer to every question is a `RefusalAnswer`, `response.isRefused(name)` returns `true`, and reading an
answer (for example with `response.yesNo(name)`) throws a `ContentFilteredException`
(see [Refusals](/tutorials/decision-models#refusals)).

The metadata of every response is a `GoogleGenAiDecisionResponseMetadata`, which exposes the block reason and message
when the input was blocked:

```java
DecisionResponse response = decisionModel.decide(request);

if (response.isRefused("urgent")) {
    GoogleGenAiDecisionResponseMetadata metadata = (GoogleGenAiDecisionResponseMetadata) response.metadata();
    String reason = metadata.blockReason();   // for example "safety"
    String message = metadata.blockMessage(); // Gemini's explanation, or null if it gave none
}
```

When the input was not blocked, `blockReason()` and `blockMessage()` return `null`.

## Custom parameters

To send a parameter that `GoogleGenAiDecisionModel` does not support yet, set it with `customParameters(...)`. It is
added to the JSON body of every request:

```java
DecisionModel decisionModel = GoogleGenAiDecisionModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-decision-1")
        .customParameters(Map.of("parameter_name", "value"))
        .build();
```

To set custom parameters for a single request, use `GoogleGenAiDecisionRequestParameters`. If not empty, they replace
(are not merged with) the custom parameters set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input("Help! My payouts have been failing for 3 days.")
        .question("urgent", YesNoQuestion.from("Does this need attention today?"))
        .parameters(GoogleGenAiDecisionRequestParameters.builder()
                .customParameters(Map.of("parameter_name", "another value"))
                .build())
        .build();
```

Values are written to JSON as they are, so a `Map` value becomes a JSON object and a `String` stays a string.
`model`, `input` and `questions` are set by `GoogleGenAiDecisionModel` and cannot be custom parameters: such a request
fails with an `IllegalArgumentException` before anything is sent.

## Token usage

`response.tokenUsage()` is a `GoogleGenAiTokenUsage` with the input, output and total token counts reported by Gemini.
Gemini counts input tokens only, so the output token count is 0. When Gemini rejects blocked input with an error (see
[Blocked input](#blocked-input)), the response has no token usage.

```java
GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) response.tokenUsage();
Integer inputTokens = tokenUsage.inputTokenCount();
```
