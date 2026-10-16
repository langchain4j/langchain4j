---
sidebar_position: 1
---

# Language Model Filter Builder

Convert natural language queries into LangChain4j Filter objects using language models and structured outputs.

## Overview

The Language Model Filter Builder enables you to convert natural language queries into structured filters for embedding stores. It automatically separates semantic content from metadata constraints, creating proper LangChain4j Filter objects while returning a modified query focused on semantic search.

This is particularly useful for implementing hybrid search where users can express both semantic intent and metadata constraints in natural language.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embedding-store-filter-builder-language</artifactId>
    <version>1.1.0-beta7-SNAPSHOT</version>
</dependency>
```

## APIs

- `LanguageModelJsonFilterBuilder` - Main class for converting natural language to filters
- `TableDefinition` - Describes available metadata columns
- `FilterResult` - Contains the generated filter and modified query
- `FilterSchemas` - JSON schemas for structured outputs

## Basic Usage

```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.filter.builder.language.*;

// Set up the language model
ChatLanguageModel chatModel = OpenAiChatModel.builder()
    .apiKey("your-api-key")
    .modelName("gpt-4")
    .build();

// Define your metadata schema
TableDefinition tableDefinition = TableDefinition.builder()
    .addColumn("author", String.class, "The document author")
    .addColumn("publishDate", java.time.LocalDate.class, "Publication date")
    .addColumn("rating", Number.class, "User rating from 1-5")
    .addColumn("category", CategoryEnum.class, "Document category")
    .build();

// Create the filter builder
LanguageModelJsonFilterBuilder filterBuilder = 
    new LanguageModelJsonFilterBuilder(chatModel, tableDefinition);

// Convert natural language to filter
String query = "Find documents by John Doe published after 2023-01-01 with rating above 4 about artificial intelligence";
FilterResult result = filterBuilder.buildFilterAndQuery(query);

// Use the results
Filter filter = result.getFilter(); 
// Results in: AND(IsEqualTo("author", "John Doe"), AND(IsGreaterThan("publishDate", "2023-01-01"), IsGreaterThan("rating", 4)))

String semanticQuery = result.getModifiedQuery(); 
// Results in: "artificial intelligence"
```

## Advanced Usage with Enum Types

```java
public enum DocumentCategory {
    RESEARCH, TUTORIAL, BLOG_POST, DOCUMENTATION
}

TableDefinition tableDefinition = TableDefinition.builder()
    .addColumn("category", DocumentCategory.class, "Type of document")
    .addColumn("tags", String.class, "Comma-separated tags")
    .addColumn("wordCount", Number.class, "Number of words in document")
    .build();

String query = "Find research papers about machine learning with more than 1000 words";
FilterResult result = filterBuilder.buildFilterAndQuery(query);

// Filter: AND(IsEqualTo("category", "RESEARCH"), IsGreaterThan("wordCount", 1000))
// Query: "machine learning"
```

## Integration with Embedding Stores

```java
// In a RAG application
public List<EmbeddingMatch<TextSegment>> search(String userQuery, EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
    // Convert natural language to filter + semantic query
    FilterResult result = filterBuilder.buildFilterAndQuery(userQuery);
    
    // Embed the semantic query
    Embedding queryEmbedding = embeddingModel.embed(result.getModifiedQuery()).content();
    
    // Search with both semantic similarity and metadata constraints
    return embeddingStore.findRelevant(
        queryEmbedding, 
        10, 
        0.7, 
        result.getFilter()
    );
}
```

## Supported Filter Types

The builder supports all LangChain4j filter types:

- **Comparison filters**: `IS_EQUAL_TO`, `IS_NOT_EQUAL_TO`, `IS_GREATER_THAN`, `IS_LESS_THAN`, etc.
- **String filters**: `CONTAINS_STRING`
- **Set filters**: `IS_IN`, `IS_NOT_IN`
- **Logical filters**: `AND`, `OR`, `NOT` (with recursive nesting)

## Date Handling

The builder automatically handles relative date queries:

```java
String query = "Find documents published in the last week about Java";
// Automatically calculates the actual date range based on current date
```

## Language Model Requirements

Your language model must support:
- **ResponseFormat.JSON**: For structured outputs
- **JSON Schema**: For guided generation
- **Function calling or structured outputs**: Some models may require specific configuration

Tested with:
- OpenAI GPT-3.5/GPT-4
- Azure OpenAI
- Other models supporting JSON mode

## Error Handling

The builder includes validation to detect common issues:

```java
try {
    FilterResult result = filterBuilder.buildFilterAndQuery(query);
} catch (IllegalArgumentException e) {
    // Handle parsing errors, invalid JSON, or unsupported filter types
    logger.error("Failed to parse filter: " + e.getMessage());
}
```

## Examples

- [LanguageModelJsonFilterBuilderExample](https://github.com/langchain4j/langchain4j-examples/blob/main/filter-builder-example/src/main/java/LanguageModelJsonFilterBuilderExample.java)

## Limitations

- Requires a language model that supports JSON structured outputs
- Complex nested logical expressions may require clear prompting
- Performance depends on the language model's response time
- Some edge cases in date parsing may need refinement

## Tips for Better Results

1. **Clear Column Descriptions**: Provide detailed descriptions in your TableDefinition
2. **Consistent Naming**: Use clear, consistent column names
3. **Enum Documentation**: For enum types, the builder automatically provides possible values to the model
4. **Date Formats**: Use standard date formats (YYYY-MM-DD) in your data