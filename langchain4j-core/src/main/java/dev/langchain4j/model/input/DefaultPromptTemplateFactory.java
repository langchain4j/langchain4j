package dev.langchain4j.model.input;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        @SuppressWarnings("unchecked")
        @Override
        public List<Content> renderContents(Map<String, Object> variables) {
            ensureNotNull(variables, "variables");

            if (allVariables.isEmpty()) {
                return List.of(TextContent.from(template));
            }

            ensureAllVariablesProvided(variables);

            List<Content> segments = new ArrayList<>();
            StringBuilder pendingText = new StringBuilder();
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            int lastEnd = 0;

            while (matcher.find()) {
                pendingText.append(template, lastEnd, matcher.start());

                String variable = matcher.group(1).trim();
                Object value = variables.get(variable);
                if (value == null || value.toString() == null) {
                    throw illegalArgument("Value for the variable '%s' is null", variable);
                }

                if (value instanceof Content content) {
                    flushPendingAsText(segments, pendingText);
                    segments.add(content);
                } else if (isListOfContents(value)) {
                    flushPendingAsText(segments, pendingText);
                    segments.addAll((List<Content>) value);
                } else {
                    pendingText.append(value.toString());
                }

                lastEnd = matcher.end();
            }

            pendingText.append(template, lastEnd, template.length());
            flushPendingAsText(segments, pendingText);

            if (segments.isEmpty()) {
                throw illegalArgument(
                        "Applying the template yielded no textual or multimodal content; check substitutions");
            }

            return List.copyOf(segments);
        }

        private static void flushPendingAsText(List<Content> target, StringBuilder pendingText) {
            if (pendingText.length() == 0) {
                return;
            }
            String raw = pendingText.toString();
            if (!isNullOrBlank(raw)) {
                target.add(TextContent.from(raw));
            }
            pendingText.setLength(0);
        }

        private static boolean isListOfContents(Object value) {
            return value instanceof List<?> list && list.stream().allMatch(Content.class::isInstance);
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
