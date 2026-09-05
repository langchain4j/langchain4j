package dev.langchain4j.code.graalvm;

import static org.graalvm.polyglot.HostAccess.UNTRUSTED;
import static org.graalvm.polyglot.SandboxPolicy.TRUSTED;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;

/**
 * {@link CodeExecutionEngine} that uses GraalVM Polyglot/Truffle to execute provided Python code.
 * Attention! It might be dangerous to execute the code, see {@link SandboxPolicy#TRUSTED}
 * and {@link HostAccess#UNTRUSTED} for more details.
 */
public class GraalVmPythonExecutionEngine implements CodeExecutionEngine {

    @Override
    public String execute(String code) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("python")
                .sandbox(TRUSTED)
                .allowHostAccess(UNTRUSTED)
                .option("engine.WarnInterpreterOnly", "false")
                .out(outputStream)
                .err(outputStream)
                .build()) {
            Value result = context.eval("python", code);
            String output = outputStream.toString(StandardCharsets.UTF_8).stripTrailing();
            if (output.isEmpty()) {
                return String.valueOf(result.as(Object.class));
            }
            Value name = result.hasMember("__name__") ? result.getMember("__name__") : null;
            if (name != null && name.isString() && "__main__".equals(name.asString())) {
                return output;
            }
            return output + "\n" + result.as(Object.class);
        }
    }
}
