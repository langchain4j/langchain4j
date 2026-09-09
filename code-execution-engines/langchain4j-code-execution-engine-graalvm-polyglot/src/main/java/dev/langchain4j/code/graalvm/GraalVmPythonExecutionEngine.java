package dev.langchain4j.code.graalvm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.polyglot.HostAccess.UNTRUSTED;
import static org.graalvm.polyglot.SandboxPolicy.TRUSTED;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;

/**
 * {@link CodeExecutionEngine} that uses GraalVM Polyglot/Truffle to execute provided Python code.
 * <p>
 * The returned string contains both what the code printed to stdout/stderr and what it evaluated to:
 * <ul>
 *     <li>{@code 40 + 2} evaluates to a value without printing anything, so {@code "42"} is returned</li>
 *     <li>{@code print('hello')} only prints, so {@code "Output:\nhello"} is returned</li>
 *     <li>{@code print('hello')\n42} does both, so {@code "Output:\nhello\nResult:\n42"} is returned</li>
 *     <li>{@code x = 1} neither prints nor evaluates to a value, so an empty string is returned</li>
 * </ul>
 * When the code fails, a {@link org.graalvm.polyglot.PolyglotException} is thrown
 * and anything printed before the failure is lost.
 * <p>
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
                .out(outputStream)
                .err(outputStream)
                // Truffle logs (for example the warning about running without runtime compilation)
                // are written to the streams above, so they have to be routed away
                // to keep them out of the returned result
                .logHandler(OutputStream.nullOutputStream())
                .build()) {
            Value result = context.eval("python", code);
            String output = outputStream.toString(UTF_8).stripTrailing();
            String value = evaluatedToNothing(result) ? null : String.valueOf(result.as(Object.class));
            return ExecutionResultFormatter.format(output, value);
        }
    }

    private static boolean evaluatedToNothing(Value result) {
        // Python code that does not end with an expression (an assignment, an import, a print, etc.)
        // evaluates to the "__main__" module itself instead of to a value.
        // Any module is treated as nothing: a module is not a result worth returning,
        // and converting one to a Java object fails inside GraalVM.
        Value metaObject = result.getMetaObject();
        return metaObject != null && "module".equals(metaObject.getMetaSimpleName());
    }
}
