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

Latest version of LangChain4j: 0.26.1

![Maven Central](https://img.shields.io/maven-central/v/dev.langchain4j/langchain4j?style=for-the-badge&labelColor=gray)

To add langchain4j to your java project, add the following dependency:

- For Maven project `pom.xml`

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>{your-version}</version> <!-- Specify your version here -->
</dependency>
```

- For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:{your-version}'
```



## Write a Hello World program

The easiest way to get started is with the OpenAI API integration. 

Next to your java classes, create a class ```ApiKeys``` where you expose you OpenAI API key:
```java
public class ApiKeys {
    public static final String OPENAI_API_KEY = System.getenv("your key goes here");
}
```

:::note
If you don't have a key for OpenAI API, you can temporarily use "demo" as a key to try out langchain4j
:::

Once you've set the key up, create this Java class and run it:

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class HelloWorldExample {

    public static void main(String[] args) {

        // Create an instance of a model
        ChatLanguageModel model = OpenAiChatModel
                .withApiKey(ApiKeys.OPENAI_API_KEY);

        // Start interacting
        String answer = model.generate("Hello world!");

        System.out.println(answer); // Hello! How can I assist you today?
    }
}
```

Alternatively, specify your OpenAI API key as the environment variable `OPENAI_API_KEY`.

```shell
export OPENAI_API_KEY=sk-<the-rest-of-your-key>
```

and load it into your java class as follows

```java
String key = System.getenv("OPENAI_API_KEY");
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

