package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Experimental
public class ToolsResultMemory {

    static Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\((.*?)\\)");

    Map<String, String> variables = new HashMap<>();

    public void addVariable(String var, String value) {
        variables.put(var, value);
    }

    public AiMessage substituteAiMessage(AiMessage message) {
        if (message.text() == null) {
            return message;
        }
        // TODO: Discuss with langchain the best approach
        // return new AiMessage(substituteArguments(message.text(), variables), message.toolExecutionRequests());
        message.updateText(substituteVariables(message.text(), variables));
        return message;
    }

    public ToolExecutionRequest substituteArguments(ToolExecutionRequest toolExecutionRequest) {
        return ToolExecutionRequest.builder()
                .id(toolExecutionRequest.id())
                .name(toolExecutionRequest.name())
                .arguments(substituteVariables(toolExecutionRequest.arguments(), variables)).build();
    }

    private static String substituteVariables(String msg, Map<String, String> resultMap) {
        Matcher matcher = VARIABLE_PATTERN.matcher(msg);
        StringBuffer newArguments = new StringBuffer();
        if(!matcher.find()) {
            return msg;
        }
        do {
            String key = matcher.group(1);
            String replacement = resultMap.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(newArguments, replacement);
        } while (matcher.find());
        matcher.appendTail(newArguments);
        return newArguments.toString();
    }
}
