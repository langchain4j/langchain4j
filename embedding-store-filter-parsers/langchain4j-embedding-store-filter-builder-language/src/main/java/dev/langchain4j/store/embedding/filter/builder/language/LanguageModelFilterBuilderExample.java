package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.time.LocalDate;
import java.util.List;

/**
 * Comprehensive example demonstrating the Language Model Filter Builder
 * for converting natural language queries into structured filters.
 */
public class LanguageModelFilterBuilderExample {

    // Example enum for document categories
    public enum DocumentCategory {
        RESEARCH, TUTORIAL, BLOG_POST, DOCUMENTATION, NEWS
    }

    public static void main(String[] args) {
        
        // Set up models (you'll need to provide your API key)
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .build();
                
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        // Define the metadata schema for your documents
        TableDefinition tableDefinition = TableDefinition.builder()
                .addColumn("author", String.class, "The document author or creator")
                .addColumn("publishDate", LocalDate.class, "Date when the document was published")
                .addColumn("rating", Number.class, "User rating from 1-5 stars")
                .addColumn("category", DocumentCategory.class, "Type or category of the document")
                .addColumn("tags", String.class, "Comma-separated list of tags")
                .addColumn("wordCount", Number.class, "Number of words in the document")
                .addColumn("language", String.class, "Primary language of the document")
                .build();

        // Create the filter builder
        LanguageModelJsonFilterBuilder filterBuilder = 
                new LanguageModelJsonFilterBuilder(chatModel, tableDefinition);

        // Create an embedding store and add some sample documents
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        populateEmbeddingStore(embeddingStore, embeddingModel);

        // Example queries demonstrating different types of filtering
        demonstrateFilterBuilding(filterBuilder);
        
        // Example of using filters with embedding store search
        demonstrateHybridSearch(filterBuilder, embeddingStore, embeddingModel);
    }

    /**
     * Demonstrates various types of natural language queries and their filter conversion
     */
    private static void demonstrateFilterBuilding(LanguageModelJsonFilterBuilder filterBuilder) {
        System.out.println("=== Filter Building Examples ===\n");

        String[] exampleQueries = {
            "Find documents by Alice Johnson about machine learning",
            "Show me research papers published after 2023-01-01 with rating above 4",
            "Find tutorials or blog posts about Java programming with more than 1000 words",
            "Show me documentation in English published this year",
            "Find highly rated articles (rating >= 4) about artificial intelligence or neural networks",
            "Show me recent blog posts (published in the last 6 months) about web development"
        };

        for (String query : exampleQueries) {
            System.out.println("Query: " + query);
            
            try {
                FilterResult result = filterBuilder.buildFilterAndQuery(query);
                
                System.out.println("Generated Filter: " + result.getFilter());
                System.out.println("Modified Query: \"" + result.getModifiedQuery() + "\"");
                System.out.println("---");
                
            } catch (Exception e) {
                System.out.println("Error processing query: " + e.getMessage());
                System.out.println("---");
            }
        }
        System.out.println();
    }

    /**
     * Demonstrates using the filter builder in a hybrid search scenario
     */
    private static void demonstrateHybridSearch(
            LanguageModelJsonFilterBuilder filterBuilder,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        
        System.out.println("=== Hybrid Search Example ===\n");

        String userQuery = "Find recent research papers about deep learning with high ratings";
        
        // Convert natural language to filter + semantic query
        FilterResult result = filterBuilder.buildFilterAndQuery(userQuery);
        
        System.out.println("Original Query: " + userQuery);
        System.out.println("Extracted Filter: " + result.getFilter());
        System.out.println("Semantic Query: \"" + result.getModifiedQuery() + "\"");
        
        // Embed the semantic query
        Embedding queryEmbedding = embeddingModel.embed(result.getModifiedQuery()).content();
        
        // Search with both semantic similarity and metadata constraints
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                queryEmbedding,
                5,  // maxResults
                0.7, // minScore
                result.getFilter()
        );
        
        System.out.println("\nSearch Results:");
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();
            
            System.out.printf("%d. Score: %.3f%n", i + 1, match.score());
            System.out.println("   Title: " + metadata.getString("title"));
            System.out.println("   Author: " + metadata.getString("author"));
            System.out.println("   Category: " + metadata.getString("category"));
            System.out.println("   Rating: " + metadata.getInteger("rating"));
            System.out.println("   Content: " + segment.text().substring(0, Math.min(100, segment.text().length())) + "...");
            System.out.println();
        }
    }

    /**
     * Populates the embedding store with sample documents
     */
    private static void populateEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        
        // Sample documents with rich metadata
        Document[] sampleDocs = {
            createDocument(
                "Deep Learning Fundamentals", 
                "This research paper explores the mathematical foundations of deep neural networks and their applications in computer vision.",
                "Alice Johnson", LocalDate.of(2023, 6, 15), 5, DocumentCategory.RESEARCH, "deep learning,neural networks,computer vision", 2500, "en"
            ),
            createDocument(
                "Getting Started with Java", 
                "A comprehensive tutorial for beginners learning Java programming language with practical examples.",
                "Bob Smith", LocalDate.of(2023, 3, 10), 4, DocumentCategory.TUTORIAL, "java,programming,tutorial", 1800, "en"
            ),
            createDocument(
                "Web Development Best Practices", 
                "Blog post discussing modern web development practices including React, Node.js, and responsive design.",
                "Carol Davis", LocalDate.of(2023, 9, 22), 4, DocumentCategory.BLOG_POST, "web development,react,nodejs", 1200, "en"
            ),
            createDocument(
                "API Documentation Guide", 
                "Complete documentation for our REST API including authentication, endpoints, and examples.",
                "DevTeam", LocalDate.of(2023, 1, 5), 3, DocumentCategory.DOCUMENTATION, "api,rest,documentation", 3000, "en"
            ),
            createDocument(
                "Machine Learning in Healthcare", 
                "Recent advances in applying machine learning techniques to medical diagnosis and treatment planning.",
                "Dr. Emily Chen", LocalDate.of(2023, 8, 30), 5, DocumentCategory.RESEARCH, "machine learning,healthcare,medical", 2800, "en"
            )
        };

        // Add documents to the embedding store
        for (Document doc : sampleDocs) {
            TextSegment segment = TextSegment.from(doc.text(), doc.metadata());
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }
    }

    /**
     * Helper method to create documents with metadata
     */
    private static Document createDocument(String title, String content, String author, 
                                         LocalDate publishDate, int rating, DocumentCategory category, 
                                         String tags, int wordCount, String language) {
        Metadata metadata = Metadata.builder()
                .put("title", title)
                .put("author", author)
                .put("publishDate", publishDate.toString())
                .put("rating", rating)
                .put("category", category.toString())
                .put("tags", tags)
                .put("wordCount", wordCount)
                .put("language", language)
                .build();
                
        return Document.from(content, metadata);
    }
}