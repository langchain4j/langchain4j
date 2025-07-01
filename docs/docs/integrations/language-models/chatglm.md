---
sidebar_position: 4
---

# ChatGLM

https://github.com/THUDM/ChatGLM-6B

ChatGLM is an open bilingual dialogue language model which is released by Tsinghua University.

For ChatGLM2, ChatGLM3 and GLM4, their API are compatible with OpenAI. you can refer to `langchain4j-zhipu-ai` or use `langchain4j-open-ai`.

## Maven Dependency

:::note
Since `1.0.0-alpha1`, `langchain4j-chatglm` has migrated to `langchain4j-community` and is renamed to `langchain4j-community-chatglm`.
:::

Before `1.0.0-alpha1`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chatglm</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-chatglm</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <typ>pom</typ>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```


## APIs

You can instantiate `ChatGlmChatModel` using following code:

```java
ChatModel model = ChatGlmChatModel.builder()
        .baseUrl(System.getenv("CHATGLM_BASE_URL"))
        .logRequests(true)
        .logResponses(true)
        .build();
```

Now you can use it like a normal `ChatModel`.

:::note
`ChatGlmChatModel` does not support Function Calling and Structured Output. see [index](index.md)
:::

## Examples

- [ChatGlmChatModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-chatglm/src/test/java/dev/langchain4j/community/model/chatglm/ChatGlmChatModelIT.java)
