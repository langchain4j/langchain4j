# GPULlama3.java
[GPULlama3.java](https://github.com/beehive-lab/GPULlama3.java)

GPULlama3.java builds on [TornadoVM](https://github.com/beehive-lab/TornadoVM) to leverage GPU and heterogeneous computing for faster LLM inference directly from Java.
Currently, GPULlama3.java supports inference on NVIDIA, AMD GPUs and Apple Silicon through PTX and OPENCL backends.

----
## Project setup

To install langchain4j to your project, add the following dependency:

For Maven project `pom.xml`

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.19.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-gpu-llama3</artifactId>
    <version>1.19.0-beta29-SNAPSHOT</version>
</dependency>

```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:1.19.0-SNAPSHOT'
implementation 'dev.langchain4j:langchain4j-gpu-llama3:1.19.0-beta29-SNAPSHOT'
```
---
## Model Compatibility

Currently, GPULlama3.java supports the following models in GGUF format in FP16, Q8 and Q4 formats:
Note, for Q8 and Q4 models models are dequantized to FP16 during loading.
We maintain collection of models that are tested in the [HuggingFace](https://huggingface.co/beehive-lab/collections) repository.

* Llama3
* Mistral
* Qwen2.5
* Qwen3.0
* Phi-3
* DeepSeek-R1-Distill-Qwen-1.5B-GGUF
* IBM Granite 3.2, 3.3 and 4.0
----
## Chat Completion
The chat models allow you to generate human-like responses with a model fined-tuned on conversational data.

### Synchronous
Create a class and add the following code.

```java
prompt = "What is the capital of France?";
ChatRequest request = ChatRequest.builder().messages(
    UserMessage.from(prompt),
    SystemMessage.from("reply with extensive sarcasm"))
    .build();

Path modelPath = Path.of(System.getenv("MODEL"));

GPULlama3ChatModel model = GPULlama3ChatModel.builder()
        .modelPath(modelPath)
        .onGPU(Boolean.TRUE) //if false, runs on CPU though a lightweight implementation of llama3.java
        .build();
ChatResponse response = model.chat(request);
System.out.println("\n" + response.aiMessage().text());
```

### Streaming

Create a class and add the following code.

```java
public static void main(String[] args) {
    CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

    String prompt;

    if (args.length > 0) {
        prompt = args[0];
        System.out.println("User Prompt: " + prompt);
    } else {
        prompt = "What is the capital of France?";
        System.out.println("Example Prompt: " + prompt);
    }

    // @formatter:off
    ChatRequest request = ChatRequest.builder().messages(
                    UserMessage.from(prompt),
                    SystemMessage.from("reply with extensive sarcasm"))
            .build();

    Path modelPath = Path.of(System.getenv("MODEL"));


    GPULlama3StreamingChatModel model = GPULlama3StreamingChatModel.builder()
            .onGPU(Boolean.TRUE) // if false, runs on CPU though a lightweight implementation of llama3.java
            .modelPath(modelPath)
            .build();
    // @formatter:on

    model.chat(request, new StreamingChatResponseHandler() {

        @Override
        public void onPartialResponse(String partialResponse) {
            System.out.print(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            futureResponse.complete(completeResponse);
            model.printLastMetrics();
        }

        @Override
        public void onError(Throwable error) {
            futureResponse.completeExceptionally(error);
        }
    });

    futureResponse.join();
}
```

## How to run Tests:

This project includes integration tests that verify GPULlama3.java functionality with TornadoVM.
The tests require proper GPULlama3.java and TornadoVM configuration.

Before running tests, ensure you have:

* GPULlama3.java properly configured and installed
* TornadoVM 5.2.0 for JDK 21 or 25, selected with SDKMAN. For example, for CUDA:
  `sdk use tornadovm 5.2.0-jdk21-cuda` or
  `sdk use tornadovm 5.2.0-jdk25-cuda`. Select the corresponding SDKMAN
  distribution for another backend.
* JDK 21 or 25 installed, matching the selected TornadoVM distribution
* TORNADOVM_HOME environment variable set to your TornadoVM installation path
* `MODEL` set to the path of a compatible GGUF model file

The active JDK selects GPULlama3 `1.0.0-jdk21` or `1.0.0-jdk25`. Both use
release-specific Java preview features. The Maven build enables preview features
for compilation and Javadoc, and the TornadoVM launcher enables them at runtime.
The selected TornadoVM distribution must match both the JDK and installed
backend runtime; for example, the CUDA distribution requires its matching CUDA
native libraries.

### Running Tests
To run the integration tests with TornadoVM GPU acceleration:

```bash
sdk use java 21.0.2-open
sdk use tornadovm 5.2.0-jdk21-cuda
export MODEL=/path/to/model.gguf
../mvnw -P run-tests
```

For JDK 25, select `java 25.0.2-open` and
`tornadovm 5.2.0-jdk25-cuda` instead.

## Notes:

* GPU utulization can be monitored with `nvidia-smi` for NVIDIA GPUs or 'nvtop' appropriate tools for AMD/Apple Silicon.
