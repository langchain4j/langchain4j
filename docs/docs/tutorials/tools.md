---
sidebar_position: 7
---

# Tools (Function Calling)

Some LLMs, in addition to generating text, can also trigger actions.

:::note
All LLMs supporting tools can be found [here](/integrations/language-models) (see the "Tools" column).
:::

:::note
Not all LLMs support tools equally well.
The ability to understand, select, and correctly use tools depends heavily on the specific model and its capabilities.
Some models may not support tools at all, while others might require careful prompt engineering 
or additional system instructions.
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
double sum(double a, double b) {
    return a + b;
}

@Tool("Returns a square root of a given number")
double squareRoot(double x) {
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

# 2 levels of abstraction

LangChain4j provides two levels of abstraction for using tools:
- Low-level, using the `ChatModel` and `ToolSpecification` APIs
- High-level, using [AI Services](/tutorials/ai-services) and `@Tool`-annotated Java methods

## Low Level Tool API

At the low level, you can use the `chat(ChatRequest)` method
of the `ChatModel`. A similar method is also present in the `StreamingChatModel`.

You can specify one or more `ToolSpecification`s when creating the `ChatRequest`.

`ToolSpecification` is an object that contains all the information about the tool:
- The `name` of the tool
- The `description` of the tool
- The `parameters` of the tool and their descriptions

It is recommended to provide as much information about the tool as possible:
a clear name, a comprehensive description, and a description for each parameter, etc.

### Creating Tool Specification

There are two ways to create a `ToolSpecification`:

1. Manually
```java
ToolSpecification toolSpecification = ToolSpecification.builder()
    .name("getWeather")
    .description("Returns the weather forecast for a given city")
    .parameters(JsonObjectSchema.builder()
        .addStringProperty("city", "The city for which the weather forecast should be returned")
        .addEnumProperty("temperatureUnit", List.of("CELSIUS", "FAHRENHEIT"))
        .required("city") // the required properties should be specified explicitly
        .build())
    .build();
```

You can find more information on `JsonObjectSchema` [here](/tutorials/structured-outputs#jsonobjectschema).

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

### Using `ChatModel`

Once you have a `List<ToolSpecification>`, you can call the model:
```java
ChatRequest request = ChatRequest.builder()
    .messages(UserMessage.from("What will the weather be like in London tomorrow?"))
    .toolSpecifications(toolSpecifications)
    .build();
ChatResponse response = model.chat(request);
AiMessage aiMessage = response.aiMessage();
```

If the LLM decides to call the tool, the returned `AiMessage` will contain data
in the `toolExecutionRequests` field.
In this case, `AiMessage.hasToolExecutionRequests()` will return `true`.
Depending on the LLM, it can contain one or multiple `ToolExecutionRequest` objects
(some LLMs support calling multiple tools in parallel).

Each `ToolExecutionRequest` should contain:
- The `id` of the tool call. Please note that some LLM providers (e.g., Google, Ollama) may omit this ID.
- The `name` of the tool to be called, for example: `getWeather`
- The `arguments`, for example: `{ "city": "London", "temperatureUnit": "CELSIUS" }`

You'll need to manually execute the tool(s) using information from the `ToolExecutionRequest`(s).

If you want to send the result of the tool execution back to the LLM,
you need to create a `ToolExecutionResultMessage` (one for each `ToolExecutionRequest`)
and send it along with all previous messages:
```java

String result = "It is expected to rain in London tomorrow.";
ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, result);
ChatRequest request2 = ChatRequest.builder()
        .messages(List.of(userMessage, aiMessage, toolExecutionResultMessage))
        .toolSpecifications(toolSpecifications)
        .build();
ChatResponse response2 = model.chat(request2);
```

### Using `StreamingChatModel`

Once you have a `List<ToolSpecification>`, you can call the model:
```java
ChatRequest request = ChatRequest.builder()
    .messages(UserMessage.from("What will the weather be like in London tomorrow?"))
    .toolSpecifications(toolSpecifications)
    .build();

model.chat(request, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("onPartialResponse: " + partialResponse);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        System.out.println("onPartialToolCall: " + partialToolCall);
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        System.out.println("onCompleteToolCall: " + completeToolCall);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("onCompleteResponse: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

If the LLM decides to call the tool, the `onPartialToolCall(PartialToolCall)` callback
will typically be called multiple times before an `onCompleteToolCall(CompleteToolCall)` callback
is eventually called, indicating that streaming for that tool call is finished.

:::note
Please note that not all LLM providers stream partial tool calls.
Some providers (e.g., Bedrock, Google, Mistral, Ollama) return only complete tool calls.
In those cases, `onPartialToolCall` callback won't be invoked - only `onCompleteToolCall` will be called.
:::

Here's an example of what streaming of a single tool call might look like:
```
onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "{\"")
onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "city")
onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = ""\":\"")
onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "London")
onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "\"}")
onCompleteToolCall(index = 0, id = "call_abc", name = "get_weather", arguments = "{\"city\":\"London\"}")
```

If the LLM initiates multiple tool calls, the `index` will increment, allowing you to correlate
the different `PartialToolCall`s with each other and with the final `CompleteToolCall`.

When complete response streaming is over and `onCompleteResponse(ChatResponse)` is invoked,
the `AiMessage` inside the `ChatResponse` will contain all the tool calls that occurred during streaming.

## High Level Tool API
At a high level of abstraction, you can annotate any Java method with the `@Tool` annotation
and specify them when creating [AI Service](/tutorials/ai-services#tools-function-calling).

AI Service will automatically convert such methods into `ToolSpecification`s
and include them in the request for each interaction with the LLM.
When the LLM decides to call the tool, the AI Service will automatically execute the appropriate method,
and the return value of the method (if any) will be sent back to the LLM.
You can find implementation details in `DefaultToolExecutor`.

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

### Tool Method Limitations
Methods annotated with `@Tool`:
- can be either static or non-static
- can have any visibility (public, private, etc.).

### Tool Method Parameters
Methods annotated with `@Tool` can accept any number of parameters of various types:
- Primitive types: `int`, `double`, etc
- Object types: `String`, `Integer`, `Double`, etc
- Custom POJOs (can contain nested POJOs)
- `enum`s
- `List<T>`/`Set<T>` where `T` is one of the above-mentioned types
- `Map<K,V>` (you need to manually specify the types of `K` and `V` in the parameter description with `@P`)

Methods without parameters are supported as well.

#### Required and Optional

By default, all tool method parameters are considered **_required_**.
This means that the LLM will have to produce a value for such a parameter.
A parameter can be made optional by annotating it with `@P(required = false)`:
```java
@Tool
void getTemperature(String location, @P(value = "Unit of temperature", required = false) Unit unit) {
    ...
}
```

Fields and sub-fields of complex parameters are also considered **_required_** by default.
You can make a field optional by annotating it with `@JsonProperty(required = false)`:
```java
record User(String name, @JsonProperty(required = false) String email) {}

@Tool
void add(User user) {
    ...
}
```

:::note
Please note that when used with [structured outputs](/tutorials/structured-outputs),
all fields and sub-fields are considered **_optional_** by default.
:::

Recursive parameters (e.g., a `Person` class having a `Set<Person> children` field)
are currently supported only by OpenAI.

### Tool Method Return Types
Methods annotated with `@Tool` can return any type, including `void`.
If the method has a `void` return type, "Success" string is sent to the LLM if the method returns successfully.

If the method has a `String` return type, the returned value is sent to the LLM as is, without any conversions.

For other return types, the returned value is converted into a JSON string before being sent to the LLM.

### AI services as tools for other AI services

AI services can also be used as tools for other AI services. This can be useful in many agentic use cases, where one AI service can ask the help of another, more specialized, AI service to perform a specific task. For instance, having defined the following AI services:

```java
    interface RouterAgent {

        @dev.langchain4j.service.UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
            then forward the request as it is to the corresponding expert provided as a tool.
            Finally return the answer that you received from the expert without any modification.

            The user request is: '{{it}}'.
            """)
        String askToExpert(String request);
    }

    interface MedicalExpert {

        @dev.langchain4j.service.UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A medical expert")
        String medicalRequest(String request);
    }

    interface LegalExpert {

        @dev.langchain4j.service.UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A legal expert")
        String legalRequest(String request);
    }

    interface TechnicalExpert {

        @dev.langchain4j.service.UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A technical expert")
        String technicalRequest(String request);
    }
```

The `RouterAgent` can be configured to use as tools the 3 other AI services, experts in specific fields, routing the user request to one of them.

```java
MedicalExpert medicalExpert = AiServices.builder(MedicalExpert.class)
        .chatModel(model)
        .build();
LegalExpert legalExpert = AiServices.builder(LegalExpert.class)
        .chatModel(model)
        .build();
TechnicalExpert technicalExpert = AiServices.builder(TechnicalExpert.class)
        .chatModel(model)
        .build();

RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
        .chatModel(model)
        .tools(medicalExpert, legalExpert, technicalExpert)
        .build();

routerAgent.askToExpert("I broke my leg what should I do");
```

:::note
Using AI services as tools for other AI services is a powerful feature that enables to build complex agentic systems. However, this approach also comes with a few relevant drawbacks that are important to be aware of:
- This implementation requires the LLM to copy-paste the user request without modifications as a tool call and this could be an error-prone operation.
- The LLM calling the other LLM as a tool has to reprocess its response, as it happens for any other tool invocation, and this could be a wasteful computation in terms of both time and consumed tokens.
- The agent-tool, being a totally separated AI service, has no access to the chat memory of the agent calling it, so it cannot use the chat memory to provide a more informed answer.
:::


### `@Tool`
Any Java method annotated with `@Tool`
and _explicitly_ specified during the build of an AI Service can be executed by the LLM:
```java
interface MathGenius {
    
    String ask(String question);
}

class Calculator {
    
    @Tool
    double add(int a, int b) {
        return a + b;
    }

    @Tool
    double squareRoot(double x) {
        return Math.sqrt(x);
    }
}

MathGenius mathGenius = AiServices.builder(MathGenius.class)
    .chatModel(model)
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

### `@Description`
The description of classes and fields can be specified using the `@Description` annotation:

```java
@Description("Query to execute")
class Query {

  @Description("Fields to select")
  private List<String> select;

  @Description("Conditions to filter on")
  private List<Condition> where;
}

@Tool
Result executeQuery(Query query) {
  ...
}
```

:::note
Please note that `@Description` placed on an `enum` value has **_no effect_** and **_is not_** included
in the generated JSON schema:
```java
enum Priority {

    @Description("Critical issues such as payment gateway failures or security breaches.") // this is ignored
    CRITICAL,
    
    @Description("High-priority issues like major feature malfunctions or widespread outages.") // this is ignored
    HIGH,
    
    @Description("Low-priority issues such as minor bugs or cosmetic problems.") // this is ignored
    LOW
}
```
:::

### `@ToolMemoryId`
If your AI Service method has a parameter annotated with `@MemoryId`,
you can also annotate a parameter of a `@Tool` method with `@ToolMemoryId`.
The value provided to the AI Service method will be automatically passed to the `@Tool` method.
This feature is useful if you have multiple users and/or multiple chats/memories per user
and wish to distinguish between them inside the `@Tool` method.

### Executing Tools Concurrently

By default, when the LLM calls **_multiple_** tools at once (also known as parallel tool calling),
the AI Service executes them sequentially. If you want the tools to be executed concurrently,
you can call `executeToolsConcurrently()` or `executeToolsConcurrently(Executor)` when building the AI Service.
If you enable one of these options, the tools will be executed concurrently (with one exception - see below),
using either the default or the specified `Executor`.

#### When using `ChatModel`:
- When the LLM calls multiple tools, they are executed concurrently in separate threads
using the `Executor`.
- When the LLM calls a single tool, it is executed in the same (caller) thread,
the `Executor` is **_not_** used to avoid wasting resources.

#### When using `StreamingChatModel`:
- When the LLM calls multiple tools, they are executed concurrently in separate threads
using the `Executor`.
Each tool is executed as soon as `StreamingChatResponseHandler.onCompleteToolCall(CompleteToolCall)`
is called, without waiting for other tools or for the response streaming to complete.
- When the LLM calls a single tool, it is executed in a separate thread using the `Executor`.
We cannot execute it in the same thread because, at that point,
we do not yet know how many tools the LLM will call.

### Accessing Executed Tools
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

In streaming mode, you can do so by specifying `onToolExecuted` callback:
```java
interface Assistant {

    TokenStream chat(String message);
}

TokenStream tokenStream = assistant.chat("Cancel my booking");

tokenStream
    .onToolExecuted((ToolExecution toolExecution) -> System.out.println(toolExecution))
    .onPartialResponse(...)
    .onCompleteResponse(...)
    .onError(...)
    .start();
```

### Specifying Tools Programmatically

When using AI Services, tools can also be specified programmatically.
This approach offers a lot of flexibility, as tools can be loaded
from external sources such as databases and configuration files.

Tool names, descriptions, parameter names, and descriptions
can all be configured using `ToolSpecification`:
```java
ToolSpecification toolSpecification = ToolSpecification.builder()
        .name("get_booking_details")
        .description("Returns booking details")
        .parameters(JsonObjectSchema.builder()
                .properties(Map.of(
                        "bookingNumber", JsonStringSchema.builder()
                                .description("Booking number in B-12345 format")
                                .build()
                ))
                .build())
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
    .chatModel(chatModel)
    .tools(Map.of(toolSpecification, toolExecutor))
    .build();
```

### Specifying Tools Dynamically

When using AI services, tools can also be specified dynamically for each invocation.
One can configure a `ToolProvider` that will be called each time the AI service is invoked
and will provide the tools that should be included in the current request to the LLM.
The `ToolProvider` accepts a `ToolProviderRequest` that contains the `UserMessage` and chat memory ID
and returns a `ToolProviderResult` that contains tools in a form of a `Map` from `ToolSpecification` to `ToolExecutor`.

Here is an example of how to add the `get_booking_details` tool only when the user's message contains the word "booking":
```java
ToolProvider toolProvider = (toolProviderRequest) -> {
    if (toolProviderRequest.userMessage().singleText().contains("booking")) {
        ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("get_booking_details")
            .description("Returns booking details")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("bookingNumber")
                .build())
            .build();
        return ToolProviderResult.builder()
            .add(toolSpecification, toolExecutor)
            .build();
    } else {
        return null;
    }
};

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(model)
    .toolProvider(toolProvider)
    .build();
```

It is possible for an AI service to use both programmatically and dynamically specified tools in the same invocation.

### Returning immediately the result of a tool execution request

By default, the result of a tool execution request is sent back to the LLM that uses this result and further reprocesses it. However, in some circumstances, the result produced by that tool execution request already represents the expected result of the AI service invocation. In this case it is possible to configure the tool to immediately/directly return its result, skipping a wasteful and resource consuming reprocessing by the LLM. This can be done by configuring the `returnBehavior` field of the `@Tool` annotation as in the following example:

```java
class CalculatorWithImmediateReturn {
    
    @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
    double add(int a, int b) {
        return a + b;
    }
}
```

:::note
This feature is supported only on AI Services having a `Result<T>` return type. Attempting to use it on AI Service with a different return type will produce an `IllegalConfigurationException`. See [Return Types](/tutorials/ai-services#return-types) for more information about `Result<T>`.
:::

In this way, an `Assistant` service like the following

```java
interface Assistant {
    Result<String> chat(String userMessage);
}
```

configured to use the above `CalculatorWithImmediateReturn` tool

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new CalculatorWithImmediateReturn())
        .build();
```

will return a response directly from the tool invocation. For instance, prompting the assistant with

```java
Result<String> result = assistant.chat("How much is 37 plus 87?");
```

will produce a `Result` with a null content, while the actual response of `124` will have to be retrieved from the `result.toolExecutions()`. Without the immediate return, the LLM would have to reprocess the result of the `add` tool execution request, thus returning a response like: `The result of adding 37 and 87 is 124.`

Also note that if the LLM calls multiple tools and at least one of them is not immediate, then reprocessing will happen.

### Error Handling

#### Handling Tool Name Errors

It may happen that an LLM hallucinates on tools invocation,
or in other words that it asks to use a tool with a name that doesn't exist.
In this case by default LangChain4j will throw an exception reporting the problem,
but it is possible to configure a different behavior providing the AI service
with a strategy to be used in this situation.

This strategy is an implementation of a `Function<ToolExecutionRequest, ToolExecutionResultMessage>` defining which `ToolExecutionResultMessage` should be produced as the result for a `ToolExecutionRequest` containing the request to invoke a tool that is not available. For instance, it could be possible to configure the AI service with a strategy that returns to the LLM a response that hopefully will push it to retry a different tool invocation, knowing that the formerly required tool doesn't exist, as in the following example:

```java
AssistantHallucinatedTool assistant = AiServices.builder(AssistantHallucinatedTool.class)
        .chatModel(chatModel)
        .tools(new HelloWorld())
        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()))
        .build();
```

#### Handling Tool Arguments Errors

By default, when something is wrong with tool arguments (e.g., the LLM generates an invalid JSON),
the AI Service will not be able to execute the tool, so it will fail with an exception.

You can customize this behaviour by configuring a `ToolArgumentsErrorHandler` on the AI Service:

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(tools)
        .toolArgumentsErrorHandler((error, context) -> ...)
        .build();
```

Currently, there are two ways to handle errors inside the `ToolArgumentsErrorHandler`:

- Throw an exception: this will stop the AI service flow.
- Return a text message (e.g., an error description) that will be sent back to the LLM,
  allowing it to respond appropriately (for example, by correcting the error and retrying).

Here is an example of the first approach:

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(tools)
        .toolArgumentsErrorHandler((error, context) -> { throw MyCustomException(error); })
        .build();

try {
    assistant.chat(...);
} catch (MyCustomException e) {
        // handle e
}
```

Here is an example of the second approach:

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(tools)
        .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text("Something is wrong with tool arguments: " + error.getMessage()))
        .build();
```

#### Handling Tool Execution Errors

By default, when a method annotated with `@Tool` throws an `Exception`,
the message of the `Exception` (`e.getMessage()`) will be sent to the LLM as the result of tool's execution.
This allows the LLM to correct its mistake and retry, if it considers it necessary.

You can customize this behaviour by configuring a `ToolExecutionErrorHandler` on the AI Service:

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(tools)
        .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text("Something is wrong with tool execution: " + error.getMessage()))
        .build();
```

As with the `ToolArgumentsErrorHandler`, there are two ways to handle errors in `ToolExecutionErrorHandler`:
throw an exception or return a text message.

## Model Context Protocol (MCP)

You can also import [tools from MCP server](https://modelcontextprotocol.io/docs/concepts/tools).
More information on this can be found [here](/tutorials/mcp/#creating-an-mcp-tool-provider).

## Related Tutorials

- [Great guide on Tools](https://www.youtube.com/watch?v=cjI_6Siry-s)
  by [Tales from the jar side (Ken Kousen)](https://www.youtube.com/@talesfromthejarside)

## Examples

- [Example with Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)
- [Example with dynamic Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithDynamicToolsExample.java)
