package dev.langchain4j.model.input;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

import dev.langchain4j.spi.prompt.Template;
import dev.langchain4j.spi.prompt.TemplateRenderingEngine;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DefaultTemplateRenderingEngine implements TemplateRenderingEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    @Override
    public Prompt render(final Template template, final Map<String, Object> variables) {

        final var content = template.content();

        final var allVariables = extractVariables(content);

        ensureAllVariablesProvided(allVariables, variables);

        String result = content;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = replaceAll(result, entry.getKey(), entry.getValue());
        }

        return Prompt.from(result);
    }

    private static Set<String> extractVariables(final String template) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private void ensureAllVariablesProvided(
            final Set<String> allVariables, final Map<String, Object> providedVariables) {

        for (String variable : allVariables) {
            if (!providedVariables.containsKey(variable)) {
                throw illegalArgument("Value for the variable '%s' is missing", variable);
            }
        }
    }

    private static String replaceAll(String template, String variable, Object value) {
        if (value == null || value.toString() == null) {
            throw illegalArgument("Value for the variable '%s' is null", variable);
        }
        return template.replace(inDoubleCurlyBrackets(variable), value.toString());
    }

    private static String inDoubleCurlyBrackets(String variable) {
        return "{{" + variable + "}}";
    }
}
