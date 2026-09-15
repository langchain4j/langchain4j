package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Translates a {@link Filter} into the text of a Cosmos DB NoSQL {@code WHERE} clause.
 * <p>
 * A {@link Filter} describes a condition on a stored document, such as "category equals electronics".
 * The embedding store passes it here and appends the returned text to the query it sends to Cosmos DB.
 * {@link DefaultAzureCosmosDBNoSqlFilterMapper} is used unless a different mapper is supplied to the
 * store or content retriever builder.
 * <p>
 * Implementations are responsible for the safety of the text they return. Both parts of a filter can
 * come from untrusted input: the value being compared against, and the metadata key naming the property
 * to compare. An implementation that concatenates either into the returned text without escaping lets a
 * caller inject query syntax and read documents the filter was meant to exclude. See
 * {@link DefaultAzureCosmosDBNoSqlFilterMapper} for how the built-in mapper handles this.
 */
public interface AzureCosmosDBNoSqlFilterMapper {
    String map(Filter filter);
}
