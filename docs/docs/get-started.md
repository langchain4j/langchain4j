---
sidebar_position: 5
---

# Get Started

:::note
If you are using Quarkus, see [Quarkus Integration](/tutorials/quarkus-integration/).

If you are using Spring Boot, see [Spring Boot Integration](/tutorials/spring-boot-integration).
:::

LangChain4j offers [integration with many LLM providers](/integrations/language-models/).
Each integration has its own maven dependency.
The simplest way to begin is with the OpenAI integration:

- For Maven in `pom.xml`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.34.0</version>
</dependency>
```

If you wish to use a high-level [AI Services](/tutorials/ai-services) API, you will also need to add 
the following dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.34.0</version>
</dependency>
```

- For Gradle in `build.gradle`:
```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:0.34.0'
implementation 'dev.langchain4j:langchain4j:0.34.0'
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
The `demo` key has a quota and should only be used for demonstration purposes.
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
