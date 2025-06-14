---
sidebar_position: 21
---

# Oracle Cloud Infrastructure GenAI

[Generative AI Service](https://www.oracle.com/artificial-intelligence/generative-ai/generative-ai-service)
provides access to pretrained, foundational models from Cohere and Meta.
See AI model availability [here](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/generative-ai/pretrained-models.htm).

With dedicated AI clusters, you can host foundational models on dedicated GPUs that are private to you. These clusters provide stable, high-throughput performance that’s required for production use cases and can support hosting and fine-tuning workloads.



## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-oci-genai</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

Additionally, you have to select HTTP client for OCI SDK, by default, use a Jersey 3 based version:
```xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-common-httpclient-jersey3</artifactId>
    <version>${oci-sdk.version}</version>
</dependency>
```

In case use are on **Java EE/Jakarta EE 8 or older** runtime, please use Jersey 2 based version:
```xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-common-httpclient-jersey</artifactId>
    <version>${oci-sdk.version}</version>
</dependency>
```

More information can be found in [OCI SDK documentation](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk3.htm#javasdk3__HTTP-client-libraries).


## APIs
Package `dev.langchain4j.community.model.oracle.oci.genai`

API is separated for Cohere and Meta models as the configuration differs.

Meta models:
* `OciGenAiChatModel` - for all OCI GenAi generic chat models(llama)
* `OciGenAiStreamingChatModel` - streaming API for OCI GenAi generic chat models

Cohere models:
* `OciGenAiCohereChatModel` - for all OCI GenAi Cohere chat models
* `OciGenAiCohereStreamingChatModel` - streaming API for OCI GenAi Cohere chat models


## Examples

Example of synchronous Cohere chat model usage with tool call:
```java
var model = OciGenAiCohereChatModel.builder()
      .modelName("cohere.command-r-08-2024")
      .compartmentId("ocid1.tenancy.oc1..aa....")
      .authProvider(new ConfigFileAuthenticationDetailsProvider("DEFAULT"))
      .maxTokens(600)
      .temperature(0.2)
      .topP(0.75)
      .build();

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new Calculator())
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();

String answer = assistant.chat("Calculate square root of 16");
```

Example of streaming Meta chat model usage:
```java
var model = OciGenAiStreamingChatModel.builder()
                .modelName("meta.llama-3.3-70b-instruct")
                .compartmentId("ocid1.tenancy.oc1..aa....")
                .authProvider(new ConfigFileAuthenticationDetailsProvider("DEFAULT"))
                .build();

CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();   

model.doChat(ChatRequest.builder()
        .messages(UserMessage.from("Tell me a joke about Java"))
        .build(), 
new StreamingChatResponseHandler() {
    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.print(partialResponse);
    }
    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        futureResponse.complete(completeResponse);
    }
    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }
});
futureResponse.join();
```
