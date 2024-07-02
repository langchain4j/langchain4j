### Jlama integration for langchain4j

[Jlama](https://github.com/tjake/Jlama) is a Java library that provides a simple way to integrate LLM models into Java
applications.

Jlama is built with Java 21 and utilizes the new [Vector API](https://openjdk.org/jeps/448) for faster inference.

Jlama uses huggingface models in safetensor format.
Models must be specified using the `owner/model-name` format. For example, `meta-llama/Llama-2-7b-chat-hf`.

Pre-quantized models are maintained under https://huggingface.co/tjake

