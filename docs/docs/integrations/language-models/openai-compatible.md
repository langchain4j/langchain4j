---
title: OpenAI-Compatible Language Models
sidebar_position: 17
---

# OpenAI-Compatible Language Models

Many services and tools expose OpenAI-compatible APIs. The general approach to using them with LangChain4j is:

1.  **Identify the Base URL:** Find the API endpoint for the service. This often ends in `/v1`.
2.  **Obtain an API Key:** If the service requires authentication, get an API key. If the service is local and doesn't require a key, put a placeholder as the `apiKey` parameter.
3.  **Specify the Model Name:** Determine the correct model name to use for the service. This is often required.
4.  **Configure `OpenAiChatModel` or `OpenAiStreamingChatModel`:**

    ```java
    ChatModel model = OpenAiChatModel.builder()
            .baseUrl("YOUR_API_BASE_URL") // e.g., "http://localhost:8000/v1"
            .apiKey("YOUR_API_KEY_OR_PLACEHOLDER") // e.g., "sk-yourkey" or "none"
            .modelName("MODEL_NAME_AS_PER_PROVIDER_DOCS") // e.g., "gpt-3.5-turbo" or custom name
            // Add other configurations like temperature, timeout, etc. as needed
            .logRequests(true)
            .logResponses(true)
            .build();
    ```
Below we provide specific examples for popular OpenAI-compatible APIs, including Groq, Docker Model Runner, GPT4All, Ollama, and LM Studio.

### Contents:
- [Prerequisites for Using OpenAI-Compatible Language Models](#prerequisites-for-using-openai-compatible-language-models)
- [Groq](#groq)
- [Docker Model Runner](#docker-model-runner)
- [GPT4All](#gpt4all)
- [Ollama](#ollama)
- [LM Studio](#lm-studio)

## Prerequisites for Using OpenAI-Compatible Language Models

LangChain4j's OpenAI module can be used with various OpenAI-compatible APIs, including local and cloud-based solutions. For each of the models below, we show how to create a `ChatModel` that you can then use to chat with the model, just like in the [standard OpenAI examples](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiChatModelExamples.java).

First, make sure you have the OpenAI module in your `pom.xml` or Gradle build file:

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.4.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.4.0-beta10</version>
</dependency>
```

## Groq

**Deployment:** SaaS (Key Required)

**Description:** Groq offers very fast inference for LLMs.

**Setup:**
To use Groq, you'll need an API key from [GroqCloud](https://console.groq.com/keys).

Configure LangChain4j's `OpenAiChatModel` or `OpenAiStreamingChatModel`:
```java
ChatModel model = OpenAiChatModel.builder()
        .baseUrl("https://api.groq.com/openai/v1")
        .apiKey(System.getenv("GROQ_API_KEY")) // Or your actual key
        .modelName("llama3-8b-8192") // Or any other model offered by Groq, e.g., mixtral-8x7b-32768, llama3-70b-8192
        .temperature(0.0)
        .build();
```
You can find available model names on the [Groq models page](https://console.groq.com/docs/models).

## Docker Model Runner

**Deployment:** Local

**Description:** Docker Model Runner allows you to run LLMs locally using Docker desktop (uses `llama.cpp` under the hood and can use your CPU). This is useful for development, testing, or offline use. Works on Mac and Windows.

**Setup:**

1. Have Docker Desktop installed
2. Enable the Docker Model Runner feature in Docker Desktop (Settings > Experimental Features > Enable Docker Model Runner)
3. Just below that, check "Enable host-side TCP support".
4. Pull a model using the Docker Model Runner CLI, e.g., `docker model pull ai/qwen3` or any other model from [this list](https://hub.docker.com/u/ai).

Example for `ai/qwen3` (more info about the model [here](https://hub.docker.com/r/ai/qwen3)):

```java
ChatModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:12434/engines/llama.cpp/v1")
        .modelName("ai/qwen3")
        .build();
```
Some models support tool calling, see details on the docker model page.

## GPT4All

**Deployment:** Local

**Description:** GPT4All provides a desktop application to run open-source LLMs locally on your machine. It can also expose an OpenAI-compatible API.

**Setup:**
1. Download and install GPT4All from [https://gpt4all.io/](https://gpt4all.io/).
2. Launch GPT4All and download the desired model(s) through its UI, eg. `llama-3.2-1b-instruct`.
3. Enable the "Web Server" mode in GPT4All settings ("Settings" > "Application" > under Advanced: "Enable Local API Server").
4. Note the IP address and port displayed in GPT4All (typically `http://localhost:4891/v1`).
5. Configure LangChain4j:
```java
ChatModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:4891/v1")
        .modelName("llama-3.2-1b-instruct") // The model name might be derived from the model loaded in GPT4All UI or configurable. Check GPT4All docs.
        .build();
```

## Ollama

While LangChain4j has a dedicated `langchain4j-ollama` module (see [Ollama docs](./ollama.md)), you can also use the OpenAI module to connect to Ollama's OpenAI-compatible endpoint as shown above.

**Deployment:** Local

**Description:** Ollama allows you to run open-source large language models, such as Llama 3, Mistral, and others, locally. It provides an OpenAI-compatible API endpoint.

**Setup:**
1. Install Ollama from [https://ollama.ai/](https://ollama.ai/).
2. Pull a model using the command line: `ollama pull <model_name>` (e.g., `ollama pull gemma3`).
3. Ensure Ollama is running. It serves an OpenAI-compatible API at `http://localhost:11434/v1/`.
4. Configure LangChain4j:
```java
ChatModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:11434/v1/")
        .modelName("gemma3")
        .build();
```

**Examples:**
*   For OpenAI-compatible endpoint usage, adapt general OpenAI examples.
*   Using the dedicated Ollama module: [langchain4j-examples/.../OllamaChatModelExamples.java](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/dev/langchain4j/model/ollama/OllamaChatModelExamples.java)


## LM Studio

**Deployment:** Local

**Description:** LM Studio provides a UI to discover, download, and run local LLMs. It also features an OpenAI-compatible local server.

**Setup:**
1. Download and install LM Studio from [https://lmstudio.ai/](https://lmstudio.ai/).
2. Download your desired model(s) through the LM Studio UI (Search tab), for example `smollm2-135m-instruct`.
3. Go to the "Developer" tab (icon like `>_` on the left) and toggle the server status on to 'running'
4. When the server runs, you get to see the address on the top right (e.g., `http://127.0.0.1:1234`). Alternatively, the cURL call will give you the full URL.
5. LMStudio as for now does not support HTTP2, hence we need to enforce the use of HTTP1.1. For that, we need to add the correct maven or gradle dependency:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-http-client-jdk</artifactId>
    <version>1.4.0</version>
</dependency>
```
6. Configure LangChain4j and specify the `httpClientBuilder`
```java
import java.net.http.HttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;

...

HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1) ;

JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
        .httpClientBuilder(httpClientBuilder);

ChatModel model = OpenAiChatModel.builder()
        .baseUrl("http://127.0.0.1:1234/v1")
        .modelName("smollm2-135m-instruct")
        .httpClientBuilder(jdkHttpClientBuilder)
        .build();
```

