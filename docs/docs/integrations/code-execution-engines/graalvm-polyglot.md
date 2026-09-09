---
sidebar_position: 2
---

# GraalVM Polyglot/Truffle

:::danger
⚠️ Security Warning: High-Risk Code Execution

This module enables execution of arbitrary Python/JavaScript code via GraalVM and is inherently dangerous.

❗ Do NOT use in production environments!
:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-code-execution-engine-graalvm-polyglot</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## APIs

- `GraalVmJavaScriptExecutionEngine`
- `GraalVmJavaScriptExecutionTool`
- `GraalVmPythonExecutionEngine`
- `GraalVmPythonExecutionTool`


## Return Value

`execute(String code)` returns a single string that contains both what the code printed
to stdout and stderr and the value the code evaluated to.

Code that only evaluates to a value returns that value:

```java
engine.execute("40 + 2");
// 42
```

Code that only prints returns what was printed, prefixed with `Output:`:

```java
engine.execute("print('hello')");
// Output:
// hello
```

Code that does both returns the printed output followed by the value:

```java
engine.execute("print('hello')\n42");
// Output:
// hello
// Result:
// 42
```

Code that neither prints nor evaluates to a value returns an empty string:

```java
engine.execute("x = 1");
// (empty string)
```

If the code fails, a `PolyglotException` is thrown
and anything the code printed before failing is lost.


## Examples

- [GraalVmJavaScriptExecutionEngineTest](https://github.com/langchain4j/langchain4j/blob/main/code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot/src/test/java/dev/langchain4j/code/graalvm/GraalVmJavaScriptExecutionEngineTest.java)
- [GraalVmJavaScriptExecutionToolIT](https://github.com/langchain4j/langchain4j/blob/main/code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot/src/test/java/dev/langchain4j/agent/tool/graalvm/GraalVmJavaScriptExecutionToolIT.java)
- [GraalVmPythonExecutionEngineTest](https://github.com/langchain4j/langchain4j/blob/main/code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot/src/test/java/dev/langchain4j/code/graalvm/GraalVmPythonExecutionEngineTest.java)
- [GraalVmPythonExecutionToolIT](https://github.com/langchain4j/langchain4j/blob/main/code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot/src/test/java/dev/langchain4j/agent/tool/graalvm/GraalVmPythonExecutionToolIT.java)
