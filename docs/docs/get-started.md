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
    <version>0.36.0</version>
</dependency>
```

If you wish to use a high-level [AI Services](/tutorials/ai-services) API, you will also need to add 
the following dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.36.0</version>
</dependency>
```

- For Gradle in `build.gradle`:
```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:0.36.0'
implementation 'dev.langchain4j:langchain4j:0.36.0'
```

<details>
<summary>Bill of Materials (BOM)</summary>

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>0.36.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
</details>

<details>
<summary>SNAPSHOT dependencies (newest features)</summary>

If you'd like to test the newest features before their official release,
you can use the most recent SNAPSHOT dependency:
```xml
<repositories>
    <repository>
        <id>snapshots-repo</id>
        <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.37.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```
</details>

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
The `demo` key has a quota, is restricted to the `gpt-4o-mini` model, and should only be used for demonstration purposes.
:::

Once you've set up the key, let's create an instance of an `OpenAiChatModel`:
```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName(GPT_4_O_MINI)
    .build();
```
Now, it is time to chat!
```java
String answer = model.generate("Say 'Hello World'");
System.out.println(answer); // Hello World
```
