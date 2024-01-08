---
sidebar_position: 2
---

# Write a Hello World program!

:::note

Before you proceed, make sure you have Java 8+ and Maven installed. Verify it by typing in these commands in your terminal:

```shell
java --version
mvn --version
```

:::

Easiest way to get started is with OpenAI integration. So, you'll need to setup an OpenAI account.
Then, specify your OpenAPI key as the environment variable `OPENAI_API_KEY`.

```shell
export OPENAI_API_KEY=sk-<the-rest-of-your-key>
```

Once you've setup OpenAPI key, create this Java class and run it.

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class HelloWorldExample {

    public static void main(String[] args) {

        // Create an instance of a model
        ChatLanguageModel model = OpenAiChatModel
                .withApiKey(System.getenv("OPENAI_API_KEY");

        // Start interacting
        AiMessage answer = model.sendUserMessage("Hello world!");

        System.out.println(answer.text()); // Hello! How can I assist you today?
    }
}
```

Find more examples [here](/docs/category/code-examples).