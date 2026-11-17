---
sidebar_position: 4
---

# Google AI Gemini

- [Gemini Decisions API documentation](https://aistudio.google.com/docs/decisions-api)

The Gemini Decisions API (`POST /v1beta/decisions` of the Gemini Developer API) answers yes/no, choice and scale
questions about a text, structured, media or conversation input, with probabilities. Media can be images, audio,
video and PDF files. `GoogleAiGeminiDecisionModel` in `langchain4j-google-ai-gemini` implements the
[`DecisionModel`](/tutorials/decision-models) API on top of it.

:::note
This integration is experimental and may change in future releases.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>1.23.0</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Usage

```java
DecisionModel decisionModel = GoogleAiGeminiDecisionModel.builder()
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
response.yesNo("urgent").probability();   // 0.95
```

The model name must be set, either on the builder or on each request. An unknown model is rejected by Gemini, and
the request fails with a `ModelNotFoundException`.

The other settings are `baseUrl` (by default `https://generativelanguage.googleapis.com/v1beta`), `timeout`,
`maxRetries`, `logRequests`, `logResponses`, `logger`, `httpClientBuilder`, `customHeaders`, `customParameters`
(see [Custom parameters](#custom-parameters)) and `listeners`.

Both `decide()` and `decideAsync()` are supported.

## Question names

Gemini accepts only question names made of the characters `A-Z`, `a-z`, `0-9`, `_` and `-`, from 1 to 64 characters
long, for example `urgent` or `prompt_injection`. LangChain4j sends the names as they are, and Gemini rejects a request
with another name, such as `prompt injection`, with an `InvalidRequestException`. This applies to every question name,
including the names of the checks of [guardrails](/tutorials/decision-models#guardrails).

## Input

The input can be:
- text (`input(...)`);
- named fields (`inputFields(...)`): each field is sent as a text block labeled with its name, with the value as JSON
  (`comment: "..."`), and each content, such as an image, right after a text block holding its name (`photo:`);
- a list of `TextContent`s, `ImageContent`s, `AudioContent`s, `VideoContent`s and `PdfFileContent`s
  (`inputContents(...)`);
- a conversation (`inputMessages(...)`, see [Conversations](#conversations)).

For example, a voice message together with the receipt it talks about:

```java
DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(
                TextContent.from("The customer left this voice message and attached the receipt."),
                AudioContent.from(base64Audio, "audio/mp3"),
                PdfFileContent.from(base64Pdf, "application/pdf")))
        .question("refund", YesNoQuestion.builder()
                .text("Does the customer ask for a refund?")
                .yesWhen("They ask for their money back.")
                .build())
        .build();
```

Images, audio, video and PDF files can be given as:
- base64 data, as above;
- a `data:` URL, such as `data:image/png;base64,...`;
- a URL that Gemini reads itself: a publicly accessible `https` URL, or the URI of a file uploaded with the
  [Gemini Files API](/integrations/language-models/google-ai-gemini#gemini-files-api). LangChain4j does not download
  URLs: they are sent to Gemini as they are.

The Files API is useful for large files:

```java
GeminiFiles files = GeminiFiles.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .build();

GeminiFile recording = files.uploadFile(Path.of("call.mp3"), "Customer call");

DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(AudioContent.from(Audio.builder()
                .url(recording.uri())
                .mimeType(recording.mimeType())
                .build())))
        .question("angry", YesNoQuestion.from("Is the caller angry?"))
        .build();
```

A large file, such as a video, can take a while to be processed after the upload: wait until it is active (see
[File States](/integrations/language-models/google-ai-gemini#file-states)) before using it.

The MIME type of each content is the one set on it. When it is not set and the content has a URL, it is guessed from
the file extension of the URL, and a PDF file without a MIME type is sent as `application/pdf`. Set the MIME type
when the URL has no file extension, as for Files API URIs.

The detail level of an `ImageContent` is sent as its resolution: `LOW`, `MEDIUM`, `HIGH` and `ULTRA_HIGH` become
`low`, `medium`, `high` and `ultra_high`. An image created without a detail level, or with `AUTO`, gets Gemini's
default resolution.

Gemini checks the limits of the API, such as the number of questions, options and levels, and the size of the input,
and rejects a request that exceeds them with an `InvalidRequestException` that holds Gemini's message. See the
[Gemini Decisions API documentation](https://aistudio.google.com/docs/decisions-api) for the limits.

### Conversations

A conversation (a list of `ChatMessage`s set with `inputMessages(...)`) is sent as a
[transcript in JSON](/tutorials/decision-models#conversations), followed by the images, audio, video and PDF files of
the conversation. The transcript refers to each of them by its kind and its position among the contents of that kind,
for example `[image 1]`, `[audio 1]` or `[pdf 2]`, and each one is sent right after a text block with that marker
(`[audio 1]:`). The Gemini Decisions API takes no conversation turns, so this is how it sees who said what.

An `AiMessage` with neither text nor tool execution requests is skipped, and a conversation with nothing left to send
is rejected with an `IllegalArgumentException` before calling the API. A `CustomMessage` is rejected with an
`UnsupportedFeatureException`.

## Questions and answers

| `DecisionModel` | Gemini | Notes |
|---|---|---|
| `YesNoQuestion` | `noul` | `yesWhen` and `noWhen` are sent as the criteria (`true_description` and `false_description`) |
| `ChoiceQuestion` | `choice` | options are sent with their descriptions |
| `ScaleQuestion` | `score` | levels are sent as text, as `label: description` when they have a description |

Yes/no answers include the probability of "yes". Choice and scale answers include the probability of each option or
level, and a confidence.

## Blocked input

Gemini can block the whole input, for example because it goes against its safety policies. Then no question is
answered: the answer to every question is a refusal, `response.isRefused(name)` returns `true`, and reading the
answer, for example with `response.yesNo(name)`, throws a `ContentFilteredException`
(see [Refusals](/tutorials/decision-models#refusals)). Such a response has token usage only when Gemini reports it.

## Custom parameters

To send a parameter that `GoogleAiGeminiDecisionModel` does not support yet, set it with `customParameters(...)`. It
is added to the JSON body of every request:

```java
DecisionModel decisionModel = GoogleAiGeminiDecisionModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-decision-1")
        .customParameters(Map.of("parameter_name", "value"))
        .build();
```

To set custom parameters for a single request, use `GoogleAiGeminiDecisionRequestParameters`. If not empty, they
replace (are not merged with) the custom parameters set on the builder:

```java
DecisionRequest request = DecisionRequest.builder()
        .input("Help! My payouts have been failing for 3 days.")
        .question("urgent", YesNoQuestion.from("Does this need attention today?"))
        .parameters(GoogleAiGeminiDecisionRequestParameters.builder()
                .customParameters(Map.of("parameter_name", "another value"))
                .build())
        .build();
```

Values are written to JSON as they are, so a `Map` value becomes a JSON object and a `String` stays a string.
A custom parameter with the same name as a parameter that LangChain4j sends, such as `model`, replaces it. To choose
the model, use `modelName(...)`: a `model` custom parameter is not reflected in `modelName()` or in what listeners
receive. Answers are always read against the questions of the `DecisionRequest`, so do not replace `questions` or
`input`.

Passing the parameters class of another decision model to `GoogleAiGeminiDecisionModel` fails with an
`IllegalArgumentException`. `DecisionRequestParameters.builder()`, which has only the parameters common to all
decision models, works with every model.

## Token usage

`response.tokenUsage()` is a `GoogleAiGeminiTokenUsage` with the input, output and total token counts reported by
Gemini. Gemini counts input tokens only, so the output token count is 0.
