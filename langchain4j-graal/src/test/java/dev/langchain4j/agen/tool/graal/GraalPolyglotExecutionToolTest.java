package dev.langchain4j.agen.tool.graal;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class GraalPolyglotExecutionToolTest {


    /**
     *
     * @throws NoSuchMethodException
     */
    @Test
    public void testJavascriptTool() throws NoSuchMethodException
    {
        GraalJavascriptExecutionTool jsTool =  new GraalJavascriptExecutionTool();
        String pythonCode = "function fibonacci(num) {                  \n" +
                "  if (num <= 1) return 1;                          \n" +
                "                                                   \n" +
                "  return fibonacci(num - 1) + fibonacci(num - 2);  \n" +
                "}                                                  \n" +
                "fibonacci(10)";

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments( new Gson().toJson(Map.of("arg0", pythonCode)))
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(jsTool, GraalJavascriptExecutionTool.class.getDeclaredMethod("executeJavaScriptCode", String.class));

        String result = toolExecutor.execute(request);
        Integer fib_of_10 = new Gson().fromJson(  result, Integer.class);
        assertThat(fib_of_10).isEqualTo(89);

    }

    @Test
    public void testPythonTool() throws NoSuchMethodException
    {
        GraalPythonExecutionTool jsTool =  new GraalPythonExecutionTool();
        String pythonCode = "def fibonacci_of(n):\n" +
                "     if n <= 1:  # Base case\n" +
                "         return 1\n" +
                "     return fibonacci_of(n - 1) + fibonacci_of(n - 2)  # Recursive case\n" +
                "fibonacci_of(10)";

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments( new Gson().toJson(Map.of("arg0", pythonCode)))
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(jsTool, GraalPythonExecutionTool.class.getDeclaredMethod("executePythonCode", String.class));

        String result = toolExecutor.execute(request);
        Integer fib_of_10 = new Gson().fromJson(  result, Integer.class);

        assertThat(fib_of_10).isEqualTo(89);

    }
}