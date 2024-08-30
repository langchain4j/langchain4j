---
sidebar_position: 7
---

# Tools (Function Calling)

Some LLMs, in addition to generating text, can also trigger actions.

:::note
All LLMs supporting tools can be found [here](/integrations/language-models) (see the "Tools" column).
:::

There is a concept known as "tools," or "function calling".
It allows the LLM to call, when necessary, one or more available tools, usually defined by the developer.
A tool can be anything: a web search, a call to an external API, or the execution of a specific piece of code, etc.
LLMs cannot actually call the tool themselves; instead, they express the intent
to call a specific tool in their response (instead of responding in plain text).
We, as developers, should then execute this tool with the provided arguments and report back
the results of the tool execution.

For example, we know that LLMs themselves are not very good at math.
If your use case involves occasional math calculations, you might want to provide the LLM with a "math tool."
By declaring one or multiple tools in the request to the LLM,
it can then decide to call one of them if it deems it appropriate.
Given a math question along with a set of math tools, the LLM might decide that to properly answer the question,
it should first call one of the provided math tools.

Let's see how this works in practice (with and without tools):

An example of a message exchange without tools:
```
Request:
- messages:
    - UserMessage:
        - text: What is the square root of 475695037565?

Response:
- AiMessage:
    - text: The square root of 475695037565 is approximately 689710.
```
Close, but not correct.

An example of a message exchange with the following tools:
```java
@Tool("Sums 2 given numbers")
public double sum(double a, double b) {
    return a + b;
}

@Tool("Returns a square root of a given number")
public double squareRoot(double x) {
    return Math.sqrt(x);
}
```

```
Request 1:
- messages:
    - UserMessage:
        - text: What is the square root of 475695037565?
- tools:
    - sum(double a, double b): Sums 2 given numbers
    - squareRoot(double x): Returns a square root of a given number

Response 1:
- AiMessage:
    - toolExecutionRequests:
        - squareRoot(475695037565)


... here we are executing the squareRoot method with the "475695037565" argument and getting "689706.486532" as a result ...


Request 2:
- messages:
    - UserMessage:
        - text: What is the square root of 475695037565?
    - AiMessage:
        - toolExecutionRequests:
            - squareRoot(475695037565)
    - ToolExecutionResultMessage:
        - text: 689706.486532

Response 2:
- AiMessage:
    - text: The square root of 475695037565 is 689706.486532.
```

As you can see, when an LLM has access to tools, it can decide to call one of them when appropriate.

This is a very powerful feature.
In this simple example, we gave the LLM primitive math tools,
but imagine if we gave it, for example, `googleSearch` and `sendEmail` tools
and a query like "My friend wants to know recent news in the AI field. Send the short summary to friend@email.com,"
then it could find recent news using the `googleSearch` tool,
then summarize it and send the summary via email using the `sendEmail` tool.

:::note
To increase the chances of the LLM calling the right tool with the right arguments,
we should provide a clear and unambiguous:
- name of the tool
- description of what the tool does and when it should be used
- description of every tool parameter

A good rule of thumb: if a human can understand the purpose of a tool and how to use it,
chances are that the LLM can too.
:::

LLMs are specifically fine-tuned to detect when to call tools and how to call them.
Some models can even call multiple tools at once, for example,
[OpenAI](https://platform.openai.com/docs/guides/function-calling/parallel-function-calling).

:::note
Please note that not all models support tools.
To see which models support tools, refer to the "Tools" column on [this](https://docs.langchain4j.dev/integrations/language-models/) page.
:::

:::note
Please note that tools/function calling is not the same as [JSON mode](/tutorials/ai-services#json-mode).
:::

## 2 levels of abstraction

LangChain4j provides two levels of abstraction for using tools:
- Low-level, using the `ChatLanguageModel` API
- High-level, using [AI Services](/tutorials/ai-services) and `@Tool`-annotated Java methods

### Low level Tool API

At the low level, you can use the `generate(List<ChatMessage>, List<ToolSpecification>)` method
of the `ChatLanguageModel`. A similar method is also present in the `StreamingChatLanguageModel`.

`ToolSpecification` is an object that contains all the information about the tool:
- The `name` of the tool
- The `description` of the tool
- The `parameters` (arguments) of the tool and their descriptions

It is recommended to provide as much information about the tool as possible:
a clear name, a comprehensive description, and a description for each parameter, etc.

There are two ways to create a `ToolSpecification`:

1. Manually
```java
ToolSpecification toolSpecification = ToolSpecification.builder()
    .name("getWeather")
    .description("Returns the weather forecast for a given city")
    .addParameter("city", type("string"), description("The city for which the weather forecast should be returned"))
    .addParameter("temperatureUnit", enums(TemperatureUnit.class)) // enum TemperatureUnit { CELSIUS, FAHRENHEIT }
    .build();
```

2. Using helper methods:
- `ToolSpecifications.toolSpecificationsFrom(Class)`
- `ToolSpecifications.toolSpecificationsFrom(Object)`
- `ToolSpecifications.toolSpecificationFrom(Method)`

```java
class WeatherTools { 
  
    @Tool("Returns the weather forecast for a given city")
    String getWeather(
            @P("The city for which the weather forecast should be returned") String city,
            TemperatureUnit temperatureUnit
    ) {
        ...
    }
}

List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(WeatherTools.class);
```

Once you have a `List<ToolSpecification>`, you can call the model:
```java
UserMessage userMessage = UserMessage.from("What will the weather be like in London tomorrow?");
Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecifications);
AiMessage aiMessage = response.content();
```

If the LLM decides to call the tool, the returned `AiMessage` will contain data
in the `toolExecutionRequests` field.
In this case, `AiMessage.hasToolExecutionRequests()` will return `true`.
Depending on the LLM, it can contain one or multiple `ToolExecutionRequest` objects
(some LLMs support calling multiple tools in parallel).

Each `ToolExecutionRequest` should contain:
- The `id` of the tool call (some LLMs do not provide it)
- The `name` of the tool to be called, for example: `getWeather`
- The `arguments`, for example: `{ "city": "London", "temperatureUnit": "CELSIUS" }`

You'll need to manually execute the tool(s) using information from the `ToolExecutionRequest`(s).

If you want to send the result of the tool execution back to the LLM,
you need to create a `ToolExecutionResultMessage` (one for each `ToolExecutionRequest`)
and send it along with all previous messages:
```java
String result = "It is expected to rain in London tomorrow.";
ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, result);
List<ChatMessage> messages = List.of(userMessage, aiMessage, toolExecutionResultMessage);
Response<AiMessage> response2 = model.generate(messages, toolSpecifications);
```

### High Level Tool API
At a high level, you can annotate any Java method with the `@Tool` annotation
and use it with [AI Services](/tutorials/ai-services).

AI Services will automatically convert such methods into `ToolSpecification`s
and include them in the request for each interaction with the LLM.
When the LLM decides to call the tool, the AI Service will automatically execute the appropriate method,
and the return value of the method (if any) will be sent back to the LLM.
You can find implementation details in `DefaultToolExecutor`.

Methods annotated with `@Tool` can accept any number of parameters of various types.

They can also return any type, including `void`. If the method has a `void` return type,
"Success" string is sent to the LLM if the method returns successfully.

If the method has a `String` return type, the returned value is sent to the LLM as is, without any conversions.

For other return types, the returned value is converted into a JSON before being sent to the LLM.

A few tool examples:
```java
@Tool("Searches Google for relevant URLs, given the query")
public List<String> searchGoogle(@P("search query") String query) {
    return googleSearchService.search(query);
}

@Tool("Returns the content of a web page, given the URL")
public String getWebPageContent(@P("URL of the page") String url) {
    Document jsoupDocument = Jsoup.connect(url).get();
    return jsoupDocument.body().text();
}
```

### `@Tool`
Any Java method annotated with `@Tool`
and _explicitly_ specified during the build of an AI Service can be executed by the LLM:
```java
interface MathGenius {
    
    String ask(String question);
}

class Calculator {
    
    @Tool
    public double add(int a, int b) {
        return a + b;
    }

    @Tool
    public double squareRoot(double x) {
        return Math.sqrt(x);
    }
}

MathGenius mathGenius = AiServices.builder(MathGenius.class)
    .chatLanguageModel(model)
    .tools(new Calculator())
    .build();

String answer = mathGenius.ask("What is the square root of 475695037565?");

System.out.println(answer); // The square root of 475695037565 is 689706.486532.
```

When the `ask` method is called, 2 interactions with the LLM occur, as described in the earlier section.
In between those interactions, the `squareRoot` method is called automatically.

The `@Tool` annotation has 2 optional fields:
- `name`: the tool's name. If this is not provided, the method's name will serve as the tool's name.
- `value`: the tool's description.

Depending on the tool, the LLM might understand it well even without any description
(for example, `add(a, b)` is obvious),
but it is usually better to provide clear and meaningful names and descriptions.
This way, the LLM has more information to decide whether or not to call the given tool, and how to do so.

### `@P`
Method parameters can optionally be annotated with `@P`.

The `@P` annotation has 2 fields
- `value`: description of the parameter. Mandatory field.
- `required`: whether the parameter is required, default is `true`. Optional field.

### `@ToolMemoryId`
If your AI Service method has a parameter annotated with `@MemoryId`,
you can also annotate a parameter of a `@Tool` method with `@ToolMemoryId`.
The value provided to the AI Service method will be automatically passed to the `@Tool` method.
This feature is useful if you have multiple users and/or multiple chats/memories per user
and wish to distinguish between them inside the `@Tool` method.

## Accessing Executed Tools
If you wish to access tools executed during the invocation of an AI Service,
you can easily do so by wrapping the return type in the `Result` class:
```java
interface Assistant {

    Result<String> chat(String userMessage);
}

Result<String> result = assistant.chat("Cancel my booking 123-456");

String answer = result.content();
List<ToolExecution> toolExecutions = result.toolExecutions();
```

### Configuring Tools Programmatically

When using AI Services, tools can also be configured programmatically.
This approach offers a lot of flexibility, as tools can now be loaded
from external sources such as databases and configuration files.

Tool names, descriptions, parameter names, and descriptions
can all be configured using `ToolSpecification`:
```java
ToolSpecification toolSpecification = ToolSpecification.builder()
    .name("get_booking_details")
    .description("Returns booking details")
    .addParameter("bookingNumber", type("string"), description("Booking number in B-12345 format"))
    .build();
```

For each `ToolSpecification`, one needs to provide a `ToolExecutor` implementation
that will be handling tool execution requests generated by the LLM:
```java
ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
    Map<String, Object> arguments = fromJson(toolExecutionRequest.arguments());
    String bookingNumber = arguments.get("bookingNumber").toString();
    Booking booking = getBooking(bookingNumber);
    return booking.toString();
};
```

Once we have one or multiple (`ToolSpecification`, `ToolExecutor`) pairs,
we can specify them when creating an AI Service:
```java
Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatLanguageModel)
    .tools(singletonMap(toolSpecification, toolExecutor))
    .build();
```

## Related Tutorials

- [Great guide on Tools](https://www.youtube.com/watch?v=cjI_6Siry-s)
  by [Tales from the jar side (Ken Kousen)](https://www.youtube.com/@talesfromthejarside)

## Examples

- [Example with Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)
- [Example with dynamic Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithDynamicToolsExample.java)
