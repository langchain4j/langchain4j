---
sidebar_position: 7
---

# Tools (Function Calling)

Some LLMs, in addition to generating text, can also trigger actions.

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
Currently, the following models have tool support:
- `OpenAiChatModel`
- `AzureOpenAiChatModel`
- `LocalAiChatModel`
- `QianfanChatModel`
:::

:::note
Please note that tools/function calling is not the same as [JSON mode](/tutorials/ai-services#json-mode).
:::

## 2 levels of abstraction

LangChain4j provides two levels of abstraction for working with tools.

### Low level Tool API
At the low level, you can use the `generate(List<ChatMessage>, List<ToolSpecification>)`
and `generate(List<ChatMessage>, ToolSpecification)` methods
of `ChatLanguageModel` (and similar methods of `StreamingChatLanguageModel`).

You'll need to manually create `ToolSpecification` object(s) containing all information about the tool,
or use the `ToolSpecifications.toolSpecificationFrom(Method)` helper method
to convert any Java method into a `ToolSpecification`.

When the LLM decides to call the tool, the returned `AiMessage` will have data
in a `List<ToolExecutionRequest> toolExecutionRequests` field instead of a `String text` field.
Depending on the LLM, it can contain one or multiple `ToolExecutionRequest`s
(some LLMs support calling multiple tools in parallel).

The `ToolExecutionRequest` will include the tool call's `id`, the `name` of the tool to be called,
and `arguments` (a valid JSON containing a value for each tool parameter).
You'll need to manually execute the tool(s) using information from the `ToolExecutionRequest`(s)
and then create a `ToolExecutionResultMessage` containing each tool's execution result.

Then, call the LLM with all messages (`UserMessage`, `AiMessage` containing `ToolExecutionRequest`,
`ToolExecutionResultMessage`) to get the final response from the LLM.

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
`@P` has a single mandatory field (`value`) for providing a description of the parameter.

### `@ToolMemoryId`
If your AI Service method has a parameter annotated with `@MemoryId`,
you can also annotate a parameter of a `@Tool` method with `@ToolMemoryId`.
The value provided to the AI Service method will be automatically passed to the `@Tool` method.
This feature is useful if you have multiple users and/or multiple chats/memories per user
and wish to distinguish between them inside the `@Tool` method.

## Advanced: Custom Type Tool Parameters

Regardless of the two levels of Tool API, 
the tool will ultimately be transformed into a JSON schema.
[OpenAI's tool/function calling method](https://platform.openai.com/docs/api-reference/chat/create#chat-create-tools)
requires tools to be outlined in [JSON schema format](https://json-schema.org/understanding-json-schema/).
This schema helps the language model understand the tool's parameters and how to construct them.
It also helps `AiServices` validate and deserialize the arguments before calling the tool.

When designing tool interfaces, 
it's generally a good practice to keep them simple and straightforward 
to elicit a reliable response from the language model.
Hence, the `@P` annotation typically supports and recommends primitive and standard library types.
This includes `int`, `long`, `byte`, `double`, `float`, `boolean`, `String`, `List`, `Set`, `Collection`, and `Map`.

Langchain4j also allows passing custom types as parameters to a `@Tool` method, but it requires additional steps.
Langchain4j delegates the JSON schema generation of custom types
to third-party libraries like [Victools](https://github.com/victools/jsonschema-generator).
To enable it, you need to add dependency:

```xml
<!-- Maven -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jsonschema-service-victools</artifactId>
    <version>{your-version}</version> <!-- Specify langchain4j version here -->
</dependency>
```

Then, you can apply the [Jackson Annotations](https://github.com/FasterXML/jackson-annotations)
on custom types to specify which fields to be included into the JSON schema and what attributes they have
(see [Jackson Annotations](https://github.com/victools/jsonschema-generator/tree/main/jsonschema-module-jackson)
for more details):

```java
import com.fasterxml.jackson.annotation.*;

@JsonClassDescription("This is a custom type, it ...")
class CustomType {

    @JsonProperty("string_field")
    @JsonPropertyDescription("This is a string field, it ...")
    @Nullable
    private String stringField;

    @JsonProperty(value = "int_field", required = true)
    @NotNull
    private int intField;

    // additional fields
}
```

After that, you can use the custom type as a parameter in a `@Tool` method:

```java
@Tool("This tool uses a custom type")
public void useCustomType(@P CustomType customTypeParam) {
    // ...
}
```

When reviewing the messages sent to the language model, you may describe the tool as:

```json
{
  "name": "useCustomType",
  "description": "This tool uses a custom type",
  "parameters": {
    "customTypeParam": {
      "type": "object",
      "description": "This is a custom type, it ...",
      "properties": {
        "string_field": {
          "type": ["string", "null"],
          "description": "This field is a string."
        },
        "int_field": {
          "type": "integer"
        }
      },
      "required": [
        "int_field"
      ]
    }
  }
}
```

The language model will then generate a tool execution request based on the JSON schema provided.
The request's arguments will be validated against the schema
and then deserialized into the custom type object for execution.

## Related Tutorials

- [Great guide on Tools](https://www.youtube.com/watch?v=cjI_6Siry-s)
  by [Tales from the jar side (Ken Kousen)](https://www.youtube.com/@talesfromthejarside)

## Examples

- [Example with Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)
- [Example with dynamic Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithDynamicToolsExample.java)
