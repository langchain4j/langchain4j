---
sidebar_position: 11
---

# MistralAI
[MistralAI Documentation](https://docs.mistral.ai/)

## Project setup

To install langchain4j to your project, add the following dependency:

For Maven project `pom.xml`

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.33.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mistral-ai</artifactId>
    <version>0.33.0</version>
</dependency>
```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:0.33.0'
implementation 'dev.langchain4j:langchain4j-mistral-ai:0.33.0'
```
### API Key setup
Add your MistralAI API key to your project, you can create a class ```ApiKeys.java``` with the following code

```java
public class ApiKeys {
    public static final String MISTRALAI_API_KEY = System.getenv("MISTRAL_AI_API_KEY");
}
```
Don't forget set your API key as an environment variable.
```shell
export MISTRAL_AI_API_KEY=your-api-key #For Unix OS based
SET MISTRAL_AI_API_KEY=your-api-key #For Windows OS
```
More details on how to get your MistralAI API key can be found [here](https://docs.mistral.ai/#api-access)

### Model Selection
You can use `MistralAiChatModelName` and `MistralAiCodeModelName` java enums to found appropriate model names for your use case.
MistralAI updated a new selection and classification of models according to performance and cost trade-offs.

| Model  name           | Deployment or available from                                                                                                                  | Description                                                                                                                                                                                                                                                                                                              |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| open-mistral-7b       | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource**<br/>The first dense model released by Mistral AI, <br/> perfect for experimentation, <br/> customization, and quick iteration. <br/><br/>Max tokens 32K<br/><br/>Java Enum<br/>`MistralAiChatModelName.OPEN_MISTRAL_7B`                                                                                   |
| open-mixtral-8x7b     | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource**<br/>Ideal to handle multi-languages operations, <br/> code generationand fine-tuned.<br/> Excellent cost/performance trade-offs. <br/><br/>Max tokens 32K<br/><br/>Java Enum<br/>`MistralAiChatModelName.OPEN_MIXTRAL_8x7B`                                                                               |
| open-mixtral-8x22b    | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource**<br/>It has all Mixtral-8x7B capabilities plus strong maths <br/> and coding natively capable of function calling <br/><br/>Max tokens 64K.<br/><br/>Java Enum<br/>`MistralAiChatModelName.OPEN_MIXTRAL_8X22B`                                                                                             |
| open-mistral-nemo     | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource**<br/>A 12B model built in collaboration with NVIDIA. <br/>Its reasoning, world knowledge, and coding accuracy are state-of-the-art in its size category.<br/><br/>Max tokens 128K.<br/><br/>Java Enum<br/>`MistralAiChatModelName.OPEN_MISTRAL_NEMO`                                                       |
| open-codestral-mamba  | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource**<br/>A Mamba2 language model specialised in code generation. <br/>It was trained with advanced code and reasoning capabilities, enabling it to perform on par with SOTA transformer-based models.<br/><br/>Max tokens 256K.<br/><br/>Java Enum<br/>`MistralAiCodeModelName.OPEN_CODESTRAL_MAMBA`           |
| mistral-small-latest  | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).                                                                          | **Commercial**<br/>Suitable for simple tasks that one can do in bulk <br/>(Classification, Customer Support, or Text Generation).<br/><br/>Max tokens 32K<br/><br/>Java Enum<br/>`MistralAiChatModelName.MISTRAL_SMALL_LATEST`                                                                                           |
| mistral-medium-latest | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).                                                                          | **Commercial**<br/>Ideal for intermediate tasks that require moderate <br/> reasoning (Data extraction, Summarizing, <br/>Writing emails, Writing descriptions.<br/><br/>Max tokens 32K<br/><br/>Java Enum<br/>`MistralAiChatModelName.MISTRAL_MEDIUM_LATEST`                                                            |
| mistral-large-latest  | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).                                                                          | **Commercial**<br/>Ideal for complex tasks that require large reasoning <br/> capabilities or are highly specialized <br/>(Text Generation, Code Generation, RAG, or Agents).<br/><br/>Max tokens 128K<br/><br/>Java Enum<br/>`MistralAiChatModelName.MISTRAL_LARGE_LATEST`                                              |
| mistral-embed         | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).                                                                          | **Commercial**<br/>Converts text into numerical vectors of <br/> embeddings in 1024 dimensions. <br/>Embedding models enable retrieval and RAG applications.<br/><br/>Max tokens 8K<br/><br/>Java Enum<br/>`MistralAiEmbeddingModelName.MISTRAL_EMBED`                                                                   |
| codestral-latest      | - Mistral AI La Plateforme.<br/>- Cloud platforms (Azure, AWS, GCP).<br/>- Hugging Face.<br/>- Self-hosted (On-premise, IaaS, docker, local). | **OpenSource (Non-production license) and Commercial**<br/>A cutting-edge generative model that has been specifically designed <br/>and optimized for code generation tasks, including fill-in-the-middle and code completion. <br/><br/>Max tokens 32K<br/><br/>Java Enum<br/>`MistralAiCodeModelName.CODESTRAL_LATEST` |

`@Deprecated` models:
- mistral-tiny (`@Deprecated`)
- mistral-small (`@Deprecated`)
- mistral-medium (`@Deprecated`)

You can find more detail and types of use cases with their respective Mistral model [here](https://docs.mistral.ai/#model-selection)

## Chat Completion
The chat models allow you to generate human-like responses with a model fined-tuned on conversational data.

### Synchronous
Create a class and add the following code.

```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;

public class HelloWorld {
    public static void main(String[] args) {
        ChatLanguageModel model = MistralAiChatModel
                .withApiKey(ApiKeys.MISTRALAI_API_KEY);

        String response = model.generate("Say 'Hello World'");
        System.out.println(response);
    }
}
```
Running the program will generate a variant of the following output

```plaintext
Hello World! How can I assist you today?
```

### Streaming
Create a class and add the following code.

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

public class HelloWorld {
    public static void main(String[] args) {
        MistralAiStreamingChatModel model = MistralAiStreamingChatModel
                .withApiKey(ApiKeys.MISTRALAI_API_KEY);

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();         
        model.generate("Tell me a joke about Java", new StreamingResponseHandler() {
            @Override
            public void onNext(String token) {
                System.out.print(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }    
        });

        futureResponse.join();
    }
}
```
You will receive each chunk of text (token) as it is generated by the LLM on the `onNext` method.

You can see that output below is streamed in real-time.

```plaintext
"Why do Java developers wear glasses? Because they can't C#"
```

Of course, you can combine MistralAI chat completion with other features like [Set Model Parameters](/tutorials/model-parameters) and [Chat Memory](/tutorials/chat-memory) to get more accurate responses.

In [Chat Memory](/tutorials/chat-memory) you will learn how to pass along your chat history, so the LLM knows what has been said before. If you don't pass the chat history, like in this simple example, the LLM will not know what has been said before, so it won't be able to correctly answer the second question ('What did I just ask?').

A lot of parameters are set behind the scenes, such as timeout, model type and model parameters.
In [Set Model Parameters](/tutorials/model-parameters) you will learn how to set these parameters explicitly.

### Function Calling
Function calling allows Mistral chat models ([synchronous](#synchronous) and [streaming](#streaming)) to connect to external tools. For example, you can call a `Tool` to get the payment transaction status as shown in the Mistral AI function calling [tutorial](https://docs.mistral.ai/guides/function-calling/).

<details>
<summary>What are the supported mistral models?</summary>

:::note
Currently, function calling is available for the following models:

- Mistral Small `MistralAiChatModelName.MISTRAL_SMALL_LATEST`
- Mistral Large `MistralAiChatModelName.MISTRAL_LARGE_LATEST`
- Mixtral 8x22B `MistralAiChatModelName.OPEN_MIXTRAL_8X22B`
- Mistral Nemo `MistralAiChatModelName.OPEN_MISTRAL_NEMO`
:::
</details>

#### 1. Define a `Tool` class and how get the payment data

Let's assume you have a dataset of payment transaction like this. In real applications you should inject a database source or REST API client to get the data.
```java
import java.util.*;

public class PaymentTransactionTool {

   private final Map<String, List<String>> paymentData = Map.of(
            "transaction_id", Arrays.asList("T1001", "T1002", "T1003", "T1004", "T1005"),
            "customer_id", Arrays.asList("C001", "C002", "C003", "C002", "C001"),
            "payment_amount", Arrays.asList("125.50", "89.99", "120.00", "54.30", "210.20"),
            "payment_date", Arrays.asList("2021-10-05", "2021-10-06", "2021-10-07", "2021-10-05", "2021-10-08"),
            "payment_status", Arrays.asList("Paid", "Unpaid", "Paid", "Paid", "Pending"));
   
    ...
}
```
Next, let's define two methods `retrievePaymentStatus` and `retrievePaymentDate` to get the payment status and payment date from the `Tool` class.

```java
// Tool to be executed to get payment status
@Tool("Get payment status of a transaction") // function description
String retrievePaymentStatus(@P("Transaction id to search payment data") String transactionId) {
    return getPaymentData(transactionId, "payment_status");
}

// Tool to be executed to get payment date
@Tool("Get payment date of a transaction") // function description
String retrievePaymentDate(@P("Transaction id to search payment data") String transactionId) {
   return getPaymentData(transactionId, "payment_date");
}

private String getPaymentData(String transactionId, String data) {
    List<String> transactionIds = paymentData.get("transaction_id");
    List<String> paymentData = paymentData.get(data);

    int index = transactionIds.indexOf(transactionId);
    if (index != -1) {
        return paymentData.get(index);
    } else {
        return "Transaction ID not found";
    }
}
```
It uses a `@Tool` annotation to define the function description and `@P` annotation to define the parameter description of the package `dev.langchain4j.agent.tool.*`. More info [here](/tutorials/tools#high-level-tool-api)

#### 2. Define an interface as an `agent` to send chat messages.

Create an interface `PaymentTransactionAgent`.

```java
import dev.langchain4j.service.SystemMessage;

interface PaymentTransactionAgent {
    @SystemMessage({
            "You are a payment transaction support agent.",
            "You MUST use the payment transaction tool to search the payment transaction data.",
            "If there a date convert it in a human readable format."
    })
    String chat(String userMessage);
}
```
#### 3. Define a `main` application class to chat with the MistralAI chat model

```java
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModelName;
import dev.langchain4j.service.AiServices;

public class PaymentDataAssistantApp {

    ChatLanguageModel mistralAiModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY")) // Please use your own Mistral AI API key
            .modelName(MistralAiChatModelName.MISTRAL_LARGE_LATEST) // Also you can use MistralAiChatModelName.OPEN_MIXTRAL_8X22B as open source model
            .logRequests(true)
            .logResponses(true)
            .build();
    
    public static void main(String[] args) {
        // STEP 1: User specify tools and query
        PaymentTransactionTool paymentTool = new PaymentTransactionTool();
        String userMessage = "What is the status and the payment date of transaction T1005?";

        // STEP 2: User asks the agent and AiServices call to the functions
        PaymentTransactionAgent agent = AiServices.builder(PaymentTransactionAgent.class)
                .chatLanguageModel(mistralAiModel)
                .tools(paymentTool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        
        // STEP 3: User gets the final response from the agent
        String answer = agent.chat(userMessage);
        System.out.println(answer);
    }
}
```

and expect an answer like this:

```shell
The status of transaction T1005 is Pending. The payment date is October 8, 2021.
```
### JSON mode
You can also use the JSON mode to get the response in JSON format. To do this, you need to set the `responseFormat` parameter to `json_object` or the java enum `MistralAiResponseFormatType.JSON_OBJECT`  in the `MistralAiChatModel` builder OR `MistralAiStreamingChatModel` builder.

Syncronous example:

```java
ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY")) // Please use your own Mistral AI API key
                .responseFormat(MistralAiResponseFormatType.JSON_OBJECT)
                .build();

String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";
String json = model.generate(userMessage);

System.out.println(json); // {"transactionId":"T123","status":"paid"}
```

Streaming example:

```java
StreamingChatLanguageModel streamingModel = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY")) // Please use your own Mistral AI API key
                .responseFormat(MistralAiResponseFormatType.JSON_OBJECT)
                .build();

String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";

CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

streamingModel.generate(userMessage, new StreamingResponseHandler() {
    @Override
    public void onNext(String token) {
        System.out.print(token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        futureResponse.complete(response);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }
});

String json = futureResponse.get().content().text();

System.out.println(json); // {"transactionId":"T123","status":"paid"}
```                

### Guardrailing
Guardrails are a way to limit the behavior of the model to prevent it from generating harmful or unwanted content. You can set optionally `safePrompt` parameter in the `MistralAiChatModel` builder or `MistralAiStreamingChatModel` builder.

Syncronous example:

```java
ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .safePrompt(true)
                .build();

String userMessage = "What is the best French cheese?";
String response = model.generate(userMessage);
```

Streaming example:

```java
StreamingChatLanguageModel streamingModel = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .safePrompt(true)
                .build();

String userMessage = "What is the best French cheese?";

CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

streamingModel.generate(userMessage, new StreamingResponseHandler() {
    @Override
    public void onNext(String token) {
        System.out.print(token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        futureResponse.complete(response);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }
});

futureResponse.join();
```
Toggling the safe prompt will prepend your messages with the following `@SystemMessage`:

```plaintext
Always assist with care, respect, and truth. Respond with utmost utility yet securely. Avoid harmful, unethical, prejudiced, or negative content. Ensure replies promote fairness and positivity.
```

## Code Completion
The Fill-in-the-Middle (FIM) models allow you to generate code completions, user can define the starting point of the code using a `prompt`, and the ending point of the code using an optional `suffix` and an optional `stop`.

### FIM Synchronous
Just like how chat completions work, the FIM endpoint works as well. You can test it by adding the following code.

```java
import dev.langchain4j.model.mistralai.MistralAiCompletionModel;
import dev.langchain4j.model.output.Response;

public class HelloWorld {
    public static void main(String[] args) {
        MistralAiCompletionModel codestral = MistralAiCompletionModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiCodeModelName.CODESTRAL_LATEST)
                .build();
        
        // I want to generate a code completion for a simple hello world program using MistralAI of LangChain4j framework.
        String prompt = "public class HelloWorld {\n" +
                "\tpublic static void main(String[] args) {\n" +
                "\t\tChatLanguageModel model = MistralAiChatModel.withApiKey(ApiKeys.MISTRALAI_API_KEY);";
        String suffix = "\t\tSystem.out.println(response);\n" +
                "\t}\n" +
                "}";

        // Asking to Codestral model to complete the code with given prompt and suffix
        Response<String> response = codestral.generate(prompt, suffix);
        
        System.out.println(
                String.format(
                        "%s%s%s",
                        prompt, // print code prompt (prefix)
                        response.content(), // print code filled-in-the-middle
                        suffix)); // print code suffix
    }
}
```
Running the program will print of the following output

```console
public class HelloWorld {
	public static void main(String[] args) {
		ChatLanguageModel model = MistralAiChatModel.withApiKey(ApiKeys.MISTRALAI_API_KEY);
        
		UserMessage userMessage = new UserMessage("How are you?");

		Response<AiMessage> response = model.generate(userMessage);
		System.out.println(response);
	}
}
```
### FIM Streaming

Create a class and add the following code.

```java
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingCompletionModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

public class HelloWorld {
    public static void main(String[] args) {
        StreamingLanguageModel codestralStream = MistralAiStreamingCompletionModel
                .withApiKey(System.getenv("MISTRAL_AI_API_KEY"));

        // I want to generate a code completion for a simple hello world program.
        String prompt = "public static void main(String[] args) {";

        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();
        codestral.generate(prompt, new StreamingResponseHandler() {
            @Override
            public void onNext(String token) {
                System.out.print(token);
            }

            @Override
            public void onComplete(Response<String> response) {
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        futureResponse.join();
    }
}
```
You will receive each chunk of text (token) as it is generated by the LLM on the onNext method.

You can see that output below is streamed in real-time.

```console
public static void main(String[] args) {

        int[] arr = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int sum = 0;

        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }

        System.out.println("Sum of all elements in the array: " + sum);
    }
}

This is a simple Java program that calculates the sum of all elements in an integer array. Here's a breakdown of how it works:

1. An integer array `arr` is declared and initialized with the values from 1 to 10.
2. A variable `sum` is declared and initialized to 0. This variable will be used to store the sum of all elements in the array.
3. A `for` loop is used to iterate through each element in the array. The loop variable `i` starts at 0 and increments by 1 in each iteration, until it reaches the length of the array.
4. In each iteration of the loop, the value of the current element (`arr[i]`) is added to the `sum` variable.
5. After the loop finishes, the final value of `sum` is printed to the console.
``` 


## Examples
- [Mistral AI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/mistral-ai-examples/src/main/java)
