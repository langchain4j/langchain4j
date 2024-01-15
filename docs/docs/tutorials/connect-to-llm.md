---
sidebar_position: 3
---

# 1. Connect to an LLM

In this example we will connect to OpenAI's GPT-3.5 model using langchain4j, ask it questions and log the response.

First, set up you basic project with Java8+ and the desired langchain4j version as described in [Get Started](/docs/get-started).

### LLM Key
For this example we will connect to OpenAI API. To expose your key, create a class ```ApiKays.java``` with the following code

```java
public class ApiKeys {
    public static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
}
```

### 'Hello World' Code

Create a class and add the following code

```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class HelloWorld {
    public static void main(String[] args) {
        ChatLanguageModel model = OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY);

        String response = model.generate("Say 'Hello World'");
        System.out.println(response);

        String response2 = model.generate("What did I just ask?");
        System.out.println(response2);
    }
}
```

Running the program will generate a variant of the following output

<!-- TODO console log formatting? -->
```plaintext
Hello World! How can I assist you today?

You just asked, "what did I just ask you?"
```

### Explanation
We first set up a connection to our LLM, in this case we choose a model from OpenAI (```OpenAIChatModel```).
To connect to OpenAI's models via API, we need a key, that is taken from ```ApiKeys.OPENAI_API_KEY```.

```java
ChatLanguageModel model = OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY);
```

Next, we interact with the model using the ```generate``` method:

```java
String response = model.generate("Say 'Hello World'");
```

This will return a String with the LLM's answer. 
Note that the API itself does not retain your former prompts. In [Chat (Memory)](chat) you will learn how to pass along your chat history, so the LLM knows what has been said before. If you don't pass the chat history, like in this simple example, the LLM will not be able to correctly answer the second question ('What did I just ask?').

A lot of parameters are set behind the scenes, such as timeout, model type and model parameters.
In [Set Model Parameters](set-model-parameters) you will learn how to set these parameters explicitly.

If you want to use another LLM, langchain4j has integrations with all major model providers (Google Vertex and Gemini, Azure OpenAI, ...) and (local) model wrappers (HuggingFace, Ollama, LocalAI).
All integrations with examples and tutorials are listed under [Integrations](/docs/category/integrations).

In this easy example, the output is returned as a complete String. If you want to print the output token by token, use [Streaming](response-streaming).