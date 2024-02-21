---
sidebar_position: 5
---

# Get Started

### Table of Contents

- [Basic Project Setup](#basic-project-setup)
- [Write a Hello World program](#write-a-hello-world-program)

## Basic Project Setup

:::note

Make sure you have Java 8+ installed. Verify it by typing in this command in your terminal:
```shell
java --version
```
:::
![Java](https://img.shields.io/badge/Java-8_+-blue.svg?style=for-the-badge&labelColor=gray)

## Write a "Hello World" program

The simplest way to begin is with the OpenAI integration.
LangChain4j offers integration with many LLMs.
Each integration has its own dependency.
In this case, we should add the OpenAI dependency:

- For Maven in `pom.xml`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.27.1</version>
</dependency>
```

- For Gradle in `build.gradle`:
```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:0.27.1'
```

Then, import your OpenAI API key.
It's recommended to store your API keys in environment variables to reduce the risk of exposing them publicly.
```java
String apiKey = System.getenv("OPENAI_API_KEY");
```
:::note
If you don't have your own OpenAI API key, don't worry.
You can temporarily use `demo` key, which we provide for free for demonstration purposes:
```java
String apiKey = "demo";
```
Be aware that when using the `demo` key, all requests to the OpenAI API go through our proxy,
which injects the real key before forwarding your request to the OpenAI API.
We do not collect or use your data in any way.
:::

Once you've set up the key, let's create an instance of an `OpenAiChatModel`:
```java
OpenAiChatModel model = OpenAiChatModel.withApiKey(apiKey);
```
Now, it is time to chat!
```java
String answer = model.generate("Say 'Hello World'");
System.out.println(answer); // Hello World
```

Find step-by-step tutorials with more complex examples [here](/docs/category/tutorials).

## Highlights

You can declaratively define concise "AI Services" that are powered by LLMs:

```java
interface Assistant {

    String chat(String userMessage);
}

Assistant assistant = AiServices.create(Assistant.class, model);

String answer = assistant.chat("Hello");
    
System.out.println(answer);
// Hello! How can I assist you today?
```

You can use LLM as a classifier:

```java
enum Sentiment {
    POSITIVE, NEUTRAL, NEGATIVE
}

interface SentimentAnalyzer {

    @UserMessage("Analyze sentiment of {{it}}")
    Sentiment analyzeSentimentOf(String text);

    @UserMessage("Does {{it}} have a positive sentiment?")
    boolean isPositive(String text);
}

SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, model);

Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf("It is good!");
// POSITIVE

boolean positive = sentimentAnalyzer.isPositive("It is bad!");
// false
```

You can easily extract structured information from unstructured data:

```java
class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
}

interface PersonExtractor {

    @UserMessage("Extract information about a person from {{text}}")
    Person extractPersonFrom(@V("text") String text);
}

PersonExtractor extractor = AiServices.create(PersonExtractor.class, model);

String text = "In 1968, amidst the fading echoes of Independence Day, "
            + "a child named John arrived under the calm evening sky. "
            + "This newborn, bearing the surname Doe, marked the start of a new journey.";

Person person = extractor.extractPersonFrom(text);
// Person { firstName = "John", lastName = "Doe", birthDate = 1968-07-04 }
```

You can provide tools that LLMs can use! Can be anything: retrieve information from DB, call APIs, etc.
See
example [here](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java).
