### GPULlama3.java integration for langchain4j

[GPULlama3.java](https://github.com/beehive-lab/GPULlama3.java) is a Java library that enables efficient GPU-accelerated inference with LLM models in Java applications.

It is a wrapper around the [llama3.java](https://github.com/mukel/llama3.java) library.
GPULlama3.java builds on [TornadoVM](https://github.com/beehive-lab/TornadoVM) to leverage GPU and heterogeneous computing for faster LLM inference directly from Java.
Currently, GPULlama3.java supports inference on NVIDIA, AMD GPUs and Apple Silicon through PTX and OPENCL backends.

It supports models in the GGUF format. Currently, it supports inference with the following models: Llama3, Mistral, PHi-3, Qwen3.
