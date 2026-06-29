---
sidebar_position: 7
---

# Google AI Gemini

https://ai.google.dev/gemini-api/docs

## Table of Contents

- [Maven Dependency](#maven-dependency)
- [API Key](#api-key)
- [Models Available](#models-available)
- [GoogleAiGeminiChatModel](#googleaigeminichatmodel)
    - [Configuring](#configuring)
    - [Default Request Parameters](#default-request-parameters)
- [GoogleAiGeminiStreamingChatModel](#googleaigeministreamingchatmodel)
- [Tools](#tools)
- [Structured Outputs](#structured-outputs)
- [Python Code Execution](#python-code-execution)
- [Multimodality](#multimodality)
- [Thinking](#thinking)
    - [Gemini 3 Pro](#gemini-3-pro)
- [Gemini Files API](#gemini-files-api)
    - [Uploading Files](#uploading-files)
    - [Managing Files](#managing-files)
    - [File States](#file-states)
- [Batch Processing](#batch-processing)
    - [GoogleAiBatchChatModel](#googleaibatchchatmodel)
    - [Creating Batch Jobs](#creating-batch-jobs)
    - [Handling Batch Responses](#handling-batch-responses)
    - [Polling for Results](#polling-for-results)
    - [Managing Batch Jobs](#managing-batch-jobs)
    - [File-Based Batch Processing](#file-based-batch-processing)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>1.17.0</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Models available

Check the list of [available models](https://ai.google.dev/gemini-api/docs/models/gemini) in the documentation.

* `gemini-3-pro-preview`
* `gemini-2.5-pro`
* `gemini-2.5-flash`
* `gemini-2.5-flash-lite`
* `gemini-2.0-flash`
* `gemini-2.0-flash-lite`

## GoogleAiGeminiChatModel

The usual `chat(...)` methods are available:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    ...
    .build();

String response = gemini.chat("Hello Gemini!");
```

As well, as the `ChatResponse chat(ChatRequest req)` method:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

ChatResponse chatResponse = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from(
        "How many R's are there in the word 'strawberry'?"))
    .build());

String response = chatResponse.aiMessage().text();
```

### Configuring

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .httpClientBuilder(...)
    .defaultRequestParameters(...)
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .baseUrl(...)
    .modelName("gemini-2.5-flash")
    .maxRetries(...)
    .temperature(1.0)
    .topP(0.95)
    .topK(64)
    .seed(42)
    .frequencyPenalty(...)
    .presencePenalty(...)
    .maxOutputTokens(8192)
    .timeout(Duration.ofSeconds(60))
    .responseFormat(ResponseFormat.JSON) // or .responseFormat(ResponseFormat.builder()...build()) 
    .stopSequences(List.of(...))
    .toolConfig(GeminiFunctionCallingConfig.builder()...build()) // or below
    .toolConfig(GeminiMode.ANY, List.of("fnOne", "fnTwo"))
    .allowCodeExecution(true)
    .includeCodeExecution(true)
    .logRequestsAndResponses(true)
    .safetySettings(List<GeminiSafetySetting> or Map<GeminiHarmCategory, GeminiHarmBlockThreshold>)
    .thinkingConfig(...)
    .returnThinking(true)
    .sendThinking(true)
    .responseLogprobs(...)
    .logprobs(...)
    .enableEnhancedCivicAnswers(...)
    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH)
    .mediaResolutionPerPartEnabled(true)
    .listeners(...)
    .supportedCapabilities(...)
    .build();
```

### Default Request Parameters

Instead of (or in addition to) the individual builder methods shown above, you can supply a single
`ChatRequestParameters` object via `defaultRequestParameters(...)`. These parameters are applied to every
request issued by the model, unless they are overridden by the parameters of an individual `ChatRequest`.

You can pass either common `ChatRequestParameters` or Gemini-specific `GoogleAiGeminiChatRequestParameters`.
The latter additionally exposes Gemini-only options such as `aspectRatio` and `imageSize`:

```java
GoogleAiGeminiChatRequestParameters parameters = GoogleAiGeminiChatRequestParameters.builder()
    .modelName("gemini-2.5-flash")
    .temperature(1.0)
    .maxOutputTokens(8192)
    .aspectRatio("16:9") // Gemini-specific
    .imageSize("2K")     // Gemini-specific
    .build();

ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .defaultRequestParameters(parameters)
    .build();
```

When the same parameter is set both via `defaultRequestParameters(...)` and via an individual builder method
(e.g., `modelName(String)`), the value set via the individual builder method takes precedence:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .defaultRequestParameters(GoogleAiGeminiChatRequestParameters.builder()
        .modelName("gemini-2.5-flash")
        .temperature(1.0)
        .build())
    .temperature(0.0) // overrides temperature from defaultRequestParameters
    .build();
// effective parameters: modelName=gemini-2.5-flash, temperature=0.0
```

## GoogleAiGeminiStreamingChatModel
The `GoogleAiGeminiStreamingChatModel` allows streaming the text of a response token by token.
The response must be handled by a `StreamingChatResponseHandler`.
```java
StreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-2.5-flash")
        .build();

CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

gemini.chat("Tell me a joke about Java", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        futureResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }
});

        futureResponse.join();
```

## Tools

Tools (aka Function Calling) is supported, including parallel calls.
You can either use the `chat(ChatRequest)` method that accepts a `ChatRequest` that can be configured with
one or more `ToolSpecification`s to let Gemini know it can request a function to be called.
Or you can use LangChain4j's `AiServices` to define them.

Here is an example of a weather tool, using `AiServices`:

```java
record WeatherForecast(
    String location,
    String forecast,
    int temperature) {}

class WeatherForecastService {
    @Tool("Get the weather forecast for a location")
    WeatherForecast getForecast(
        @P("Location to get the forecast for") String location) {
        if (location.equals("Paris")) {
            return new WeatherForecast("Paris", "sunny", 20);
        } else if (location.equals("London")) {
            return new WeatherForecast("London", "rainy", 15);
        } else if (location.equals("Tokyo")) {
            return new WeatherForecast("Tokyo", "warm", 32);
        } else {
            return new WeatherForecast("Unknown", "unknown", 0);
        }
    }
}

interface WeatherAssistant {
    String chat(String userMessage);
}

WeatherForecastService weatherForecastService =
    new WeatherForecastService();

ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .temperature(0.0)
    .build();

WeatherAssistant weatherAssistant =
    AiServices.builder(WeatherAssistant.class)
        .chatModel(gemini)
        .tools(weatherForecastService)
        .build();

String tokyoWeather = weatherAssistant.chat(
        "What is the weather forecast for Tokyo?");

System.out.println("Gemini> " + tokyoWeather);
// Gemini> The weather forecast for Tokyo is warm
//         with a temperature of 32 degrees.
```

## Structured Outputs

See more info on Structured Outputs [here](/tutorials/structured-outputs).

### Type-safe data extraction from free form text
Large Language Models are great at extracting structured information out of unstructured text.
In the following example, we retrieve a type-safe `WeatherForecast` object from a weather forecast text, thanks to `AiServices`:
```java
// A type-safe / strongly-typed object 
// representing the weather forecast

record WeatherForecast(
    @Description("minimum temperature")
    Integer minTemperature,
    @Description("maximum temperature")
    Integer maxTemperature,
    @Description("chances of rain")
    boolean rain
) { }

// An interface contract, to interact with Gemini

interface WeatherForecastAssistant {
    WeatherForecast extract(String forecast);
}

// Let's extract the data:

ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // this is required to enable structured outputs feature
    .build();

WeatherForecastAssistant forecastAssistant =
    AiServices.builder(WeatherForecastAssistant.class)
        .chatModel(gemini)
        .build();

WeatherForecast forecast = forecastAssistant.extract("""
    Morning: The day dawns bright and clear in Osaka, with crisp
    autumn air and sunny skies. Expect temperatures to hover
    around 18°C (64°F) as you head out for your morning stroll
    through Namba.
    Afternoon: The sun continues to shine as the city buzzes with
    activity. Temperatures climb to a comfortable 22°C (72°F).
    Enjoy a leisurely lunch at one of Osaka's many outdoor cafes,
    or take a boat ride on the Okawa River to soak in the beautiful
    scenery.
    Evening: As the day fades, expect clear skies and a slight chill
    in the air. Temperatures drop to 15°C (59°F). A cozy dinner at a
    traditional Izakaya will be the perfect way to end your day in
    Osaka.
    Overall: A beautiful autumn day in Osaka awaits, perfect for
    exploring the city's vibrant streets, enjoying the local cuisine,
    and soaking in the sights.
    Don't forget: Pack a light jacket for the evening and wear
    comfortable shoes for all the walking you'll be doing.
    """);
```

### Response Format / Response Schema
You can specify a `ResponseFormat` either when creating a `GoogleAiGeminiChatModel` or when calling it.

Especially, in cases of Json format, you can choose to define schema programmatically by creating the respective java objects or by providing raw json schema. 
#### Response Schema
Let's have a look at an example to define a JSON schema for a recipe when creating the `GoogleAiGeminiChatModel`.
In this example we declare the json schema using `JsonObjectSchema` class.
```java
ResponseFormat responseFormat = ResponseFormat.builder()
        .type(ResponseFormatType.JSON)
        .jsonSchema(JsonSchema.builder() // see [1] below
                .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("title")
                        .addIntegerProperty("preparationTimeMinutes")
                        .addProperty("ingredients", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .build())
                        .addProperty("steps", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .build())
                        .build())
                .build())
        .build();

ChatModel gemini = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-2.5-flash")
        .responseFormat(responseFormat)
        .build();

String recipeResponse = gemini.chat("Suggest a dessert recipe with strawberries");

System.out.println(recipeResponse);
```
Notes:
- [1] - The `JsonSchema` can be generated automatically from your class using `JsonSchemas.jsonSchemaFrom()` helper method.
```java
JsonSchema jsonSchema = JsonSchemas.jsonSchemaFrom(TripItinerary.class).get();
```

Let's have a look at an example to define a JSON schema for a recipe when calling the `GoogleAiGeminiChatModel`:
```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-2.5-flash")
        .build();

ResponseFormat responseFormat = ...;

ChatRequest chatRequest = ChatRequest.builder()
        .messages(UserMessage.from("Suggest a dessert recipe with strawberries"))
        .responseFormat(responseFormat)
        .build();

ChatResponse chatResponse = gemini.chat(chatRequest);

System.out.println(chatResponse.aiMessage().text());
```

#### Raw Response Schema
Another example shows how we can use the `responseJsonSchema` of the Gemini API to provide a raw JSON schema using `JsonRawSchema` class.  
Please be cautious to use only the [supported types](https://ai.google.dev/gemini-api/docs/structured-output?example=recipe#json_schema_support) of the Gemini API.
```
String rawSchema = """
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string"
    },
    "birthDate": {
      "type": "string",
      "format": "date"
    },
    "preferredContactTime": {
      "type": "string",
      "format": "time"
      },
    "height": {
      "type": "number",
      "minimum": 1.83,
      "maximum": 1.88
    },
    "role": {
      "type": "string",
      "enum": ["developer", "maintainer", "researcher"]
    },
    "isAvailable": { "type": "boolean" },
    "tags": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "minItems": 1,
      "maxItems": 5
    },
    "address": {
      "type": "object",
      "properties": {
        "city": { "type": "string" },
        "streetName": { "type": "string" },
        "streetNumber": { "type": "string" }
      },
      "required": ["city", "streetName", "streetNumber"],
      "additionalProperties": true
    }
  },
  "required": ["name", "birthDate", "height", "role", "tags", "address"]
}
""";

JsonRawSchema jsonRawSchema = JsonRawSchema.builder().schema(rawSchema).build();
JsonSchema jsonSchema = JsonSchema.builder().rootElement(jsonRawSchema).build();
        
ResponseFormat responseFormat = ResponseFormat.builder()
        .type(ResponseFormatType.JSON)
        .jsonSchema(jsonSchema)
        .build();

GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
        .apiKey(GOOGLE_AI_GEMINI_API_KEY)
        .modelName("gemini-2.5-flash-lite")
        .logRequests(true)
        .logResponses(true)
        .responseFormat(responseFormat)
        .build();
        
UserMessage userMessage = UserMessage.from(
        """
           Tell me about a detective named Sherlock Holmes,
           who was born on November 28 1852 and sees the world over six feet from the ground.
           He is a trouble-seeker, an active volunteer and lives in London at 221B Baker Street.
           He plays the violin and he likes to conduct various physics and chemistry experiments.
           He accepts clients or prefers to be contacted at 09:00am.
           """);

ChatResponse response = gemini.chat(ChatRequest.builder()
        .messages(userMessage)
        .build());
```
### JSON Mode

You can force Gemini to reply in JSON:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .responseFormat(ResponseFormat.JSON)
    .build();

String roll = gemini.chat("Roll a 6-sided dice");

System.out.println(roll);
// {"roll": "3"}
```

A system prompt can further describe what the JSON output should look like.
Gemini normally follows the suggested schema, but it is not guaranteed.
If you want a guaranteed application of a JSON schema, you should define a response format, as explained in the previous section.


## Python code execution

Beyond function calling, Google AI Gemini allows you to create and execute Python code in a sandboxed environment.
This is particularly interesting for situations where more advanced calculations or logic is needed.

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .allowCodeExecution(true)
    .includeCodeExecutionOutput(true)
    .build();
```

There are 2 builder methods:
* `allowCodeExecution(true)`: to let Gemini know it can do some Python coding
* `includeCodeExecutionOutput(true)`: if you want to see the actual Python script it came up with, and the output of its execution

```java
ChatResponse mathQuizz = gemini.chat(
    SystemMessage.from("""
        You are an expert mathematician.
        When asked a math problem or logic problem,
        you can solve it by creating a Python program,
        and execute it to return the result.
        """),
    UserMessage.from("""
        Implement the Fibonacci and Ackermann functions.
        What is the result of `fibonacci(22)` - ackermann(3, 4)?
        """)
);
```

Gemini will craft a Python script, execute it on its server, and return the result.
Since we asked to see the code and output of the execution, the answer will look as follows:

~~~
Code executed:
```python
def fibonacci(n):
    if n <= 1:
        return n
    else:
        return fibonacci(n-1) + fibonacci(n-2)

def ackermann(m, n):
    if m == 0:
        return n + 1
    elif n == 0:
        return ackermann(m - 1, 1)
    else:
        return ackermann(m - 1, ackermann(m, n - 1))

print(fibonacci(22) - ackermann(3, 4))
```
Output:
```
17586
```
The result of `fibonacci(22) - ackermann(3, 4)` is **17586**.

I implemented the Fibonacci and Ackermann functions in Python.
Then I called `fibonacci(22) - ackermann(3, 4)` and printed the result.
~~~

If we hadn't asked for the code / output, we would have received only the following text:

```
The result of `fibonacci(22) - ackermann(3, 4)` is **17586**.

I implemented the Fibonacci and Ackermann functions in Python.
Then I called `fibonacci(22) - ackermann(3, 4)` and printed the result.
```

## Multimodality

Gemini is a multimodal model, which means it can both accept and generate different _modalities_ besides text.

### Input Modalities

In input, Gemini accepts:
* pictures (`ImageContent`)
* videos (`VideoContent`)
* audio files (`AudioContent`)
* PDF files (`PdfFileContent`)

The example below shows how to mix a text prompt with an image:

```java
// PNG of the cute colorful parrot mascot of the LangChain4j project
String base64Img = b64encoder.encodeToString(readBytes(
  "https://avatars.githubusercontent.com/u/132277850?v=4"));

ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

ChatResponse response = gemini.chat(
    UserMessage.from(
        ImageContent.from(base64Img, "image/png"),
        TextContent.from("""
            Do you think this logo fits well
            with the project description?
            """)
    )
);
```

### Image Generation Output

Some Gemini models (such as `gemini-2.5-flash-image`) can generate images as part of their response. When images are generated, they are stored in the `AiMessage` attributes and can be accessed using the `GeneratedImageHelper` utility class.

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey("Your API Key")
    .modelName("gemini-2.5-flash-image")
    .build();

ChatResponse response = gemini.chat(UserMessage.from("A high-resolution, studio-lit product photograph of a minimalist ceramic coffee mug in matte black"));

// Extract generated images from the response
AiMessage aiMessage = response.aiMessage();
List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(aiMessage);

if (GeneratedImageHelper.hasGeneratedImages(aiMessage)) {
    System.out.println("Generated " + generatedImages.size() + " image(s)");
    System.out.println("Text response: " + aiMessage.text());

    for (Image image : generatedImages) {
        String base64Data = image.base64Data();
        String mimeType = image.mimeType();
        
        // You can now save the image, display it, or process it further
        // For example, save to file:
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        Files.write(Paths.get("generated_image.png"), imageBytes);
    }
} else {
    System.out.println("Text response: " + aiMessage.text());
}
```

### Media Resolution

You can control the resolution of media (images, videos, PDFs) sent to the model. This can be done globally or per-part (per image).

#### Global Media Resolution

To set the media resolution for all media parts in a request, use the `.mediaResolution()` builder method:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW) // or MEDIUM, HIGH, ULTRA_HIGH, UNSPECIFIED
    .build();
```

#### Per-Part Media Resolution (Gemini 3)

With Gemini 3, you can specify the resolution for individual images using the `DetailLevel` in `ImageContent`.
First, enable this feature in the builder, then set the detail level on `ImageContent`:

```java
ChatModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-3-pro-preview")
    .mediaResolutionPerPartEnabled(true)
    .build();

ChatResponse response = gemini.chat(
    UserMessage.from(
        ImageContent.from(url1, ImageContent.DetailLevel.LOW),
        ImageContent.from(url2, ImageContent.DetailLevel.HIGH),
        TextContent.from("Compare these two images")
    )
);
```

Supported `DetailLevel` values and their mapping to Gemini's resolution levels:
- `LOW` -> `MEDIA_RESOLUTION_LOW`
- `MEDIUM` -> `MEDIA_RESOLUTION_MEDIUM`
- `HIGH` -> `MEDIA_RESOLUTION_HIGH`
- `ULTRA_HIGH` -> `MEDIA_RESOLUTION_ULTRA_HIGH` (Highest token count, required for specific use cases such as computer use)
- `AUTO` -> `MEDIA_RESOLUTION_UNSPECIFIED`

## Thinking

Both `GoogleAiGeminiChatModel` and `GoogleAiGeminiStreamingChatModel`
support [thinking](https://ai.google.dev/gemini-api/docs/thinking).

The following parameters also control thinking behaviour:
- `GeminiThinkingConfig.includeThoughts` and `thinkingBudget`: enables thinking, see more details [here](https://ai.google.dev/gemini-api/docs/thinking).
- `returnThinking`: controls whether to return thinking (if available) inside `AiMessage.thinking()`
  and whether to invoke `StreamingChatResponseHandler.onPartialThinking()` and `TokenStream.onPartialThinking()`
  callbacks when using `GoogleAiGeminiStreamingChatModel`.
  Disabled by default. If enabled, tinking signatures will also be stored and returned inside the `AiMessage.attributes()`.
- `sendThinking`: controls whether to send thinking and signatures stored in `AiMessage` to the LLM in follow-up requests.
- Disabled by default.

:::note
Please note that when `returnThinking` is not set (is `null`) and `thinkingConfig` is set,
thinking text will be prepended to the actual response inside the `AiMessage.text()` field
and `StreamingChatResponseHandler.onPartialResponse()` will be invoked
instead of `StreamingChatResponseHandler.onPartialThinking()`.
:::

Here is an example of how to configure thinking:
```java
GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
        .includeThoughts(true)
        .thinkingBudget(250)
        .build();

ChatModel model = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-2.5-flash")
        .thinkingConfig(thinkingConfig)
        .returnThinking(true)
        .sendThinking(true)
        .build();
```

### Gemini 3 Pro

With Gemini 3 Pro, the thinking configuration introduces a _thinking level_, either `"low"` or `"high"` (high being the default).
It's possible to set the level within the thinking configuration:
```java
GoogleAiGeminiChatModel modelHigh = GoogleAiGeminiChatModel.builder()
        .modelName("gemini-3-pro-preview")
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .thinkingConfig(GeminiThinkingConfig.builder()
                .thinkingLevel(LOW) // or HIGH
                .build())
        .sendThinking(true)
        .returnThinking(true)
        .build();
```

You can pass either a string `"high"` / `"low"` or a `GeminiThinkingConfig.GeminiThinkingLevel.HIGH`
/ `GeminiThinkingConfig.GeminiThinkingLevel.LOW` enum value.

When using Gemini 3 Pro, it's mandatory to configure `sendThinking()` and `returnThinking()` to `true`,
to ensure [thought signatures](https://ai.google.dev/gemini-api/docs/thought-signatures) are properly passed around to the model.

## Gemini Files API

The Gemini Files API allows you to upload and manage media files for use with Gemini models. This is particularly useful when your total request size exceeds 20 MB, as files can be uploaded separately and referenced in your content generation requests.

### Key Features

- **Multimodal Support**: Upload images, audio, videos, and documents
- **Storage**: Files are stored for 48 hours
- **Capacity**: Up to 20 GB of files per project, with a maximum of 2 GB per individual file
- **No Cost**: The Files API is available at no charge

### Uploading Files

You can upload files in two ways:

**From a file path:**

```java
GeminiFiles filesApi = GeminiFiles.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .build();

// Upload from a file path
Path filePath = Paths.get("path/to/your/file.pdf");
GeminiFile uploadedFile = filesApi.uploadFile(filePath, "My Document");

System.out.println("File uploaded: " + uploadedFile.name());
System.out.println("File URI: " + uploadedFile.uri());
```

**From a byte array:**

```java
byte[] fileBytes = Files.readAllBytes(Paths.get("path/to/file.jpg"));
GeminiFile uploadedFile = filesApi.uploadFile(
    fileBytes,
    "image/jpeg",
    "My Image"
);
```

### Managing Files

**List all uploaded files:**

```java
List<GeminiFile> files = filesApi.listFiles();
for (GeminiFile file : files) {
    System.out.println("File: " + file.displayName() + " (" + file.name() + ")");
}
```

**Get file metadata:**

```java
GeminiFile file = filesApi.getMetadata("files/abc123");
System.out.println("File size: " + file.sizeBytes() + " bytes");
System.out.println("MIME type: " + file.mimeType());
System.out.println("Created: " + file.createTime());
System.out.println("Expires: " + file.expirationTime());
```

**Delete a file:**

```java
filesApi.deleteFile("files/abc123");
System.out.println("File deleted successfully");
```

### File States

Files can be in different states during their lifecycle:

```java
GeminiFile file = filesApi.getMetadata("files/abc123");

if (file.isActive()) {
    System.out.println("File is ready to use");
} else if (file.isProcessing()) {
    System.out.println("File is still being processed");
} else if (file.isFailed()) {
    System.out.println("File processing failed");
}
```

## Batch Processing

### GoogleAiBatchChatModel

The `GoogleAiBatchChatModel` provides an interface for processing large volumes of chat requests asynchronously at a reduced cost [(50% of standard pricing)](https://ai.google.dev/gemini-api/docs/batch-api). It is ideal for non-urgent, large-scale tasks with a 24-hour turnaround SLO.

### Creating Batch Jobs

**Inline batch creation:**

```java
GoogleAiGeminiBatchChatModel batchModel = GoogleAiGeminiBatchChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

// Create batch requests
List<ChatRequest> requests = List.of(
    ChatRequest.builder()
        .messages(UserMessage.from("What is the capital of France?"))
        .build(),
    ChatRequest.builder()
        .messages(UserMessage.from("What is the capital of Germany?"))
        .build(),
    ChatRequest.builder()
        .messages(UserMessage.from("What is the capital of Italy?"))
        .build()
);

// Submit the batch (generic API, no Gemini-specific options)
BatchResponse<ChatResponse> response = batchModel.submit(new BatchRequest<>(requests));

// Or, to set a Gemini-specific display name and priority, use GeminiBatchRequest:
BatchResponse<ChatResponse> response = batchModel.submit(GeminiBatchRequest.from(
    requests,
    "Geography Questions Batch", // display name
    0L                           // priority (optional, defaults to 0)
));
```

**File-based batch creation:**

For larger batches or when you need more control over the request format, you can create a batch from an uploaded file:

```java
// First, upload a file with batch requests
GeminiFiles filesApi = GeminiFiles.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .build();

GeminiFile uploadedFile = filesApi.uploadFile(
    Paths.get("batch_chat_requests.jsonl"),
    "Batch Chat Requests"
);

// Wait for file to be active
while (uploadedFile.isProcessing()) {
    Thread.sleep(1000);
    uploadedFile = filesApi.getMetadata(uploadedFile.name());
}

// Create batch from file
BatchResponse<ChatResponse> response = batchModel.submit("My Batch Job", uploadedFile);
```

### Handling Batch Responses

A `BatchResponse` exposes the current `state()` together with the per-request `results()` and the
`responses()` / `errors()` convenience views. Branch on the `state()` (use `state().isTerminal()`
to tell whether the batch is still in progress):

```java
BatchResponse<ChatResponse> response = batchModel.submit(new BatchRequest<>(requests));

if (!response.state().isTerminal()) {
    System.out.println("Batch is " + response.state());
    System.out.println("Batch ID: " + response.batchId());
} else if (response.state() == BatchState.SUCCEEDED) {
    System.out.println("Batch completed successfully!");

    // Process successful responses
    for (ChatResponse chatResponse : response.responses()) {
        System.out.println(chatResponse.aiMessage().text());
    }

    // Check for individual request errors within the batch
    if (!response.errors().isEmpty()) {
        System.out.println("Some requests failed:");
        for (BatchError error : response.errors()) {
            System.err.println("Error code: " + error.code() + ", message: " + error.message());
        }
    }
} else {
    System.err.println("Batch " + response.state() + ": " + response.errors());
}
```

**Note:** A batch with `state() == SUCCEEDED` indicates the batch job completed, but individual
requests within the batch may have failed. The `errors()` list contains any individual request
failures (e.g., timeouts, rate limits), while `responses()` contains the successful responses.
Both are convenience views and are never `null` (empty when there is nothing to report), so check
`!responses().isEmpty()` / `!errors().isEmpty()` to handle partial failures gracefully.

### Correlating Results with Requests

`responses()` and `errors()` are flat views that lose track of which input produced which outcome.
When you need to map every outcome back to its originating request, use `results()` instead: it
returns one `BatchItemResult` per request, **in the same order as the submitted requests**, so the
i-th result corresponds to the i-th request. Each result is either a `BatchItemResult.Success`
(carrying the `response()`) or a `BatchItemResult.Failure` (carrying the `error()`):

```java
BatchResponse<ChatResponse> result = batchModel.submit(new BatchRequest<>(requests));
// ... poll until terminal ...

List<BatchItemResult<ChatResponse>> results = result.results();
for (int i = 0; i < results.size(); i++) {
    BatchItemResult<ChatResponse> item = results.get(i);
    if (item.isSuccess()) {
        System.out.println("Request #" + i + " -> " + item.response().aiMessage().text());
    } else {
        BatchError error = item.error();
        System.err.println("Request #" + i + " failed: " + error.code() + " - " + error.message());
    }
}
```

### Polling for Results

Since batch processing is asynchronous, you need to poll for results (results might take up to 24 hours to process):

```java
BatchResponse<ChatResponse> result = batchModel.submit(new BatchRequest<>(requests));
String batchId = result.batchId();

// Poll until the batch reaches a terminal state
while (!result.state().isTerminal()) {
    Thread.sleep(5000); // Wait 5 seconds between polls
    result = batchModel.retrieve(batchId);
}

// Process final result
if (result.state() == BatchState.SUCCEEDED) {
    System.out.println("Successful responses: " + result.responses().size());
    for (ChatResponse chatResponse : result.responses()) {
        System.out.println(chatResponse.aiMessage().text());
    }

    // Handle any individual request failures
    if (!result.errors().isEmpty()) {
        System.out.println("Failed requests: " + result.errors().size());
        for (BatchError error : result.errors()) {
            System.err.println("Error: " + error.code() + " - " + error.message());
        }
    }
} else {
    System.err.println("Batch did not succeed: " + result.state());
}
```

### Managing Batch Jobs

**Cancel a batch job:**

```java
String batchId = // ... obtained from submit(...)

try {
    batchModel.cancel(batchId);
    System.out.println("Batch cancelled successfully");
} catch (HttpException e) {
    System.err.println("Failed to cancel batch: " + e.getMessage());
}
```

**Delete a batch job:**

```java
batchModel.deleteBatchJob(batchId);
System.out.println("Batch deleted successfully");
```

**List batch jobs:**

```java
// List first page of batch jobs
BatchPage<ChatResponse> page = batchModel.list(new BatchPagination(10, null));

for (BatchResponse<ChatResponse> batch : page.batches()) {
    System.out.println("Batch: " + batch);
}

// Get next page if available
if (page.nextPageToken() != null) {
    BatchPage<ChatResponse> nextPage = batchModel.list(new BatchPagination(10, page.nextPageToken()));
}
```

### File-Based Batch Processing

For advanced use cases, you can write batch requests to a JSONL file and upload it:

```java
// Create a JSONL file with batch requests
Path batchFile = Files.createTempFile("batch", ".jsonl");

try (JsonLinesWriter writer = new StreamingJsonLinesWriter(batchFile)) {
    List<BatchFileRequest<ChatRequest>> fileRequests = List.of(
        new BatchFileRequest<>("request-1", ChatRequest.builder()
            .messages(UserMessage.from("Question 1"))
            .build()),
        new BatchFileRequest<>("request-2", ChatRequest.builder()
            .messages(UserMessage.from("Question 2"))
            .build())
    );
    
    batchModel.writeBatchToFile(writer, fileRequests);
}

// Upload the file
GeminiFiles filesApi = GeminiFiles.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .build();

GeminiFile uploadedFile = filesApi.uploadFile(batchFile, "Batch Chat Requests");

// Create batch from file
BatchResponse<ChatResponse> response = batchModel.submit("File-Based Chat Batch", uploadedFile);
```

### Batch Job States

The `BatchState` enum represents the possible states of a batch job:

- `PENDING`: Batch is queued and waiting to be processed
- `RUNNING`: Batch is currently being processed
- `SUCCEEDED`: Batch completed successfully (terminal)
- `FAILED`: Batch processing failed (terminal)
- `CANCELLED`: Batch was cancelled by the user (terminal)
- `EXPIRED`: Batch expired before completion (terminal)
- `UNSPECIFIED`: State is unknown or not provided

Use `BatchState.isTerminal()` on `BatchResponse.state()` to detect when polling can stop.

### Setting Batch Priority

Higher priority batches are processed before lower priority ones. Set priority through `GeminiBatchRequest`:

```java
// High priority batch
BatchResponse<ChatResponse> highPriority = batchModel.submit(GeminiBatchRequest.from(
    urgentRequests, "Urgent Batch", 100L));

// Low priority batch
BatchResponse<ChatResponse> lowPriority = batchModel.submit(GeminiBatchRequest.from(
    backgroundRequests, "Background Batch", -50L));
```

### Configuration

The `GoogleAiGeminiBatchChatModel` supports the same configuration options as `GoogleAiGeminiChatModel`:

```java
GoogleAiGeminiBatchChatModel batchModel = GoogleAiGeminiBatchChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .temperature(0.7)
    .topP(0.95)
    .topK(40)
    .maxOutputTokens(2048)
    .maxRetries(3)
    .timeout(Duration.ofMinutes(5))
    .logRequestsAndResponses(true)
    .build();
```

### Important Constraints

- **Model Consistency**: All requests in a batch must use the same model
- **Size Limit**: The inline API supports a total request size of 20MB or under
- **Cost**: Batch processing offers 50% cost reduction compared to real-time requests
- **Turnaround**: 24-hour SLO, though completion is often much quicker
- **Use Cases**: Best for large-scale, non-urgent tasks like data pre-processing or evaluations


### Example: Complete Workflow

```java
GoogleAiGeminiBatchChatModel batchModel = GoogleAiGeminiBatchChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

// Prepare batch requests
List<ChatRequest> requests = new ArrayList<>();
for (int i = 0; i < 50; i++) {
    requests.add(ChatRequest.builder()
        .messages(UserMessage.from("Generate a creative story idea #" + i))
        .build());
}

// Submit batch
BatchResponse<ChatResponse> result = batchModel.submit(GeminiBatchRequest.from(
    requests, "Story Ideas Batch", 0L));
String batchId = result.batchId();

// Poll for completion
int attempts = 0;
int maxAttempts = 720; // 1 hour with 5-second intervals
while (!result.state().isTerminal()) {
    if (attempts++ >= maxAttempts) {
        throw new RuntimeException("Batch processing timeout");
    }
    Thread.sleep(5000);
    result = batchModel.retrieve(batchId);
    System.out.println("Status: " + result.state());
}

// Process results
if (result.state() == BatchState.SUCCEEDED) {
    System.out.println("Generated " + result.responses().size() + " stories");
    for (int i = 0; i < result.responses().size(); i++) {
        ChatResponse chatResponse = result.responses().get(i);
        System.out.println("Story #" + i + ": " + chatResponse.aiMessage().text());
    }

    // Report any failures
    if (!result.errors().isEmpty()) {
        System.err.println(result.errors().size() + " requests failed:");
        for (BatchError error : result.errors()) {
            System.err.println("  - Code " + error.code() + ": " + error.message());
        }
    }
} else {
    System.err.println("Batch did not succeed: " + result.state());
}
```

## Learn more

If you're interested in learning more about the Google AI Gemini model, please have a look at its
[documentation](https://ai.google.dev/gemini-api/docs/models/gemini).
