---
sidebar_position: 5
---

# Get Started

:::note
If you are using Quarkus, see [Quarkus Integration](/tutorials/quarkus-integration/).

If you are using Spring Boot, see [Spring Boot Integration](/tutorials/spring-boot-integration).

If you are using Helidon, see [Helidon Integration](/tutorials/helidon-integration)
:::

LangChain4j offers integrations with many [LLM providers](/integrations/language-models/),
[embedding/vector stores](/integrations/embedding-stores), etc.
Each integration has its own maven dependency.

The minimum supported JDK version is 17.

As an example, let's import the OpenAI dependency:

- For Maven in `pom.xml`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.0.1</version>
</dependency>
```

If you wish to use a high-level [AI Services](/tutorials/ai-services) API, you will also need to add 
the following dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.1</version>
</dependency>
```

- For Gradle in `build.gradle`:
```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:1.0.1'
implementation 'dev.langchain4j:langchain4j:1.0.1'
```

<details>
<summary>Bill of Materials (BOM)</summary>

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

:::note
Please note that `langchain4j-bom` always contains the latest versions of all LangChain4j modules.
:::

:::note
Please note that while the `langchain4j-bom` version is `1.0.1`,
many of the modules still have version `1.0.1-beta{n}`,
so there might be some breaking changes for these modules in the furture.
:::
</details>

<details>
<summary>SNAPSHOT dependencies (newest features)</summary>

If you'd like to test the newest features before their official release,
you can use the most recent `SNAPSHOT` dependency:
```xml
<repositories>
  <repository>
    <name>Central Portal Snapshots</name>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>1.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```
</details>

Then, import your OpenAI API key.
It's recommended to store your API keys in environment variables to reduce the risk of exposing them publicly.
```java
String apiKey = System.getenv("OPENAI_API_KEY");
```

<details>
<summary>What if I don't have an API key?</summary>

If you don't have your own OpenAI API key, don't worry.
You can temporarily use `demo` key, which we provide for free for demonstration purposes.
Be aware that when using the `demo` key, all requests to the OpenAI API need to go through our proxy,
which injects the real key before forwarding your request to the OpenAI API.
We do not collect or use your data in any way.
The `demo` key has a quota, is restricted to the `gpt-4o-mini` model, and should only be used for demonstration purposes.

```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .baseUrl("http://langchain4j.dev/demo/openai/v1")
    .apiKey("demo")
    .modelName("gpt-4o-mini")
    .build();
```
</details>

Once you've set up the key, let's create an instance of an `OpenAiChatModel`:
```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-4o-mini")
    .build();
```
Now, it is time to chat!
```java
String answer = model.chat("Say 'Hello World'");
System.out.println(answer); // Hello World
```
