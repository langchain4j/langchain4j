package dev.langchain4j.code.graalvm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import org.graalvm.polyglot.Value;

final class GraalVmExecutionResult {

    private GraalVmExecutionResult() {}

    static String fromJavaScript(Value result, ByteArrayOutputStream outputStream) {
        return from(result, outputStream, GraalVmExecutionResult::isJavaScriptEmptyResult);
    }

    static String fromPython(Value result, ByteArrayOutputStream outputStream) {
        return from(result, outputStream, GraalVmExecutionResult::isPythonEmptyResult);
    }

    private static String from(Value result, ByteArrayOutputStream outputStream, Predicate<Value> isEmptyResult) {
        String output = outputStream.toString(StandardCharsets.UTF_8);
        if (output.isEmpty()) {
            return resultString(result);
        }

        output = removeTrailingLineBreaks(output);
        if (isEmptyResult.test(result)) {
            return output;
        }

        return output + "\n" + resultString(result);
    }

    private static String resultString(Value result) {
        return String.valueOf(result.as(Object.class));
    }

    private static boolean isJavaScriptEmptyResult(Value result) {
        return result.isNull() || (!result.isString() && "undefined".equals(String.valueOf(result)));
    }

    private static boolean isPythonEmptyResult(Value result) {
        // GraalPy returns the __main__ module for top-level code without an expression result.
        if (!result.hasMember("__name__")) {
            return false;
        }

        Value name = result.getMember("__name__");
        return name != null && name.isString() && "__main__".equals(name.asString());
    }

    private static String removeTrailingLineBreaks(String string) {
        int end = string.length();
        while (end > 0) {
            char character = string.charAt(end - 1);
            if (character != '\n' && character != '\r') {
                break;
            }
            end--;
        }
        return string.substring(0, end);
    }
}
