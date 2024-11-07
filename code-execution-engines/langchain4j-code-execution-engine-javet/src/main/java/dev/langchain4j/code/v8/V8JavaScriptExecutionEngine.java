package dev.langchain4j.code.v8;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import dev.langchain4j.code.CodeExecutionEngine;

public class V8JavaScriptExecutionEngine implements CodeExecutionEngine {

    private final V8Runtime v8Runtime;

    private static class SingletonHelper {
        private static final V8JavaScriptExecutionEngine INSTANCE = new V8JavaScriptExecutionEngine();
    }

    private V8JavaScriptExecutionEngine() {
        try {
            this.v8Runtime = V8Host.getV8Instance().createV8Runtime();
        }catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }

    public static V8JavaScriptExecutionEngine getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public String execute(String code) {
        try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
            return v8Value.asString();
        } catch (JavetException e) {
            throw new RuntimeException("Execution failed", e);
        }
    }
}
