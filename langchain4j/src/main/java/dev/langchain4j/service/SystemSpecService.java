package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.SystemSpec;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Provides services to dynamically select system specifications based on user input
 * utilizing an AI model. This class includes constants for default system setups and
 * methods to fetch system specifications dynamically using an AI-driven decision process.
 *
 * <p>The class leverages a static logger for logging important information and errors during
 * the operation of methods. It defines a default system specification that sets up a context
 * for AI responses aimed at expert-level communication.</p>
 *
 * <p>Additionally, this service includes a method to route user input through various
 * system prompts to select the most appropriate system specification based on the AI's analysis
 * of the input.</p>
 *
 * <h3>Usage:</h3>
 * <p>This class is primarily used for integrating with AI services to handle complex input
 * and provide appropriate system responses based on the context of the input and pre-defined system specs.</p>
 *
 * <h3>Key Components:</h3>
 * <ul>
 *   <li>DEFAULT_SYSTEM_SPEC - Provides a default configuration for system prompts.</li>
 *   <li>MULTI_PROMPT_ROUTER_TEMPLATE - Template used for generating dynamic system selection prompts.</li>
 * </ul>
 */
public class SystemSpecService {

    static final Logger logger = LoggerFactory.getLogger(SystemSpecService.class);

    private static final String DEFAULT_SYSTEM_NAME = "DEFAULT_SYSTEM";
    private static final String DEFAULT_SYSTEM_PROMPT_DESCRIPTION = "This prompt sets up the AI to generate responses that are not only informative " +
            "but also suitably pitched to the level of expertise expected from a leading specialist in the specified field. " +
            "It encourages detailed, expert-level communication, enhancing the user's experience by providing a credible and authoritative answer.";
    public static final String DEFAULT_SYSTEM_TEMPLATE = "Assume the role of a leading expert in the relevant field for the question provided. " +
            "Your task is to formulate a response that demonstrates deep knowledge and expertise. Ensure the answer is thorough, accurate, " +
            "and conveys confidence in the subject matter. Additionally, the response should be engaging and crafted in a way that educates " +
            "the questioner without overwhelming them. Prepare to adapt your expertise based on the content of the question to provide the " +
            "best possible answer. Below is the question you need to respond to:\n" +
            "\n" +
            "%s\n" +
            "\n" +
            "Tailor your response to act as if you are the most appropriate expert to answer this query.";
    public static final SystemSpec DEFAULT_SYSTEM_SPEC = SystemSpec.builder()
            .name(DEFAULT_SYSTEM_NAME)
            .description(DEFAULT_SYSTEM_PROMPT_DESCRIPTION)
            .template(new String[]{DEFAULT_SYSTEM_TEMPLATE})
            .build();

    public static final String MULTI_PROMPT_ROUTER_TEMPLATE = "Given a raw text input to a language model select the model prompt best suited for \n" +
            "the input. You will be given the names of the available prompts and a description of \n" +
            "what the prompt is best suited for. You may also revise the original input if you \n" +
            "think that revising it will ultimately lead to a better response from the language \n" +
            "model.\n" +
            "<< FORMATTING >>\n" +
            "Return a markdown code snippet with a JSON object formatted to look like:\n" +
            "```json\n" +
            "{{{{\n" +
            "    \"destination\": string \\\\ name of the prompt to use or \"DEFAULT\"\n" +
            "    \"nextInputs\": string \\\\ a potentially modified version of the original input\n" +
            "}}}}\n" +
            "```\n" +
            "\n" +
            "REMEMBER: \"destination\" MUST be one of the candidate prompt names specified below OR \\\n" +
            "it can be \"DEFAULT\" if the input is not well suited for any of the candidate prompts.\n" +
            "REMEMBER: \"next_inputs\" can just be the original input if you don't think any \\\n" +
            "modifications are needed.\n" +
            "\n" +
            "<< CANDIDATE PROMPTS >>\n" +
            "%s\n" +
            "\n" +
            "<< INPUT >>\n" +
            "%s\n" +
            "\n" +
            "<< OUTPUT (must include ```json at the start of the response) >>\n" +
            "<< OUTPUT (must end with ```) >>\n" +
            "\"\"\"";

    public static final String DESTINATION_PATTERN = "%s : %s\n";

    /**
     * Fetches the system specification using an AI model.
     *
     * @param userMessage The message from the user to be incorporated in the prompt for dynamic system selection.
     * @param systemSpecs       The list of system specifications to be used for dynamic system selection.
     * @param model       The chat language model used for generating AI responses.
     * @return The system specification determined by the AI's routing decision, or the default system specification if an error occurs.
     */
    public static SystemSpec fetchSystemSpecUsingAI(String userMessage, List<SystemSpec> systemSpecs, ChatLanguageModel model) {
        // Ensure the userMessage is not Blank otherwise throw exception
        ensureNotBlank(userMessage, "userMessage");
        try {
            // StringBuilder to accumulate the prompt for dynamic system selection
            StringBuilder dynamicSystemSpecsPrompt = new StringBuilder();
            // Map to store DynamicSystemSpec objects for quick lookup by name
            Map<String, SystemSpec> dynamicSystemSpecsMap = new HashMap<>();

            // Populate both the StringBuilder and the Map with dynamic system specifications
            systemSpecs.forEach(spec -> {
                dynamicSystemSpecsPrompt.append(String.format(DESTINATION_PATTERN, spec.getName(), spec.getDescription()));
                dynamicSystemSpecsMap.put(spec.getName(), spec);
            });

            // Log the completed dynamic systems prompt
            logger.trace("Dynamic systems prompt constructed: {}", dynamicSystemSpecsPrompt);

            // Prepare the final prompt to be sent to the AI model by incorporating the user message to choose the right system
            String dynamicSystemSelectorPrompt = String.format(MULTI_PROMPT_ROUTER_TEMPLATE, dynamicSystemSpecsPrompt, userMessage);
            // Log the selector prompt
            logger.debug("Dynamic system selector prompt: {}", dynamicSystemSelectorPrompt);

            // Generate AI response using the selector prompt
            AiMessage aiMessage = model.generate(Collections.singletonList(userMessage(dynamicSystemSelectorPrompt))).content();
            // Log the AI's raw response
            String text = aiMessage.text();
            logger.trace("AI message response: {}", text);

            // Parse the AI message to get dynamic system routing response
            DynamicSystemRoutingResponse dynamicSystemRoutingResponse = Json.fromJson(text, DynamicSystemRoutingResponse.class);
            // Determine which system specification to use based on AI's routing decision
            return dynamicSystemSpecsMap.getOrDefault(dynamicSystemRoutingResponse.getDestination(), DEFAULT_SYSTEM_SPEC);
        } catch (Exception exception) {
            logger.error("Falling back to default dynamic system spec due to the following error: {}", exception.getMessage(), exception);
            return DEFAULT_SYSTEM_SPEC;
        }
    }

    /**
     * Represents a response from the dynamic system routing process.
     */
    @Getter
    private static class DynamicSystemRoutingResponse {
        private final String destination;
        private final String nextInputs;

        @Builder
        public DynamicSystemRoutingResponse(String destination, String nextInputs) {
            this.destination = destination;
            this.nextInputs = nextInputs;
        }
    }
}
