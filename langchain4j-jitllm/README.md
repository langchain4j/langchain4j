# jitLLM integration for LangChain4j

[jitLLM](https://github.com/beehive-lab/jitllm) runs LLMs in GGUF format inside the JVM, on a GPU through
[TornadoVM](https://github.com/beehive-lab/TornadoVM) or on the CPU.

See the [documentation](https://docs.langchain4j.dev/integrations/language-models/jitllm) for setup and usage.

## Running the tests

The unit tests run in every build. The integration tests (`*IT`) need a model in GGUF format, pointed to by the
`MODEL` environment variable, and are skipped without it.

On a GPU, the `run-tests` profile runs them in a TornadoVM JVM:

```bash
export TORNADOVM_HOME=/path/to/tornadovm-7.0.1-jdk22plus-cuda
export MODEL=/path/to/Qwen3-0.6B-Q8_0.gguf
../mvnw -P run-tests
```

`-Dit.selection=...` selects other test classes, for example
`"-Dit.selection=--select-class=dev.langchain4j.model.jitllm.JitLLMChatModelIT"`.

On the CPU, they run in a plain JVM with `io.github.beehive-lab:tornado-api:7.0.1-jdk22plus` added to the test
classpath and `--add-modules jdk.incubator.vector`.

On JDK 21, the build uses the `jdk21` build of jitLLM (`1.0.2-jdk21`); use a `jdk21` TornadoVM SDK or
`tornado-api:7.0.1-jdk21`, and add `--enable-preview`.
