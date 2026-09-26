---
sidebar_position: 23
---

# jitLLM

[jitLLM](https://github.com/beehive-lab/jitllm) runs LLM inference on the JVM and uses
[TornadoVM](https://github.com/beehive-lab/TornadoVM) to JIT-compile its kernels for GPUs through
CUDA, OpenCL or Metal. It is the successor of [GPULlama3.java](/integrations/language-models/gpullama3-java) (`langchain4j-gpu-llama3`).

## Project setup

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jitllm</artifactId>
    <version>1.21.0-beta31</version>
</dependency>
```

```groovy
implementation 'dev.langchain4j:langchain4j-jitllm:1.21.0-beta31'
```

jitLLM publishes one artifact per JDK line, matching TornadoVM's: `jitllm:<version>-jdk21` and
`jitllm:<version>-jdk22plus` (JDK 22 and newer). This module's `jdk21` and `jdk22plus` profiles
select the one that matches the JDK building it.

## Requirements

* JDK 21, or JDK 22 or newer
* A TornadoVM SDK for the same JDK line (`jdk21` or `jdk22plus`) and for your GPU's backend,
  from [tornadovm.org](https://www.tornadovm.org/downloads) or SDKMAN! (`sdk install tornadovm`)
* A model in GGUF format (FP16, Q8_0 or Q4_0): Llama 3, Mistral, Qwen 2.5, Qwen 3, Phi-3,
  IBM Granite 3.3 / 4.0, Gemma 4 and DeepSeek-R1-Distill are supported. Tested models are collected
  on [Hugging Face](https://huggingface.co/beehive-lab/collections).

On the GPU, start the JVM through TornadoVM's `tornado` launcher and pass `-Duse.tornadovm=true`
and `--add-modules jdk.incubator.vector`. The backend is whichever one the SDK provides.

jitLLM's jar leaves TornadoVM to the SDK. Its CPU path still uses TornadoVM's array types, so to
run on the CPU without the SDK (`onGPU(false)` in a plain JVM), add `tornado-api` from the same
TornadoVM release with `runtime` scope, for example
`io.github.beehive-lab:tornado-api:7.0.1-jdk22plus` (`7.0.1-jdk21` on JDK 21). Leave it out when
running through TornadoVM: the SDK already provides it.

## Chat

```java
JitLLMChatModel model = JitLLMChatModel.builder()
        .modelPath(Path.of(System.getenv("MODEL")))
        .temperature(0.7)
        .maxTokens(2048)
        .onGPU(true)            // false runs on the CPU
        .build();

ChatResponse response = model.chat(ChatRequest.builder()
        .messages(SystemMessage.from("You are a helpful assistant."), UserMessage.from("Who are you?"))
        .build());
System.out.println(response.aiMessage().text());
model.printLastMetrics();       // logs prompt/generation token rates at INFO
```

## Streaming

```java
JitLLMStreamingChatModel model = JitLLMStreamingChatModel.builder()
        .modelPath(Path.of(System.getenv("MODEL")))
        .onGPU(true)
        .build();

model.chat(request, new StreamingChatResponseHandler() {
    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println();
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

Run it with the TornadoVM launcher:

```bash
tornado --jvm="-Duse.tornadovm=true --add-modules jdk.incubator.vector -Dtornado.device.memory=20GB" \
  -cp "target/my-app.jar:$(cat cp.txt)" com.example.Main
```

## Capabilities

* Thinking content is separated from the answer (`AiMessage.thinking()`).
* Tool calling, synchronous and streaming, including several calls in one turn. Forced or named
  tool choice, and tools combined with a JSON response format, are not supported.
* A response cut off by `maxTokens` is returned in full with `FinishReason.LENGTH`.
* Not supported: JSON response formats, stop sequences, images, per-request model names.
