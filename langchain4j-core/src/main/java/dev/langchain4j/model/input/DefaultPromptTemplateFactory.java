package dev.langchain4j.model.input;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
class DefaultPromptTemplateFactory implements PromptTemplateFactory {

    @Override
    public DefaultTemplate create(PromptTemplateFactory.Input input) {
        return new DefaultTemplate(input.getTemplate());
    }

    static class DefaultTemplate implements Template {

        /**
         * A regular expression pattern for identifying variable placeholders within double curly braces in a template string.
         * Variables are denoted as <code>{{variable_name}}</code> or <code>{{ variable_name }}</code>,
         * where spaces around the variable name are allowed.
         * <p>
         * This pattern is used to match and extract variables from a template string for further processing,
         * such as replacing these placeholders with their corresponding values.
         */
        @SuppressWarnings({"RegExpRedundantEscape"})
        private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");

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
                variables.add(matcher.group(1).trim());
            }
            return variables;
        }

        public String render(Map<String, Object> variables) {
            ensureNotNull(variables, "variables");

            if (allVariables.isEmpty()) {
                return template;
            }

            ensureAllVariablesProvided(variables);

            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            StringBuffer result = new StringBuffer(template.length());
            while (matcher.find()) {
                String variable = matcher.group(1).trim();
                Object value = variables.get(variable);
                if (value == null || value.toString() == null) {
                    throw illegalArgument("Value for the variable '%s' is null", variable);
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
            }
            matcher.appendTail(result);

            return result.toString();
        }

        private void ensureAllVariablesProvided(Map<String, Object> providedVariables) {
            for (String variable : allVariables) {
                if (!providedVariables.containsKey(variable)) {
                    throw illegalArgument("Value for the variable '%s' is missing", variable);
                }
            }
        }
    }
}
