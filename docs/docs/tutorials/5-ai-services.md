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

AI Services currently support:
- Prompt templating
- Interactions with LLMs
- Output parsing
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

Then, we create our low-level components. These components will be used behind the scenes by our AI Service.
```java
ChatLanguageModel model = OpenAiChatModel.withApiKey("demo");
```

Finally, we can use the `AiServices` class to create an instance of our AI Service.
In a Quarkus or Spring Boot application, this can be a bean that you can then inject into your code
wherever you need AI services.
```java
Assistant assistant = AiServices.create(Assistant.class, model);
```

Now we can use `Assistant` as we wish:
```java
String answer = assistant.chat("Hello"); // Hello, how can I help you?
```

## How does it work?

You provide the `Class` of your interface to `AiServices` along with the low-level components,
and `AiServices` creates a proxy object implementing this interface.
Currently, it uses reflection, but we are exploring other options as well.
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

interface Bro {

    @SystemMessage("Answer using slang")
    String chat(String userMessage);
}

    Bro bro = AiServices.create(Bro.class, model);

    String answer = bro.chat("Hello"); // Hey! What's up?
```
In this example, we have added the `@SystemMessage` annotation with a prompt we want to use.
This will be converted into a `SystemMessage` behind the scenes and sent to the LLM along with the `UserMessage`.

## @UserMessage

Now, let's assume the model we use does not support system messages,
or maybe we just want to use `UserMessage` for that purpose.
```java

interface Bro {

    @UserMessage("Answer using slang. {{it}}")
    String chat(String userMessage);
}

    Bro bro = AiServices.create(Bro.class, model);

    String answer = bro.chat("Hello"); // Hey! What's shakin'?
```
We have replaced the `@SystemMessage` annotation with `@UserMessage`
and specified a prompt template with the variable `it` to refer to the only method argument.

Additionally, it's possible to annotate the `String userMessage` with `@V`
and assign a custom name to the prompt template variable:
```java
interface Bro {

    @UserMessage("Answer using slang.\n{{message}}")
    String chat(@V("message") String userMessage);
}
```

## More Examples
- [Simple](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/SimpleServiceExample.java)
- [With Memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryExample.java)
- [With Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)
- [With Streaming](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithStreamingExample.java)
- [With Retriever](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithRetrieverExample.java)
- [With Auto-Moderation](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithAutoModerationExample.java)
- [With Structured Outputs, Structured Prompts, etc](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/OtherServiceExamples.java)
