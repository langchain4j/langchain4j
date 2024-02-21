---
sidebar_position: 6
---

# AI Services

So far, we have been covering low-level components like `ChatLanguageModel`, `ChatMessage`, `ChatMemory`, etc.
Working at this level is very flexible and gives you total freedom, but it also forces you to write a lot of boilerplate code.
Since LLM-powered applications usually require not just a single component but multiple components working together
(e.g., prompt templates, chat memory, LLMs, output parsers, RAG components: embedding models and stores)
and often involve multiple interactions, orchestrating them all becomes even more cumbersome.

We want you to focus on business logic, not on low-level implementation details.
Thus, there are currently two high-level concepts in LangChain4j that can help with that: AI Services and Chains.

## Chains

The concept of Chains originates from Python's LangChain (before the introduction of LCEL).
The idea is to have a `Chain` for each common use case, like a chatbot, RAG, etc.
Chains combine multiple low-level components and orchestrate interactions between them.
The main problem with them is that they are too rigid if you need to customize something.
LangChain4j has only two Chains implemented (`ConversationalChain` and `ConversationalRetrievalChain`),
and we do not plan to add more at this moment.

## AI Services

We propose another solution called AI Services, tailored for Java.
The idea is to hide the complexities of interacting with LLMs and other components behind a simple API.

This approach is very similar to Spring Data JPA or Retrofit: you declaratively define an interface with the desired API,
and LangChain4j provides an object (proxy) that implements this interface.
You can think of AI Service as a component of the service layer in your application.
It provides _AI_ services. Hence the name.

AI services handle the most common operations:
- Formatting inputs for the LLM
- Parsing outputs from the LLM

They also support more advanced features:
- Chat memory
- Tools
- RAG

Let's take a look at the simplest possible AI Service. After that, we will explore more complicated examples.

## Simplest AI Service

First, we define an interface with a single method, `chat`, which takes a `String` as input and returns a `String`.
```java
interface Assistant {

    String chat(String userMessage);
}
```

Then, we create our low-level components. These components will be used under the hood of our AI Service.
In this case, we just need the `ChatLanguageModel`:
```java
ChatLanguageModel model = OpenAiChatModel.withApiKey("demo");
```

Finally, we can use the `AiServices` class to create an instance of our AI Service:
```java
Assistant assistant = AiServices.create(Assistant.class, model);
```
:::note
In a Quarkus or Spring Boot application, this can be a bean that you can then inject into your code
wherever you need AI services.
:::

Now we can use `Assistant`:
```java
String answer = assistant.chat("Hello");
System.out.println(answer); // Hello, how can I help you?
```

## How does it work?

You provide the `Class` of your interface to `AiServices` along with the low-level components,
and `AiServices` creates a proxy object implementing this interface.
Currently, it uses reflection, but we are considering alternatives as well.
This proxy object handles all the conversions for inputs and outputs.
In this case, the input is a single `String`, but we are using a `ChatLanguageModel` which takes `ChatMessage` as input.
So, `AiService` will automatically convert it into a `UserMessage` and invoke `ChatLanguageModel`.
Since the output type of the `chat` method is a `String`, after `ChatLanguageModel` returns `AiMessage`,
it will be converted into a `String` before being returned from the `chat` method.

## @SystemMessage

Now, let's look at a more complicated example.
We'll force the LLM reply using slang 😉

This is usually achieved by providing instructions in the `SystemMessage`.

```java
interface Friend {

    @SystemMessage("You are a good friend of mine. Answer using slang.")
    String chat(String userMessage);
}

Friend friend = AiServices.create(Friend.class, model);

String answer = friend.chat("Hello"); // Hey! What's up?
```
In this example, we have added the `@SystemMessage` annotation with a prompt we want to use.
This will be converted into a `SystemMessage` behind the scenes and sent to the LLM along with the `UserMessage`.

## @UserMessage

Now, let's assume the model we use does not support system messages,
or maybe we just want to use `UserMessage` for that purpose.
```java
interface Friend {

    @SystemMessage("You are a good friend of mine. Answer using slang. {{it}}")
    String chat(String userMessage);
}

Friend friend = AiServices.create(Friend.class, model);

String answer = friend.chat("Hello"); // Hey! What's shakin'?
```
We have replaced the `@SystemMessage` annotation with `@UserMessage`
and specified a prompt template with the variable `it` to refer to the only method argument.

Additionally, it's possible to annotate the `String userMessage` with `@V`
and assign a custom name to the prompt template variable:
```java
interface Friend {

    @UserMessage("You are a good friend of mine. Answer using slang. {{message}}")
    String chat(@V("message") String userMessage);
}
```

## Output Parsing
If you want to receive a structured output from the LLM,
you can change the return type of your AI Service method from `String` to something else.
Currently, AI Services support the following return types:
- `String`
- `AiMessage`
- `Response<AiMessage>` (if you need to access `TokenUsage` or `FinishReason`)
- `boolean`/`Boolean` (if you need to get "yes" or "no" answer)
- `byte`/`Byte`
- `short`/`Short`
- `int`/`Integer`
- `long`/`Long`
- `BigInteger`
- `float`/`Float`
- `double`/`Double`
- `BigDecimal`
- `Date`
- `LocalDate`
- `LocalTime`
- `LocalDateTime`
- `List<String>` (if you want to get the answer in the form of a list of bullet points)
- `Set<String>`
- Any `Enum` (if you want to classify text, e.g. sentiment, user intent, etc)
- Any custom POJO

Unless the return type is `String`, `AiMessage`, or `Response<AiMessage>`,
the AI service will automatically append instructions to the end of `UserMessage` indicating the format
in which the LLM should respond.
Before the method returns, the AI Service will parse the output of the LLM into the desired type.

You can see the specific instructions by enabling logging for the model, for example:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey(...)
    .logRequests(true)
    .logResponses(true)
    .build();
```

Now let's take a look at some examples.

`Enum` and `boolean` as return types:
```java
enum Sentiment {
    POSITIVE, NEUTRAL, NEGATIVE
}

interface SentimentAnalyzer {

    @UserMessage("Analyze sentiment of {{it}}")
    Sentiment analyzeSentimentOf(String text);

    @UserMessage("Does {{it}} has a positive sentiment?")
    boolean isPositive(String text);
}

SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, model);

Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf("This is great!");
// POSITIVE

boolean positive = sentimentAnalyzer.isPositive("It's awful!");
// false
```

Custom POJO as a return type:
```java
class Person {
    String firstName;
    String lastName;
    LocalDate birthDate;
    Address address;
}

class Address {
    String street;
    Integer streetNumber;
    String city;
}

interface PersonExtractor {

    @UserMessage("Extract information about a person from {{it}}")
    Person extractPersonFrom(String text);
}

PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

String text = """
            In 1968, amidst the fading echoes of Independence Day,
            a child named John arrived under the calm evening sky.
            This newborn, bearing the surname Doe, marked the start of a new journey.
            He was welcomed into the world at 345 Whispering Pines Avenue
            a quaint street nestled in the heart of Springfield
            an abode that echoed with the gentle hum of suburban dreams and aspirations.
            """;

Person person = personExtractor.extractPersonFrom(text);

System.out.println(person); // // Person { firstName = "John", lastName = "Doe", birthDate = 1968-07-04, address = Address { ... } }
```

When extracting custom POJOs (actually JSON, which is then parsed into the POJO),
it is recommended to set a "json mode" in the model configuration.
This way, the LLM will be forced to produce valid JSON.

- For OpenAI:
```java
OpenAiChatModel.builder()
        ...
        .responseFormat("json_object")
        .build();
```
- For Azure OpenAI:
```java
AzureOpenAiChatModel.builder()
        ...
        .responseFormat(new ChatCompletionsJsonResponseFormat())
        .build();
```
- For Ollama:
```java
OllamaChatModel.builder()
        ...
        .format("json")
        .build();
```
- For other model providers: if the underlying model provider does not support "json mode",
try lowering the `temperature` and see if that helps.

[More examples](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/OtherServiceExamples.java)

## Streaming
[Example](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithStreamingExample.java)

## Chat Memory
[Example](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryExample.java)

## Tools
[Example](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)

## RAG
[Example](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithRetrieverExample.java)

## Auto-Moderation
[Example](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithAutoModerationExample.java)

## Chaining
The more complicated the logic of your LLM-powered application becomes,
the more important it is to decompose it into smaller parts, as we usually do in software development.
More granular parts are easier to develop, test, maintain, and reason about.

You can decompose your flow into multiple AI Services and use them
as you would with standard deterministic software components:
- Calling one AI Service after another.
- Using deterministic and LLM-powered (AI service can return `boolean`) `if`/`else` statements.
- Using deterministic and LLM-powered (AI service can return `enum`) `switch` statements.
- Using `for`/`while` loops.
- etc

TODO