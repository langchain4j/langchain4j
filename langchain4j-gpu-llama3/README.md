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
    <version>1.6.0</version>
</dependency>

<dependency>
<groupId>dev.langchain4j</groupId>
<artifactId>langchain4j-gpu-llama3</artifactId>
<version>1.6.0-beta12</version>
</dependency>

```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:1.6.0'
implementation 'dev.langchain4j:langchain4j-gpu-llama3:1.6.0-beta12'
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

Path modelPath = Paths.get("beehive-llama-3.2-1b-instruct-fp16.gguf");

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

    Path modelPath = Paths.get("beehive-llama-3.2-1b-instruct-fp16.gguf");


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
The tests require proper GPULlama3.java and TornadoVM configuration.PrerequisitesBefore running tests, ensure you have:

* GPULlama3.java properly configured and installed
* TornadoVM installed and configured with GPU support
* JDK 21+ installed
* TORNADO_SDK environment variable set to your TornadoVM installation path
* A compatible GGUF model file (e.g., Phi-3-mini-4k-instruct-fp16.gguf) in the project root

### Running Tests
To run the integration tests with TornadoVM GPU acceleration:

```bash
mvn clean compile test-compile
mvn -P run-tests
```

#### Expected Output
```bash
[INFO] --- exec:3.1.0:exec (default-cli) @ langchain4j-gpu-llama3 ---
WARNING: Using incubator modules: jdk.incubator.vector

Thanks for using JUnit! Support its development at https://junit.org/sponsoring

Here's one:

What do you call a fake noodle?

An impasta!
╷
├─ JUnit Jupiter ✔
│  ├─ GPULlama3ChatModelIT ✔
│  │  └─ should_get_non_empty_response() ✔
│  └─ GPULlama3CStreamingChatModelIT ✔
│     └─ should_stream_answer_and_return_response() 22313 ms ✔
├─ JUnit Vintage ✔
└─ JUnit Platform Suite ✔

Test run finished after 31605 ms
[         5 containers found      ]
[         0 containers skipped    ]
[         5 containers started    ]
[         0 containers aborted    ]
[         5 containers successful ]
[         0 containers failed     ]
[         2 tests found           ]
[         0 tests skipped         ]
[         2 tests started         ]
[         0 tests aborted         ]
[         2 tests successful      ]
[         0 tests failed          ]

```

## How to run:

One need to configure TornadoVM to run the example
Detailed instructions can be found **[Setup & Configure](https://github.com/beehive-lab/GPULlama3.java?tab=readme-ov-file#prerequisites)**
#### **Step 1 — Get Tornado JVM flags**

Run the following command (You need to have Tornado installed):

```bash
tornado --printJavaFlags
```

Example output:

```bash
/home/mikepapadim/.sdkman/candidates/java/current/bin/java -server \
 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI \
-XX:-UseCompressedClassPointers --enable-preview \
-Djava.library.path=/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/lib \
--module-path .:/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/share/java/tornado \
-Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph \
-Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime \
-Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado \
-Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor \
-Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel \
--upgrade-module-path /home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/share/java/graalJars \
-XX:+UseParallelGC \
@/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/etc/exportLists/common-exports \
@/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/etc/exportLists/opencl-exports \
--add-modules ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common,tornado.drivers.opencl
```

#### **Step 2 — Build the Maven classpath**

From the project root, run:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
```

#### **Step 3 — Build the Maven classpath**

```bash
mvn clean package
```

Your main JAR will be located at:
```bash
target/gpullama3.java-example-1.4.0-beta10.jar
```

#### **Step 4 — Run the program directly with Java**
You can now run the example with all JVM and Tornado flags:

```bash
JAVA_BIN=/home/mikepapadim/.sdkman/candidates/java/current/bin/java
CP="target/gpullama3.java-example-1.4.0-beta10.jar:$(cat cp.txt)"

$JAVA_BIN \
  -server \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+EnableJVMCI \
  --enable-preview \
  -Djava.library.path=/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/lib \
  --module-path .:/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/share/java/tornado \
  -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph \
  -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime \
  -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado \
  -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor \
  -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel \
  --upgrade-module-path /home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/share/java/graalJars \
  -XX:+UseParallelGC \
  @/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/etc/exportLists/common-exports \
  @/home/mikepapadim/java-ai-demos/GPULlama3.java/external/tornadovm/bin/sdk/etc/exportLists/opencl-exports \
  --add-modules ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common,tornado.drivers.opencl \
  -Xms6g -Xmx6g \
  -Dtornado.device.memory=6GB \
  -cp "$CP" \
  GPULlama3ChatModelExamples
```

## Expected output:

```bash
WARNING: Using incubator modules: jdk.incubator.vector
Example Prompt: What is the capital of France?
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
Wow, I'm so glad you asked. I've been waiting for someone to finally ask me this question. It's not like I have better things to do, like take a nap or something. So, yes, the capital of France is... (dramatic pause) ...Paris!

achieved tok/s: 48.86. Tokens: 87, seconds: 1.78
```

## Notes:

* GPU utulization can be monitored with `nvidia-smi` for NVIDIA GPUs or 'nvtop' appropriate tools for AMD/Apple Silicon.