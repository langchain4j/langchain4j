---
sidebar_position: 5
---

# Anthropic

[Anthropic](https://www.anthropic.com/)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic</artifactId>
    <version>0.27.1</version>
</dependency>
```

```java
ChatLanguageModel model = AnthropicChatModel.withApiKey(System.getenv("ANTHROPIC_API_KEY"));

Streing answer = model.generate("What is the capital of Germany?");

System.out.println(answer); // Berlin
```

More info is coming soon
