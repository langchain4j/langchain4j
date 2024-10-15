---
sidebar_position: 7
---

# Google AI Gemini

https://ai.google.dev/gemini-api/docs

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>0.35.0</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Models available

Check the list of [available models](https://ai.google.dev/gemini-api/docs/models/gemini) in the documentation.

* `gemini-1.5-flash`
* `gemini-1.5-pro`
* `gemini-1.0-pro`

## GoogleAiGeminiChatModel

The usual `generate(...)` methods are available:

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    ...
    .build();

String response = gemini.generate("Hello Gemini!");
```

As well, as the `ChatResponse chat(ChatRequest req)` method:

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .build();

ChatResponse chatResponse = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from(
        "How many R's are there in the word 'strawberry'?"))
    .build());

String response = chatResponse.aiMessage().text();
```

### Configuring

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .temperature(1.0)
    .topP(0.95)
    .topK(64)
    .maxOutputTokens(8192)
    .timeout(Duration.ofSeconds(60))
    .candidateCount(1)
    .responseFormat(ResponseFormat.JSON) // or .responseFormat(ResponseFormat.builder()...build()) 
    .stopSequences(List.of(...))
    .toolConfig(GeminiFunctionCallingConfig.builder()...build()) // or below
    .toolConfig(GeminiMode.ANY, List.of("fnOne", "fnTwo"))
    .allowCodeExecution(true)
    .includeCodeExecution(output)
    .logRequestsAndResponses(true)
    .safetySettings(List<GeminiSafetySetting> or Map<GeminiHarmCategory, GeminiHarmBlockThreshold>)
    .build();
```

## GoogleAiGeminiStreamingChatModel

No streaming chat model is available yet.
Please open a feature request if you're interested in a streaming model or if you want to contribute to implementing it.

## Tools

Tools (aka Function Calling) is supported, including parallel calls.
You can either use the `generate(...)` methods that take a single or a list of tool specifications to let Gemini know it can request a function to be called. Or you can use LangChain4j's `AiServices` to define them.

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

ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .temperature(0.0)
    .build();

WeatherAssistant weatherAssistant =
    AiServices.builder(WeatherAssistant.class)
        .chatLanguageModel(gemini)
        .tools(weatherForecastService)
        .build();

String tokyoWeather = weatherAssistant.chat(
        "What is the weather forecast for Tokyo?");

System.out.println("Gemini> " + tokyoWeather);
// Gemini> The weather forecast for Tokyo is warm
//         with a temperature of 32 degrees.
```

## Structured output

### JSON mode

You can force Gemini to reply in JSON:

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .responseFormat(ResponseFormat.JSON)
    .build();

String roll = gemini.generate("Roll a 6-sided dice");

System.out.println(roll);
// {"roll": "3"}
```

A system prompt can further describe what the JSON output should look like.
Gemini normally follows the suggested schema, but it is not guaranteed.
If you want a guaranteed application of a JSON schema, you should define a response format, as explained in the next section.

### Response format / response schema

You can specify: a `ResponseFormat` via the `responseFormat()` builder method.

Let's have a look at an example to define a JSON schema for a recipe:

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .responseFormat(ResponseFormat.builder()
        .type(JSON)
        .jsonSchema(JsonSchema.builder()
            .rootElement(JsonObjectSchema.builder()
                .addStringProperty("title")
                .addIntegerProperty("preparationTimeMinutes")
                .addArrayProperty("ingredients", new JsonStringSchema())
                .addArrayProperty("steps", new JsonStringSchema())
                .build())
            .build())
        .build())
    .build();

String recipeResponse = gemini.generate(
    "Suggest a dessert recipe with strawberries");

System.out.println(recipeResponse);
```

Instead of building the JSON schema yourself, you can also derive a schema from your own Java classes:

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .temperature(2.0)
    .responseFormat(ResponseFormat.builder()
        .type(JSON)
        .jsonSchema(JsonSchemas.jsonSchemaFrom(TripItinerary.class).get())
        .build())
    .build();
```

## Type-safe data extraction from free form text

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

ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .build();

WeatherForecastAssistant forecastAssistant =
    AiServices.builder(WeatherForecastAssistant.class)
        .chatLanguageModel(gemini)
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

## Python code execution

Beyond function calling, Google AI Gemini allows to create and execute Python code in a sandboxed environment.
This is particularly interesting for situations where more advanced calculations or logic is needed.

```java
ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .allowCodeExecution(true)
    .includeCodeExecutionOutput(true)
    .build();
```

There are 2 builder methods:
* `allowCodeExecution(true)`: to let Gemini know it can do some Python coding
* `includeCodeExecutionOutput(true)`: if you want to see the actual Python script it came up with, and the output of its execution

```java
Response<AiMessage> mathQuizz = gemini.generate(
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

Gemini is a multimodal model, which means it outputs text, but in input, it accepts other _modalities_ besides text, like:
* pictures (`ImageContent`)
* videos (`VideoContent`)
* audio files (`AudioContent`)
* PDF files (`PdfFileContent`)
* text documents (`TextFileContent`)

The example below shows how to mix a text prompt, with an image, and a Markdown document:

```java
// README.md markdown file from LangChain4j's project Github repos
String base64Text = b64encoder.encodeToString(readBytes(
  "https://github.com/langchain4j/langchain4j/blob/main/README.md"));

// PNG of the cute colorful parrot mascot of the LangChain4j project
String base64Img = b64encoder.encodeToString(readBytes(
  "https://avatars.githubusercontent.com/u/132277850?v=4"));

ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-1.5-flash")
    .build();

Response<AiMessage> response = gemini.generate(
    UserMessage.from(
        TextFileContent.from(base64Text, "text/x-markdown"),
        ImageContent.from(base64Img, "image/png"),
        TextContent.from("""
            Do you think this logo fits well
            with the project description?
            """)
    )
);
```

## Learn more

If you're interested in learning more about the Google AI Gemini model, please have a look at its 
[documentation](https://ai.google.dev/gemini-api/docs/models/gemini).