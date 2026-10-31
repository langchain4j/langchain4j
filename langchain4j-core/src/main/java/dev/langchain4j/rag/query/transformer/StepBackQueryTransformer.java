package dev.langchain4j.rag.query.transformer;

import static java.util.Arrays.asList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link QueryTransformer} that generates an additional "step-back" query
 * representing a broader, higher-level concept behind the user's original query.
 *
 * <p>This technique is commonly used in Retrieval-Augmented Generation (RAG)
 * systems to improve document retrieval quality. While the original query
 * targets a specific detail or scenario, the step-back query captures the
 * underlying concept, allowing the retriever to fetch more general background
 * information.</p>
 *
 * <p>For example:</p>
 *
 * <pre>
 * Original Query:
 * How does backpropagation work in CNNs?
 *
 * Step-Back Query:
 * What is backpropagation in neural networks?
 * </pre>
 *
 * <p>By issuing both queries to the retriever, the system can retrieve:</p>
 * <ul>
 *     <li>documents that directly answer the user's specific question</li>
 *     <li>documents that explain the broader concept required to understand the answer</li>
 * </ul>
 *
 * <p>This transformer uses a {@link ChatModel} to generate the step-back query
 * from the user's input query and optional conversation context. If chat memory
 * is available in the query metadata, it is included in the prompt to help the
 * model understand references and context from the conversation.</p>
 *
 * <p>The transformation produces two queries:</p>
 * <ol>
 *     <li>The original query</li>
 *     <li>The generated step-back query</li>
 * </ol>
 *
 * <p>Both queries retain the original {@link dev.langchain4j.rag.query.Metadata}
 * (if present) and are returned together so that downstream retrievers can
 * search using both the specific and conceptual forms of the question.</p>
 *
 * <p>This approach is particularly effective for complex queries where
 * retrieving background knowledge improves the quality of generated answers.</p>
 *
 * @see QueryTransformer
 * @see Query
 */
public class StepBackQueryTransformer implements QueryTransformer {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
Generate a broader, more general question that captures the high-level concept
behind the user's query. This question will help retrieve background knowledge.

Use the conversation if needed to understand the topic.

Conversation:
{{chatMemory}}

User query:
{{query}}

Return only the step-back question.
""");

    private final ChatModel chatModel;
    private final PromptTemplate promptTemplate;

    public StepBackQueryTransformer(ChatModel chatModel) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE);
    }

    public StepBackQueryTransformer(ChatModel chatModel, PromptTemplate promptTemplate) {
        this.chatModel = Objects.requireNonNull(chatModel);
        this.promptTemplate = promptTemplate == null ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
    }

    @Override
    public Collection<Query> transform(Query query) {

        List<ChatMessage> chatMemory =
                query.metadata() != null ? query.metadata().chatMemory() : null;

        String memory = format(chatMemory);

        Prompt prompt = createPrompt(query.text(), memory);

        String stepBackQueryText = chatModel.chat(prompt.text());

        Query stepBackQuery = query.metadata() == null
                ? Query.from(stepBackQueryText)
                : Query.from(stepBackQueryText, query.metadata());

        // return both original and step-back queries
        return asList(query, stepBackQuery);
    }

    private Prompt createPrompt(String query, String memory) {

        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query);
        variables.put("chatMemory", memory);

        return promptTemplate.apply(variables);
    }

    private String format(List<ChatMessage> messages) {

        if (messages == null) return "";

        StringBuilder sb = new StringBuilder();

        for (ChatMessage message : messages) {

            if (message instanceof UserMessage user) {
                sb.append("User: ").append(user.singleText()).append("\n");
            }

            if (message instanceof AiMessage ai) {
                if (ai.text() != null) {
                    sb.append("AI: ").append(ai.text()).append("\n");
                }
            }
        }

        return sb.toString();
    }
}
