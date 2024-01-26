package dev.langchain4j.model.input;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class DefaultPromptTemplateFactory implements PromptTemplateFactory {

    @Override
    public DefaultTemplate create(PromptTemplateFactory.Input input) {
        return new DefaultTemplate(input.getTemplate());
    }

    static class DefaultTemplate implements Template {

        private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

        private final String template;
        private final Set<String> allVariables;

        public DefaultTemplate(String template) {
            this.template = ensureNotBlank(template, "template");
            this.allVariables = extractVariables(template);
        }

        private static Set<String> extractVariables(String template) {
            Set<String> variables = new HashSet<>();
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
            return variables;
        }

        public String render(Map<String, Object> variables) {
            ensureAllVariablesProvided(variables);

            String result = template;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                result = replaceAll(result, entry.getKey(), entry.getValue());
            }

            return result;
        }

        private void ensureAllVariablesProvided(Map<String, Object> providedVariables) {
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
}
