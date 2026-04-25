# LangChain4j Language Model Filter Builder

A LangChain4j extension that converts natural language queries into structured Filter objects using language models and JSON Schema-based structured outputs.

## Overview

This module enables you to convert user queries like "Find documents by John Doe published after 2023 with rating above 4" into both:
1. A structured LangChain4j Filter object for metadata constraints
2. A modified query focused on semantic content for embedding search

This is particularly useful for implementing hybrid search in RAG applications where users need to express both semantic intent and metadata constraints in natural language.

## Features

- **Natural Language to Filter Conversion**: Automatically extracts metadata constraints from natural language
- **Semantic Query Extraction**: Separates semantic content from structural constraints
- **Comprehensive Filter Support**: Supports all LangChain4j filter types (comparison, set, logical operations)
- **Type Safety**: Strongly typed column definitions with automatic type conversion
- **Enum Support**: Automatic handling of enum types with value validation
- **Date Intelligence**: Smart handling of relative dates ("last week", "this month")
- **JSON Schema Validation**: Uses structured outputs for reliable filter generation

## Quick Start

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embedding-store-filter-builder-language</artifactId>
    <version>1.1.0-beta7-SNAPSHOT</version>
</dependency>
```

```java
// Define your metadata schema
TableDefinition tableDefinition = TableDefinition.builder()
    .addColumn("author", String.class, "Document author")
    .addColumn("rating", Number.class, "User rating from 1-5")
    .addColumn("publishDate", LocalDate.class, "Publication date")
    .build();

// Create filter builder
LanguageModelJsonFilterBuilder builder = new LanguageModelJsonFilterBuilder(
    chatModel, tableDefinition);

// Convert natural language to filter
FilterResult result = builder.buildFilterAndQuery(
    "Find documents by Alice with rating above 4 about machine learning");

Filter filter = result.getFilter(); 
// AND(IsEqualTo("author", "Alice"), IsGreaterThan("rating", 4))

String query = result.getModifiedQuery(); 
// "machine learning"
```

## Use Cases

- **Hybrid Search**: Combine semantic search with metadata filtering
- **Natural Language Database Queries**: Allow users to query document repositories naturally
- **RAG Applications**: Enhance retrieval with both semantic and metadata constraints
- **Content Management**: Enable intuitive content discovery and filtering
- **Search Interfaces**: Build user-friendly search experiences

## Requirements

- Java 17+
- A language model that supports ResponseFormat.JSON (OpenAI GPT-3.5/4, Azure OpenAI, etc.)
- LangChain4j 1.1.0+

## Architecture

The module uses a multi-step process:

1. **Schema Definition**: TableDefinition describes available metadata columns
2. **Prompt Generation**: Creates a structured prompt with column descriptions and current date context
3. **LLM Processing**: Language model generates JSON using structured output schemas
4. **Filter Parsing**: JSON is parsed into LangChain4j Filter objects with type conversion
5. **Query Modification**: Semantic content is extracted for embedding search

## Column Types

Supported column types include:

- `String.class` - Text values
- `Number.class` - Numeric values (integers, floats)
- `Boolean.class` - Boolean values
- `LocalDate.class` - Date values
- `LocalDateTime.class` - Date-time values
- `Enum.class` - Enumeration values (automatically provides possible values to LLM)

## Filter Types

All LangChain4j filter types are supported:

- **Comparison**: `IsEqualTo`, `IsNotEqualTo`, `IsGreaterThan`, `IsLessThan`, etc.
- **String**: `ContainsString`
- **Set**: `IsIn`, `IsNotIn`
- **Logical**: `And`, `Or`, `Not` (with recursive nesting)

## Examples

See the [documentation](../../docs/docs/integrations/filter-builders/language-model.md) for comprehensive examples and the [examples repository](https://github.com/langchain4j/langchain4j-examples) for working code samples.

## Contributing

This module follows LangChain4j contribution guidelines. Please see [CONTRIBUTING.md](../../CONTRIBUTING.md) for details.

## License

This project is licensed under the Apache License 2.0 - see the main LangChain4j repository for details.