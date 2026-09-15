package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete working example demonstrating the EmbeddingStoreContentRetrieverDelegate for hybrid search.
 * This example creates an in-memory embedding store with sample data and shows real hybrid search results.
 */
public class EmbeddingStoreContentRetrieverDelegateExample {

    public static void main(String[] args) {
        // Setup models
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required");
        }
        
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o")
                .build();

        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();

        // Create table definition
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

        // Create filter builder
        LanguageModelJsonFilterBuilder filterBuilder = new LanguageModelJsonFilterBuilder(chatModel, tableDefinition);

        // Create and populate embedding store with sample data
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        populateEmbeddingStore(embeddingStore, embeddingModel);

        // Create the intelligent retriever delegate
        EmbeddingStoreContentRetrieverDelegate intelligentRetriever = 
                EmbeddingStoreContentRetrieverDelegate.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .filterBuilder(filterBuilder)
                        .maxResults(5)
                        .build();

        System.out.println("Hybrid Search Retriever - Complete Working Example");
        System.out.println("=================================================");
        System.out.println();

        // Test queries demonstrating hybrid search
        String[] testQueries = {
                "Find documents about machine learning by John Doe",
                "Show me TECHNOLOGY articles with rating > 4",
                "Get PUBLISHED documents about climate change",
                "Find documents by Alice Smith about artificial intelligence"
        };

        System.out.println("Sample Data Loaded:");
        System.out.println("- 6 documents with embedded metadata about authors, categories, ratings, and status");
        System.out.println("- Topics: AI, ML, Climate, Blockchain, Neural Networks, Business Ethics");
        System.out.println("- Note: This example uses simplified text-based approach for demonstration");
        System.out.println();

        for (String queryText : testQueries) {
            try {
                System.out.println("Query: \"" + queryText + "\"");
                
                try {
                    // Use basic query for semantic search with proper filtering
                    List<Content> results = intelligentRetriever.retrieve(new Query(queryText));
                    System.out.println("  → Results (" + results.size() + " found):");
                    for (int i = 0; i < Math.min(5, results.size()); i++) {
                        Content content = results.get(i);
                        String fullText = content.textSegment().text();
                        System.out.println("    " + (i + 1) + ". " + fullText);
                        
                        // Display actual metadata from the TextSegment
                        Metadata metadata = content.textSegment().metadata();
                        if (metadata != null) {
                            System.out.println("       Author: " + metadata.getString("author") + 
                                             ", Category: " + metadata.getString("category") + 
                                             ", Rating: " + metadata.getInteger("rating") +
                                             ", Status: " + metadata.getString("status"));
                        }
                    }
                } catch (Exception searchException) {
                    System.out.println("  → Error in search: " + searchException.getMessage());
                    searchException.printStackTrace();
                }
                System.out.println();
                
            } catch (Exception e) {
                    e.printStackTrace();
            }
        }        
    }

    private static void populateEmbeddingStore(InMemoryEmbeddingStore<TextSegment> store, EmbeddingModel embeddingModel) {
        // Sample documents with metadata
        String[] texts = {
                "Machine learning algorithms are revolutionizing data science and artificial intelligence applications across industries.",
                "Climate change impacts are accelerating worldwide, requiring immediate action on renewable energy and carbon reduction strategies.",
                "Blockchain technology enables decentralized finance and cryptocurrency applications with enhanced security and transparency.",
                "Neural networks and deep learning models are advancing artificial intelligence capabilities in computer vision and natural language processing.",
                "Sustainable business practices and corporate responsibility are becoming essential for long-term success in modern markets.",
                "Artificial intelligence ethics and responsible AI development are crucial considerations for technology companies and researchers."
        };

        String[] titles = {
                "Introduction to Machine Learning",
                "Climate Change and Renewable Energy",
                "Blockchain and DeFi Applications",
                "Deep Learning and Neural Networks",
                "Sustainable Business Strategies", 
                "AI Ethics and Responsible Development"
        };

        String[] authors = {"John Doe", 
                "Alice Smith", 
                "Bob Johnson", 
                "John Doe", 
                "Carol Williams", 
                "Alice Smith"
        };

        String[] categories = {
                "TECHNOLOGY", 
                "SCIENCE", 
                "TECHNOLOGY", 
                "TECHNOLOGY", 
                "BUSINESS", 
                "TECHNOLOGY"
        };

        int[] ratings = {
                5, 
                4, 
                4, 
                5, 
                3, 
                4
        };

        String[] statuses = {
                "PUBLISHED", 
                "PUBLISHED", 
                "PUBLISHED", 
                "PUBLISHED", 
                "REVIEW", 
                "PUBLISHED"};

        LocalDate[] publishDates = {
                LocalDate.of(2023, 6, 15), 
                LocalDate.of(2023, 8, 20), 
                LocalDate.of(2023, 9, 5),
                LocalDate.of(2023, 10, 12), 
                LocalDate.of(2023, 7, 8), 
                LocalDate.of(2023, 11, 3)
        };
        int[] wordCounts = {
                1250, 
                2100, 
                1800, 
                1950, 
                1600, 
                2200
        };

        // Create text segments with proper metadata
        for (int i = 0; i < texts.length; i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", titles[i]);
            metadata.put("author", authors[i]);
            metadata.put("category", categories[i]);
            metadata.put("publishDate", publishDates[i].toString());
            metadata.put("rating", ratings[i]);
            metadata.put("status", statuses[i]);
            metadata.put("isPublished", statuses[i].equals("PUBLISHED") ? "true" : "false");
            metadata.put("wordCount", wordCounts[i]);

            // Create TextSegment with actual metadata (need to check the correct API)
            TextSegment segment = createTextSegmentWithMetadata(texts[i], metadata);
            store.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static TextSegment createTextSegmentWithMetadata(String text, Map<String, Object> metadata) {
        // Create Metadata object from the map and use the proper TextSegment.from(text, metadata) API
        Metadata langchainMetadata = new Metadata(metadata);
        return TextSegment.from(text, langchainMetadata);
    }


    public enum DocumentStatus {
        DRAFT, REVIEW, PUBLISHED, ARCHIVED
    }

    public enum DocumentCategory {
        TECHNOLOGY, SCIENCE, BUSINESS, HEALTH, EDUCATION
    }
}