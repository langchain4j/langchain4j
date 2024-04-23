---
sidebar_position: 5
---

# Get Started

## Prerequisites
:::note
Ensure you have Java 8 or higher installed. Verify it by typing this command in your terminal:
```shell
java --version
```
:::

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
    <version>0.30.0</version>
</dependency>
```

- For Gradle in `build.gradle`:
```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:0.30.0'
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
