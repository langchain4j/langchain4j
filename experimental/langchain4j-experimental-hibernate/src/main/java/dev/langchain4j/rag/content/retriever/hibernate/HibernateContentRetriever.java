package dev.langchain4j.rag.content.retriever.hibernate;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.tool.language.internal.MetamodelJsonSerializerImpl;
import org.hibernate.tool.language.internal.ResultsJsonSerializerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Using the {@link SessionFactory} and the {@link ChatModel}, this {@link ContentRetriever}
 * attempts to generate and execute Hibernate queries for given natural language queries.
 * <br>
 * The generated HQL is guaranteed to be a SELECT statement, as Hibernate's own query parser will
 * reject any non-SELECT HQL. Furthermore, only data contained in tables mapped by entities within
 * the provided {@link SessionFactory} can be accessed, and existing filters/restrictions will apply.
 * <br>
 * <b>
 * WARNING! All mapped data can be accessed by the content retriever. Make sure the provided {@link SessionFactory}
 * only has access to entities that do not contain sensitive information.
 * </b>
 * <br>
 * <br>
 * The entity model structure is automatically extracted from Hibernate's metamodel and provided
 * to the LLM to generate valid HQL queries.
 * <br>
 * Optionally, {@link #databaseStructure}, {@link #promptTemplate}, and {@link #maxRetries} can be specified
 * to customize the behavior. See the javadoc of the constructor for more details.
 * Most methods can be overridden to customize the behavior further.
 * <br>
 * The default prompt template is not highly optimized,
 * so it is advised to experiment with it and see what works best for your use case.
 */
@Experimental
public class HibernateContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(HibernateContentRetriever.class);

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE =
            PromptTemplate.from("You are an expert in writing Hibernate Query Language (HQL) queries.\n"
                    + "You have access to an entity model with the following structure:\n"
                    + "{{databaseStructure}}\n"
                    + "If a user asks a question that can be answered by querying this model, generate an HQL SELECT query.\n"
                    + "The query must not include any input parameters.\n"
                    + "Do not output anything else aside from a valid HQL statement!");

    private final SessionFactory sessionFactory;
    private final String databaseStructure;
    private final PromptTemplate promptTemplate;
    private final ChatModel chatModel;
    private final int maxRetries;

    /**
     * Creates an instance of a {@code HibernateContentRetriever}.
     *
     * @param sessionFactory    The {@link SessionFactory} to be used for executing HQL queries and extracting the metamodel.
     *                          This is a mandatory parameter.
     * @param chatModel         The {@link ChatModel} to be used for generating HQL queries.
     *                          This is a mandatory parameter.
     * @param databaseStructure The structure of the entity model, which will be provided to the LLM in the system message.
     *                          The LLM should be familiar with available entities, attributes, relationships, etc.
     *                          in order to generate valid HQL queries.
     *                          This is an optional parameter. If not specified, it will be generated from the
     *                          {@link SessionFactory}'s runtime metamodel automatically (recommended).
     * @param promptTemplate    The {@link PromptTemplate} to be used for creating the system message.
     *                          This is an optional parameter. Default: {@link #DEFAULT_PROMPT_TEMPLATE}.
     * @param maxRetries        The maximum number of retries to perform if Hibernate cannot execute the generated HQL query.
     *                          An error message will be sent back to the LLM to try correcting the query.
     *                          This is an optional parameter. Default: 0.
     */
    @Experimental
    public HibernateContentRetriever(
            SessionFactory sessionFactory,
            ChatModel chatModel,
            String databaseStructure,
            PromptTemplate promptTemplate,
            Integer maxRetries) {
        this.sessionFactory = ensureNotNull(sessionFactory, "sessionFactory");
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.databaseStructure = getOrDefault(databaseStructure, () -> defaultDatabaseStructure(sessionFactory));
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.maxRetries = getOrDefault(maxRetries, 0);
    }

    public static HibernateContentRetrieverBuilder builder() {
        return new HibernateContentRetrieverBuilder();
    }

    protected static String defaultDatabaseStructure(SessionFactory sessionFactory) {
        // By default, use Hibernate's metamodel to generate a description of the entity model structure.
        // We use our default MetamodelJsonSerializerImpl, which produces a JSON representation of the metamodel
        return MetamodelJsonSerializerImpl.INSTANCE.toString(sessionFactory.getMetamodel());
    }

    @Override
    public List<Content> retrieve(Query naturalLanguageQuery) {
        String hqlQuery = null;
        String errorMessage = null;

        int attemptsLeft = maxRetries + 1;
        while (attemptsLeft > 0) {
            attemptsLeft--;

            hqlQuery = generateHqlQuery(naturalLanguageQuery, hqlQuery, errorMessage);

            hqlQuery = clean(hqlQuery);

            log.debug("Generated HQL query: {}", hqlQuery);

            try {
                String result = execute(hqlQuery);
                Content content = format(result, hqlQuery);
                return singletonList(content);
            } catch (Exception e) {
                errorMessage = e.getMessage();
                if (attemptsLeft > 0) {
                    log.warn("HQL execution failed, retrying (attempts left: {}): {}", attemptsLeft, errorMessage);
                } else {
                    log.warn("HQL execution failed, no retries left: {}", errorMessage);
                }
            }
        }

        return emptyList();
    }

    protected String generateHqlQuery(
            Query naturalLanguageQuery, String previousHqlQuery, String previousErrorMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(createSystemPrompt().toSystemMessage());
        messages.add(UserMessage.from(naturalLanguageQuery.text()));

        if (previousHqlQuery != null && previousErrorMessage != null) {
            messages.add(AiMessage.from(previousHqlQuery));
            messages.add(UserMessage.from(previousErrorMessage));
        }

        return chatModel.chat(messages).aiMessage().text();
    }

    protected Prompt createSystemPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("databaseStructure", databaseStructure);
        return promptTemplate.apply(variables);
    }

    protected String clean(String hqlQuery) {
        if (hqlQuery.contains("```hql")) {
            return hqlQuery.substring(hqlQuery.indexOf("```hql") + 6, hqlQuery.lastIndexOf("```"));
        } else if (hqlQuery.contains("```sql")) {
            return hqlQuery.substring(hqlQuery.indexOf("```sql") + 6, hqlQuery.lastIndexOf("```"));
        } else if (hqlQuery.contains("```")) {
            return hqlQuery.substring(hqlQuery.indexOf("```") + 3, hqlQuery.lastIndexOf("```"));
        }
        return hqlQuery;
    }

    protected String execute(String hqlQuery) {
        return sessionFactory.fromStatelessSession(session -> {
            // We create a selection query, ensuring the generated HQL is a SELECT statement.
            // If not, an exception will be thrown by Hibernate.
            var query = session.createSelectionQuery(hqlQuery, Object.class);
            var results = query.getResultList();
            try {
                // We use our own custom serializer to convert the results (which might include lazy/circular
                // associations)
                // into a JSON string that can be sent back to the LLM.
                return new ResultsJsonSerializerImpl((SessionFactoryImplementor) sessionFactory)
                        .toString(results, query);
            } catch (IOException e) {
                throw new RuntimeException("Error during query results serialization", e);
            }
        });
    }

    private static Content format(String result, String hqlQuery) {
        return Content.from(String.format("Result of executing '%s':\n%s", hqlQuery, result));
    }

    public static class HibernateContentRetrieverBuilder {
        private SessionFactory sessionFactory;
        private ChatModel chatModel;
        private String databaseStructure;
        private PromptTemplate promptTemplate;
        private Integer maxRetries;

        HibernateContentRetrieverBuilder() {}

        /**
         * Sets the {@link SessionFactory} to be used for executing HQL queries and extracting the metamodel.
         * This is a mandatory parameter (either this or {@link #entityManagerFactory(EntityManagerFactory)} must be set).
         *
         * @param sessionFactory the Hibernate {@link SessionFactory}
         * @return this builder
         */
        public HibernateContentRetrieverBuilder sessionFactory(SessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
            return this;
        }

        /**
         * Sets the {@link EntityManagerFactory} to be used for executing HQL queries and extracting the metamodel.
         * The provided {@link EntityManagerFactory} will be unwrapped to a Hibernate {@link SessionFactory}.
         * This is a mandatory parameter (either this or {@link #sessionFactory(SessionFactory)} must be set).
         *
         * @param entityManagerFactory the Jakarta Persistence {@link EntityManagerFactory}
         * @return this builder
         */
        public HibernateContentRetrieverBuilder entityManagerFactory(EntityManagerFactory entityManagerFactory) {
            this.sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            return this;
        }

        /**
         * Sets the {@link ChatModel} to be used for generating HQL queries from natural language.
         * This is a mandatory parameter.
         *
         * @param chatModel the {@link ChatModel} to use
         * @return this builder
         */
        public HibernateContentRetrieverBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * Sets the structure of the entity model, which will be provided to the LLM in the system message.
         * The LLM should be familiar with available entities, attributes, relationships, etc.
         * in order to generate valid HQL queries.
         * This is an optional parameter. If not specified, it will be automatically generated from the
         * {@link SessionFactory}'s runtime metamodel (recommended).
         *
         * @param databaseStructure a string representation of the entity model structure
         * @return this builder
         */
        public HibernateContentRetrieverBuilder databaseStructure(String databaseStructure) {
            this.databaseStructure = databaseStructure;
            return this;
        }

        /**
         * Sets the {@link PromptTemplate} to be used for creating the system message.
         * The template can use the {@code {{databaseStructure}}} variable, which will be replaced
         * with the entity model structure.
         * This is an optional parameter. Default: {@link #DEFAULT_PROMPT_TEMPLATE}.
         *
         * @param promptTemplate the {@link PromptTemplate} to use
         * @return this builder
         */
        public HibernateContentRetrieverBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        /**
         * Sets the maximum number of retries to perform if Hibernate cannot execute the generated HQL query.
         * On each retry, the error message will be sent back to the LLM to try correcting the query.
         * This is an optional parameter. Default: 0.
         *
         * @param maxRetries the maximum number of retries
         * @return this builder
         */
        public HibernateContentRetrieverBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Builds a new {@link HibernateContentRetriever} instance with the configured parameters.
         *
         * @return a new {@link HibernateContentRetriever}
         */
        public HibernateContentRetriever build() {
            return new HibernateContentRetriever(
                    this.sessionFactory, this.chatModel, this.databaseStructure, this.promptTemplate, this.maxRetries);
        }

        @Override
        public String toString() {
            return "HibernateContentRetriever.HibernateContentRetrieverBuilder("
                    + "sessionFactory=" + this.sessionFactory
                    + ", chatModel=" + this.chatModel
                    + ", databaseStructure=" + this.databaseStructure
                    + ", promptTemplate=" + this.promptTemplate
                    + ", maxRetries=" + this.maxRetries
                    + ")";
        }
    }
}
