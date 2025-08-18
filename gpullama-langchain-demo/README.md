# GPU Llama 3 Demo (LangChain4j + TornadoVM)

A tiny Java demo showing how to run **Llama 3** locally via **LangChain4j** with GPU acceleration using **TornadoVM**. Dependencies are resolved with **Maven**.

## Requirements

* **JDK 21 LTS** with preview features enabled (Vector API in `jdk.incubator.vector`)
* **TornadoVM** installed and `tornado` available on your `PATH`
* A compatible **GPU** with drivers/toolchain supported by TornadoVM (OpenCL / PTX, in the future: Metal)
* **Maven**

## Build

```bash
mvn -q -DskipTests package
```

This creates `./target/gpullama-langchain-demo-1.0-SNAPSHOT.jar`.

## Run (with TornadoVM)

Use the `tornado` launcher to enable preview features and the Vector API, and to include runtime deps from Maven:

```bash
tornado --enable-preview --add-modules jdk.incubator.vector \
  -cp target/lc4j-ollama-tornado-1.0-SNAPSHOT.jar:\
$(mvn -q -pl '' -am dependency:build-classpath -DincludeScope=runtime \
  -Dmdep.outputAbsoluteArtifactFilename=true | tail -n1) \
  com.example.App
```

## What this demo does

* Uses **LangChain4j** to call **GPULlama3.java** locally.
* Offloads vectorizable hotspots to the GPU via **TornadoVM**, leveraging the JDK Vector API where applicable.
