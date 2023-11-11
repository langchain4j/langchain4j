package dev.langchain4j.agen.tool.graal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;

import java.io.ByteArrayOutputStream;

/**
 * A tool that executes JS code using Graal JS
 */
public class GraalJavascriptExecutionTool {


    @Tool("You are an agent designed to write and execute javascript code to answer questions.\n" +
            "You have access to a javascript REPL, which you can use to execute js code.\n" +
            "If you get an error, debug your code and try again.\n" +
            "Only use the output of your code to answer the question. \n" +
            "You might know the answer without running any code, but you should still run the code to get the answer.\n" +
            "If it does not seem like you can write code to answer the question, just return \"I don't know\" as the answer")
    public String executeJavaScriptCode(
            @P("JavaScript code to execute, result MUST be returned by the code")
            String javaScriptCode
    ) {
        ByteArrayOutputStream out =  new ByteArrayOutputStream();
        ByteArrayOutputStream err =  new ByteArrayOutputStream();

        // GraalVM community edition supports only sandbox policy TRUSTED or CONSTRAINED.
        try (Context context = Context.newBuilder("js")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .allowHostAccess(HostAccess.UNTRUSTED)
                .out(new ByteArrayOutputStream())
                .err(new ByteArrayOutputStream())
                .build()) {
            return String.valueOf(context.eval("js", javaScriptCode).as(Object.class));
        }
    }
}
