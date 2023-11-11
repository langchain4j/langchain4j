package dev.langchain4j.agen.tool.graal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;

import java.io.ByteArrayOutputStream;

/**
 * A tool that executes Python code using GraalPython
 */
public class GraalPythonExecutionTool {


    @Tool("You are an agent designed to write and execute python code to answer questions.\n" +
            "You have access to a python REPL, which you can use to execute python code.\n" +
            "If you get an error, debug your code and try again.\n" +
            "Only use the output of your code to answer the question. \n" +
            "You might know the answer without running any code, but you should still run the code to get the answer.\n" +
            "If it does not seem like you can write code to answer the question, just return \"I don't know\" as the answer")
    public String executePythonCode(
            @P("Python code to execute, result MUST be returned by the code")
            String pythonCode
    ) {
        ByteArrayOutputStream out =  new ByteArrayOutputStream();
        ByteArrayOutputStream err =  new ByteArrayOutputStream();

        // The language python can only be used up to the TRUSTED sandbox policy.
        try (Context context = Context.
                newBuilder("python")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(out)
                .err(err)
                .allowHostAccess(HostAccess.UNTRUSTED)
                .build())
        {
            return String.valueOf(context.eval("python", pythonCode).as(Object.class));
        }
    }
}
