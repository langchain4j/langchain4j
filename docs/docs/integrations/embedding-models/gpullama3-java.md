---
sidebar_position: 22
---
# GPULlama3.java
[GPULlama3.java](https://github.com/beehive-lab/GPULlama3.java)

GPULlama3.java builds on [TornadoVM](https://github.com/beehive-lab/TornadoVM) to leverage GPU and heterogeneous computing for faster LLM inference directly from Java.
Currently, GPULlama3.java supports inference on NVIDIA, AMD GPUs and Apple Silicon through PTX and OPENCL backends.


### Project setup

To install langchain4j to your project, add the following dependency:

For Maven project `pom.xml`

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.5.0</version>
</dependency>

<dependency>
<groupId>dev.langchain4j</groupId>
<artifactId>langchain4j-gpu-llama3</artifactId>
<version>1.5.0-beta11</version>
</dependency>

```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:1.4.0'
implementation 'dev.langchain4j:langchain4j-gpu-llama3:1.4.0'
```