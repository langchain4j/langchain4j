package dev.langchain4j.code.graalvm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.polyglot.HostAccess.UNTRUSTED;
import static org.graalvm.polyglot.SandboxPolicy.CONSTRAINED;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;

/**
 * {@link CodeExecutionEngine} that uses GraalVM Polyglot/Truffle to execute provided JavaScript code.
 * <p>
 * The returned string contains both what the code printed to stdout/stderr and what it evaluated to:
 * <ul>
 *     <li>{@code 40 + 2} evaluates to a value without printing anything, so {@code "42"} is returned</li>
 *     <li>{@code console.log('hello')} only prints, so {@code "Output:\nhello"} is returned</li>
 *     <li>{@code console.log('hello'); 42} does both, so {@code "Output:\nhello\nResult:\n42"} is returned</li>
 *     <li>{@code var x = 1;} neither prints nor evaluates to a value, so an empty string is returned</li>
 * </ul>
 * When the code fails, a {@link org.graalvm.polyglot.PolyglotException} is thrown
 * and anything printed before the failure is lost.
 * <p>
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
                .out(outputStream)
                .err(outputStream)
                // Truffle logs (for example the warning about running without runtime compilation)
                // are written to the streams above, so they have to be routed away
                // to keep them out of the returned result
                .logHandler(OutputStream.nullOutputStream())
                .build()) {
            Value result = context.eval("js", code);
            String output = outputStream.toString(UTF_8).stripTrailing();
            // JavaScript code that does not end with an expression evaluates to "undefined",
            // which, just like "null", carries nothing worth returning
            String value = result.isNull() ? null : String.valueOf(result.as(Object.class));
            return ExecutionResultFormatter.format(output, value);
        }
    }
}
