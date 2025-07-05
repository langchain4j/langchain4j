---
title: OpenAI-Compatible Language Models
sidebar_position: 99 # Adjust as needed
---

# OpenAI-Compatible Language Models

Langchain4j's OpenAI module can be used with various OpenAI-compatible APIs, including local and cloud-hosted solutions. This page provides guidance on setting up and using these providers.

## Groq

**Deployment:** SaaS

**Key Required:** Yes (API Key)

**Description:** Groq offers high-performance inference for LLMs.

**Setup:**
To use Groq, you'll need an API key from [GroqCloud](https://console.groq.com/keys).

Configure Langchain4j's `OpenAiChatModel` or `OpenAiStreamingChatModel`:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("https://api.groq.com/openai/v1")
        .apiKey(System.getenv("GROQ_API_KEY")) // Or your actual key
        .modelName("llama3-8b-8192") // Or any other model offered by Groq, e.g., mixtral-8x7b-32768, llama3-70b-8192
        .temperature(0.0)
        .build();
```
You can find available model names on the [Groq models page](https://console.groq.com/docs/models).

**Examples:**
There are no specific Groq examples in the `langchain4j-examples` repository yet. However, standard OpenAI examples can be adapted by changing the `baseUrl`, `apiKey`, and `modelName`.
<!-- TODO: Add a specific Groq example to langchain4j-examples -->

## Docker Model Runner

**Deployment:** Local

**Key Required:** No (for local models, but API key parameter might be mandatory)

**Description:** Docker Model Runner allows you to run LLMs locally using Docker containers. This is useful for development, testing, or offline use. It often wraps technologies like Ollama or other model servers.

**Setup:**
Follow the instructions in this [Medium article](https://medium.com/@lize.raes/local-ai-in-java-langchain4j-with-docker-model-runner-7d73a57db660) to set up Docker Model Runner.

In essence, you need to:
1. Install Docker.
2. Run the desired model container (e.g., `quay.io/ollama/ollama:latest` and then pull a model, or a specific model runner like `chonig/docker-gpt4all-api:latest`). The exact command depends on the model runner you choose.
3. Configure the `OpenAiChatModel` or `OpenAiStreamingChatModel` in Langchain4j to point to the local endpoint exposed by the container.

Example for an Ollama-based Docker runner:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:11434/v1/") // Default Ollama API port, adjust if your Docker setup exposes a different one
        .apiKey("whatever") // Required by the API, but not validated by many local runners
        .modelName("mistral") // The model you are running via Ollama inside Docker
        .logRequests(true)
        .logResponses(true)
        .build();
```

Example for a GPT4All-based Docker runner (like `chonig/docker-gpt4all-api`):
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:8080/v1") // Port exposed by chonig/docker-gpt4all-api
        .apiKey("whatever")
        .modelName("GPT4All-J Vulcanic (ggml)") // Specify a model compatible with the runner
        .logRequests(true)
        .logResponses(true)
        .build();
```
Always check the documentation of the specific Docker image you are using for the correct `baseUrl` and how to specify models.

**Examples:**
See the [langchain4j-examples repository](https://github.com/langchain4j/langchain4j-examples) for general OpenAI examples that can be adapted.
<!-- TODO: Add a specific Docker Model Runner example to langchain4j-examples -->

## GPT4All

**Deployment:** Local

**Key Required:** No (but API key parameter might be mandatory)

**Description:** GPT4All provides a desktop application to run open-source LLMs locally on your machine. It can also expose an OpenAI-compatible API.

**Setup:**
1. Download and install GPT4All from [https://gpt4all.io/](https://gpt4all.io/).
2. Launch GPT4All and download the desired model(s) through its UI.
3. Enable the "Web Server" mode in GPT4All settings (usually under the "Settings" or "Server" tab). This will start a local server.
4. Note the IP address and port displayed in GPT4All (typically `http://localhost:4891/v1`).
5. Configure Langchain4j:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:4891/v1") // Default GPT4All API endpoint
        .apiKey("whatever") // Required by the API, but not validated by GPT4All
        // .modelName("...") // The model name might be derived from the model loaded in GPT4All UI or configurable. Check GPT4All docs.
        .logRequests(true)
        .logResponses(true)
        .build();
```

**Examples:**
<!-- TODO: Link to GPT4All examples if available, otherwise remove or add a TODO for creating them -->
General OpenAI examples from `langchain4j-examples` can be adapted.

## Ollama

**Deployment:** Local

**Key Required:** No (but API key parameter might be mandatory)

**Description:** Ollama allows you to run open-source large language models, such as Llama 3, Mistral, and others, locally. It provides an OpenAI-compatible API endpoint.

**Setup:**
1. Install Ollama from [https://ollama.ai/](https://ollama.ai/).
2. Pull a model using the command line: `ollama pull <model_name>` (e.g., `ollama pull mistral`).
3. Ensure Ollama is running. It typically serves an OpenAI-compatible API at `http://localhost:11434/v1/`.
4. Configure Langchain4j:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:11434/v1/")
        .apiKey("whatever") // Required by the API, but not validated by Ollama
        .modelName("mistral") // Specify the model you pulled and want to use
        .temperature(0.0)
        .logRequests(true)
        .logResponses(true)
        .build();
```
While Langchain4j has a dedicated `langchain4j-ollama` module (see [Ollama docs](./ollama.md)), you can also use the OpenAI module to connect to Ollama's OpenAI-compatible endpoint as shown above.

**Examples:**
*   Using the dedicated Ollama module: [langchain4j-examples/.../OllamaChatModelExamples.java](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/dev/langchain4j/model/ollama/OllamaChatModelExamples.java)
*   For OpenAI-compatible endpoint usage, adapt general OpenAI examples.

## LM Studio

**Deployment:** Local

**Key Required:** No (but API key parameter might be mandatory)

**Description:** LM Studio provides a UI to discover, download, and run local LLMs. It also features an OpenAI-compatible local server.

**Setup:**
1. Download and install LM Studio from [https://lmstudio.ai/](https://lmstudio.ai/).
2. Download your desired model(s) through the LM Studio UI (Search tab).
3. Go to the "Local Server" tab (usually an icon like `<->` on the left).
4. Select a model to load from the dropdown at the top.
5. Click "Start Server". Note the address and port the server is running on (e.g., `http://localhost:1234/v1`).
6. Configure Langchain4j:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .baseUrl("http://localhost:1234/v1/") // Use the address from LM Studio server tab
        .apiKey("whatever") // Required by the API, but not validated by LM Studio
        // .modelName("...") // Model is typically selected in LM Studio UI before starting the server.
                           // The server endpoint might reflect the loaded model or be generic.
                           // Check LM Studio documentation if a specific model name is needed here.
        .logRequests(true)
        .logResponses(true)
        .build();
```

**Examples:**
<!-- TODO: Link to LM Studio examples if available, otherwise remove or add a TODO for creating them -->
General OpenAI examples from `langchain4j-examples` can be adapted.

## Other OpenAI-Compatible APIs

Many other services and tools expose OpenAI-compatible APIs. The general approach to using them with Langchain4j is:

1.  **Identify the Base URL:** Find the API endpoint for the service. This often ends in `/v1`.
2.  **Obtain an API Key:** If the service requires authentication, get an API key. If the service is local and doesn't require a key, you might still need to provide a placeholder string like "whatever" or "no-key" as the `apiKey` parameter in Langchain4j, as the underlying OpenAI client might expect it.
3.  **Specify the Model Name:** Determine the correct model name to use for the service. This is often required.
4.  **Configure `OpenAiChatModel` or `OpenAiStreamingChatModel`:**

    ```java
    ChatLanguageModel model = OpenAiChatModel.builder()
            .baseUrl("YOUR_API_BASE_URL") // e.g., "http://localhost:8000/v1"
            .apiKey("YOUR_API_KEY_OR_PLACEHOLDER") // e.g., "sk-yourkey" or "none"
            .modelName("MODEL_NAME_AS_PER_PROVIDER_DOCS") // e.g., "gpt-3.5-turbo" or custom name
            // Add other configurations like temperature, timeout, etc. as needed
            .logRequests(true)
            .logResponses(true)
            .build();
    ```

Always refer to the specific documentation of the OpenAI-compatible service you are using for the exact Base URL, API key requirements, and available model names. Some local servers might also provide model listings at an endpoint like `/v1/models`.
