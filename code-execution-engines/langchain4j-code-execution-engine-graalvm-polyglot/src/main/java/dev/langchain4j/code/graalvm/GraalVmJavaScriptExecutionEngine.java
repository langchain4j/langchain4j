package dev.langchain4j.code.graalvm;

import static org.graalvm.polyglot.HostAccess.UNTRUSTED;
import static org.graalvm.polyglot.SandboxPolicy.CONSTRAINED;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.ByteArrayOutputStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;

/**
 * {@link CodeExecutionEngine} that uses GraalVM Polyglot/Truffle to execute provided JavaScript code.
 * Attention! It might be dangerous to execute the code, see {@link SandboxPolicy#CONSTRAINED}
 * and {@link HostAccess#UNTRUSTED} for more details.
 */
public class GraalVmJavaScriptExecutionEngine implements CodeExecutionEngine {

    @Override
    public String execute(String code) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("js")
                .sandbox(CONSTRAINED)
                .allowHostAccess(UNTRUSTED)
                .option("engine.WarnInterpreterOnly", "false")
                .out(outputStream)
                .err(outputStream)
                .build()) {
            Value result = context.eval("js", code);
            return GraalVmExecutionResult.fromJavaScript(result, outputStream);
        }
    }
}
