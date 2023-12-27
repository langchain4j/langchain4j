package dev.langchain4j.code.graalvm;

import dev.langchain4j.code.CodeExecutionEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.graalvm.polyglot.HostAccess.UNTRUSTED;
import static org.graalvm.polyglot.SandboxPolicy.TRUSTED;

/**
 * {@link CodeExecutionEngine} that uses GraalVM Polyglot/Truffle to execute provided Python code.
 * Attention! It might be dangerous to execute the code, see {@link SandboxPolicy#TRUSTED}
 * and {@link HostAccess#UNTRUSTED} for more details.
 */
public class GraalVmPythonExecutionEngine implements CodeExecutionEngine {

    @Override
    public String execute(String code) {
        OutputStream outputStream = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("python")
                .sandbox(TRUSTED)
                .allowHostAccess(UNTRUSTED)
                .out(outputStream)
                .err(outputStream)
                .build()) {
            Object result = context.eval("python", code).as(Object.class);
            return String.valueOf(result);
        }
    }
}
