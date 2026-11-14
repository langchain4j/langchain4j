---
sidebar_position: 4
---

# Google GenAI

- [Gemini Decisions API reference](https://ai.google.dev/api/decisions)

The Gemini Decisions API answers yes/no, choice and scale questions about a text, image, PDF, audio, video or
conversation input, with calibrated probabilities. `GoogleGenAiDecisionModel` in `langchain4j-google-genai`
implements the [`DecisionModel`](/tutorials/decision-models) API with the official Google GenAI Java SDK.

:::note
This integration is experimental and may change in future releases. The Gemini Decisions API is in beta.
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-genai</artifactId>
    <version>1.23.0-beta33</version>
</dependency>
```

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
response.yesNo("urgent").probability();   // 0.98
```

It has the same client settings as the other models of the module, for example `apiEndpoint`, `timeout`,
`customHeaders`, `logRequests` and `logResponses`. `maxRetries` (3 by default) is the number of times the SDK retries
rate limits and server errors, with exponential backoff.

The Decisions API is served by the Gemini API under `v1beta`, and is not available on Vertex AI. If you pass your own
`Client` with `client(...)`, build it with `HttpOptions.builder().apiVersion("v1beta")`, otherwise the API answers with
HTTP 404.

## Input

The input can be:
- text (`input(...)`);
- named fields (`inputFields(...)`): each field is sent as a text part labeled with its name, with the value as JSON
  (`comment: "..."`), and contents such as images right after a text part holding their name (`photo:`);
- a list of contents (`inputContents(...)`): `TextContent`, `ImageContent`, `PdfFileContent`, `AudioContent` and
  `VideoContent`, for example a photo together with a description:

```java
DecisionRequest request = DecisionRequest.builder()
        .inputContents(List.of(
                TextContent.from("The customer says the package arrived like this."),
                ImageContent.from(base64Image, "image/jpeg")))
        .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
        .build();
```

Images, PDFs, audio and video can be base64 data (with their MIME type), the URI of a file uploaded with the
[Gemini File API](/integrations/language-models/google-genai#file-api), or a publicly accessible `https` URL, which Gemini
downloads itself. The detail level of an image is sent as its resolution: `LOW`, `MEDIUM`, `HIGH` and `ULTRA_HIGH`
map to the resolutions of the same name, and `AUTO` leaves the resolution to the model. A higher resolution uses more
input tokens.

The input can also be a conversation, a list of `ChatMessage`s set with `inputMessages(...)`. It is sent as a
[transcript in JSON](/tutorials/decision-models#conversations), followed by the images of the conversation, each after
a text part with its marker (`[image 1]:`).

## Questions and answers

| `DecisionModel` | Gemini | Notes |
|---|---|---|
| `YesNoQuestion` | `noul` | `yesWhen` and `noWhen` are sent as the descriptions of the true and false answers |
| `ChoiceQuestion` | `choice` | option names are sent with their descriptions, from 2 to 255 options |
| `ScaleQuestion` | `score` | each level is sent as `label: description`, from 2 to 255 levels |

Choice and scale answers include the probability of each option or level, and a confidence. The mean of a scale
answer is the probability-weighted mean of the level indexes, from 0 to the number of levels minus 1.

If Gemini blocks the input, for example for safety reasons, every question is answered with a refusal:
`response.isRefused(name)` returns `true` (see [Refusals](/tutorials/decision-models#refusals)).

## Token usage

`response.tokenUsage()` is a `GoogleGenAiTokenUsage`, which also includes the number of cached input tokens:

```java
GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) response.tokenUsage();
Integer cachedTokens = tokenUsage.cachedContentTokenCount();
```
