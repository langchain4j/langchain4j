---
sidebar_position: 23
---

# jitLLM

[jitLLM](https://github.com/beehive-lab/jitllm) runs LLMs in GGUF format inside the JVM.
It runs them on a GPU through [TornadoVM](https://github.com/beehive-lab/TornadoVM),
which JIT-compiles the model's kernels for CUDA, OpenCL or Metal, or on the CPU.
No separate inference server is needed.

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

## Requirements

* JDK 21 or newer. On JDK 21, see [Running on JDK 21](#running-on-jdk-21).
* A model in GGUF format (FP16, Q8_0 or Q4_0). Supported families include Llama 3, Mistral, Qwen 2.5, Qwen 3,
  Phi-3, IBM Granite 3.3 / 4.0, Gemma 4 and DeepSeek-R1-Distill.
  Tested models are collected on [Hugging Face](https://huggingface.co/beehive-lab/collections).
* The JVM option `--add-modules jdk.incubator.vector`.
* To run on a GPU: a [TornadoVM SDK](https://www.tornadovm.org/downloads) for your JDK line (`jdk21` or `jdk22plus`)
  and for your GPU's backend, for example installed with SDKMAN! (`sdk install tornadovm`).
  The JVM must be started through TornadoVM's `tornado` launcher with `-Duse.tornadovm=true`.
* To run on the CPU in a plain JVM (without the TornadoVM launcher): `io.github.beehive-lab:tornado-api:7.0.1-jdk22plus`
  (`7.0.1-jdk21` on JDK 21) as a `runtime` dependency. Leave it out when running through TornadoVM, which already provides it.

Only the JVM mode is supported; GraalVM native images are not.

### Running on JDK 21

jitLLM is published in two builds: `jdk22plus`, which `langchain4j-jitllm` depends on, and `jdk21`.
On JDK 21, replace the first with the second and start the JVM with `--enable-preview`,
because the `jdk21` build uses the Foreign Function & Memory API, which is a preview feature in JDK 21:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jitllm</artifactId>
    <version>1.21.0-beta31</version>
    <exclusions>
        <exclusion>
            <groupId>io.github.beehive-lab</groupId>
            <artifactId>jitllm</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>io.github.beehive-lab</groupId>
    <artifactId>jitllm</artifactId>
    <version>1.0.2-jdk21</version>
</dependency>
```

## Chat

```java
try (JitLLMChatModel model = JitLLMChatModel.builder()
        .modelPath(Path.of("Qwen3-0.6B-Q8_0.gguf"))
        .build()) {

    String answer = model.chat("What is the capital of Germany?");
}
```

The model is loaded when it is built and keeps its memory, including GPU memory, until `close()` is called.
Build it once and reuse it.

## Streaming

```java
try (JitLLMStreamingChatModel model = JitLLMStreamingChatModel.builder()
        .modelPath(Path.of("Qwen3-0.6B-Q8_0.gguf"))
        .build()) {

    model.chat("Tell me a story", new StreamingChatResponseHandler() {

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
}
```

`chat(...)` returns once the response is complete: the model runs in the calling thread, and the handler is called
on that thread. To avoid blocking, call it from another thread, for example a virtual thread.
Streaming can be stopped through the `StreamingHandle` available in
`onPartialResponse(PartialResponse, PartialResponseContext)`.

## Running on a GPU

```bash
tornado --jvm="-Duse.tornadovm=true --add-modules jdk.incubator.vector -Dtornado.device.memory=8GB" \
  -cp "target/my-app.jar:target/dependency/*" com.example.Main
```

`target/dependency` can be filled with `mvn dependency:copy-dependencies`.
When the JVM is started this way, the model runs on the GPU, using the backend that the TornadoVM SDK provides.
`onGPU(false)` forces the CPU, and `onGPU(true)` fails at build time if the JVM was not started through TornadoVM.

## Configuration

| Builder method             | Default                                              | Description                                                                                                         |
|----------------------------|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `modelPath`                | required                                             | Path to the GGUF file.                                                                                              |
| `contextLength`            | 4096                                                 | Maximum number of tokens of the whole conversation, including the response. Memory for it is reserved at load time. |
| `onGPU`                    | `true` when started with `-Duse.tornadovm=true`      | Run on a GPU or on the CPU.                                                                                         |
| `temperature`              | 0.1                                                  | Sampling temperature.                                                                                               |
| `topP`                     | 0.95                                                 | Nucleus sampling probability.                                                                                       |
| `maxTokens`                | 512                                                  | Maximum number of tokens generated per response, including the thinking.                                           |
| `stopSequences`            |                                                      | Sequences that end the response. They apply to the answer, not to the thinking.                                     |
| `seed`                     | random per request                                   | Seed of the sampling, for reproducible responses.                                                                   |
| `think`                    | model default                                        | Whether reasoning models think before answering. `false` makes them answer directly.                                |
| `returnThinking`           | `false`                                              | Return the thinking of reasoning models in `AiMessage.thinking()` and stream it to `onPartialThinking`.             |
| `defaultRequestParameters` |                                                      | Default `ChatRequestParameters`; the values above take precedence.                                                  |
| `listeners`                |                                                      | [Listeners](/tutorials/observability) notified about requests, responses and errors.                                |

`temperature`, `topP`, `maxOutputTokens` and `stopSequences` can also be set per request in `ChatRequestParameters`.

One model instance can be used by several threads, but it generates one response at a time:
concurrent requests wait for each other.

## Capabilities

* Streaming, with cancellation.
* Tools, synchronous and streaming, including several tool calls in one response.
  With tools, the streamed response is delivered at once when it is complete.
* Thinking of reasoning models (text between `<think>` and `</think>`): it can be switched on or off with `think`,
  and is returned when `returnThinking(true)` is set.
* Token usage and finish reason in `ChatResponse`.
* Not supported: JSON response formats, `ToolChoice.REQUIRED`, images, and the `modelName`, `topK`,
  `frequencyPenalty` and `presencePenalty` parameters.

## Migrating from GPULlama3.java

jitLLM is the successor of [GPULlama3.java](/integrations/language-models/gpullama3-java), and `langchain4j-jitllm`
replaces the deprecated `langchain4j-gpu-llama3` module:

* Replace `GPULlama3ChatModel` with `JitLLMChatModel` and `GPULlama3StreamingChatModel` with `JitLLMStreamingChatModel`.
* `maxTokens` now only limits the generated tokens. The context window is set with `contextLength`.
* Free the memory with `close()` instead of `freeTornadoVMGPUResources()`. There is no automatic cleanup when the
  model is garbage-collected.
* The thinking is only returned with `returnThinking(true)`, and without the `<think>` tags.
* `printLastMetrics()` is replaced by `ChatResponse.tokenUsage()`.
* When `onGPU` is not set, the model runs on the GPU only if the JVM was started with `-Duse.tornadovm=true`.
