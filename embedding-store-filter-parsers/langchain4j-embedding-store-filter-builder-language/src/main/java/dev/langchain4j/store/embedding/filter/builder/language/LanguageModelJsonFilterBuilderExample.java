package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Arrays;

public class LanguageModelJsonFilterBuilderExample {

    public static void main(String[] args) {
        // Setup the language model (you'll need to provide your API key)
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required");
        }
        
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                 .apiKey(apiKey)
                 .modelName("gpt-4o")
                 .build();

        // Define the table structure with metadata columns using type-safe approach
        TableDefinition tableDefinition = new TableDefinition(Arrays.asList(
                new TableDefinition.ColumnDefinition("title", String.class, "The title of the document"),
                new TableDefinition.ColumnDefinition("author", String.class, "The author of the document"),
                new TableDefinition.ColumnDefinition("category", DocumentCategory.class, "The category or topic of the document"),
                new TableDefinition.ColumnDefinition("publishDate", java.time.LocalDate.class, "The publication date in ISO format"),
                new TableDefinition.ColumnDefinition("rating", Number.class, "The rating score from 1 to 5"),
                new TableDefinition.ColumnDefinition("status", DocumentStatus.class, "The document status"),
                new TableDefinition.ColumnDefinition("isPublished", Boolean.class, "Whether the document is published"),
                new TableDefinition.ColumnDefinition("wordCount", Number.class, "The number of words in the document")
        ));

        // Create the filter builder
        LanguageModelJsonFilterBuilder filterBuilder = new LanguageModelJsonFilterBuilder(chatModel, tableDefinition);

        // Example natural language queries demonstrating hybrid search
        String[] queries = {
                "Find documents by John Doe",
                "Show me articles about machine learning published after 2023-01-01",
                "Get documents about artificial intelligence with rating greater than 4",
                "Find published documents about Swiss mountains in the 'technology' category",
                "Show documents about climate change with more than 1000 words and rating at least 4",
                "Find unpublished documents about renewable energy by either Alice Smith or Bob Johnson",
                "Get documents about data science published between 2022-01-01 and 2023-12-31",
                "Show me all documents about neural networks created during the last 3 months",
                "Find documents about blockchain published yesterday",
                "Get documents about cryptocurrency created this year",
                "Show me articles about quantum computing from last week",
                "Find documents by authors whose name contains 'Smith' about robotics"
        };

        System.out.println("Hybrid Search Filter Builder Examples");
        System.out.println("====================================");

        for (String query : queries) {
            try {
                System.out.println("\nQuery: " + query);
                FilterResult result = filterBuilder.buildFilterAndQuery(query);
                System.out.println("Generated Filter: " + result.getFilter());
                System.out.println("Modified Query: " + result.getModifiedQuery());
            } catch (Exception e) {
                System.err.println("Error processing query '" + query + "': " + e.getMessage());
            }
        }
    }

    public enum DocumentStatus {
        DRAFT,
        REVIEW,
        PUBLISHED,
        ARCHIVED
    }

    public enum DocumentCategory {
        TECHNOLOGY,
        SCIENCE,
        BUSINESS,
        HEALTH,
        EDUCATION
    }


}