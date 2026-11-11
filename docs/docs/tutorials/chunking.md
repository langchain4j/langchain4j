---
sidebar_position: 15
---

# Document Chunking

Chunking, also known as document splitting, is the process of breaking down large documents into smaller,
more manageable pieces called segments or chunks. This is a critical step in building effective RAG
(Retrieval-Augmented Generation) systems.

## Dependencies

To use document chunking features, add the following dependency to your project:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>${version.langchain4j}</version>
</dependency>
```

This dependency includes all document splitters mentioned in this tutorial, including:
- `DocumentSplitters` factory methods (recursive, etc.)
- Individual splitter implementations
- `MarkdownSectionSplitter` for markdown documents

:::info
For token-based splitting (recommended), you'll also need a tokenizer dependency such as `langchain4j-open-ai` for OpenAI's tokenizer. 
Alternatively, you can use character-based splitting which requires no additional dependencies.
:::

## Core Concepts

Before diving into the available splitters, it's important to understand a few key concepts:

### Segment Size

The maximum size of each chunk, which can be defined in:
- **Characters**: Simple character count (default)
- **Tokens**: More accurate for LLM processing, requires a `TokenCountEstimator`

Token-based sizing is generally preferred as it aligns with how LLMs process text.

### Overlap

Overlap is the amount of text that appears in consecutive segments. Overlap helps maintain context at segment 
boundaries and prevents information from being split awkwardly. For example, with overlap, the end of one segment
will appear at the beginning of the next segment.

Overlap can also be defined in characters or tokens.

### Hierarchical Splitting

Many splitters support hierarchical (recursive) splitting, where a document is first split at larger boundaries 
(e.g., paragraphs), and if a piece is still too large, 
it's recursively split at smaller boundaries (e.g., sentences, then words, then characters).

## Available Document Splitters

LangChain4j provides several document splitters, each optimized for different use cases.

### Recommended: Recursive Document Splitter

The `DocumentSplitters.recursive()` method creates a hierarchical splitter that is recommended for generic text. 
It intelligently splits documents by trying larger units first and falling back to smaller units when necessary.

**How it works:**
1. First, tries to split by paragraphs (`DocumentByParagraphSplitter`)
2. If paragraphs are too large, splits by lines (`DocumentByLineSplitter`)
3. If lines are too large, splits by sentences (`DocumentBySentenceSplitter`)
4. If sentences are too large, splits by words (`DocumentByWordSplitter`)
5. As a last resort, splits by characters (`DocumentByCharacterSplitter`)

**Example with token-based sizing:**
```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;

Document document = Document.from("Your long document text here...");

// Token-based splitting (recommended)
DocumentSplitter splitter = DocumentSplitters.recursive(
    300,  // max segment size in tokens
    30,   // max overlap size in tokens
    new OpenAiTokenizer()
);

List<TextSegment> segments = splitter.split(document);
```

**Example with character-based sizing:**
```java
// Character-based splitting (simpler, but less precise)
DocumentSplitter splitter = DocumentSplitters.recursive(
    1000,  // max segment size in characters
    100    // max overlap size in characters
);

List<TextSegment> segments = splitter.split(document);
```

### Document By Regex Splitter

`DocumentByRegexSplitter` splits documents using a custom regular expression pattern.

**Example:**
```java
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;

// Split by custom delimiter
DocumentSplitter splitter = new DocumentByRegexSplitter(
    "---",        // regex pattern to split by
    "\n---\n",    // delimiter to join parts with
    500,          // max segment size in tokens
    50,           // max overlap size in tokens
    tokenCountEstimator
);
```

**When to use:** Useful for documents with custom delimiters or structure.

### Markdown Section Splitter

`MarkdownSectionSplitter` is a specialized splitter for Markdown documents that splits by section headers while 
preserving the document structure and hierarchy.

**Key features:**
- Splits documents by heading levels (H1, H2, H3, etc.)
- Maintains parent-child relationships between sections
- Adds metadata about section headers, levels, and hierarchy
- Optionally applies additional splitting to each section
- Handles YAML front matter

**Example - basic usage:**
```java
import dev.langchain4j.data.document.splitter.MarkdownSectionSplitter;

DocumentSplitter splitter = MarkdownSectionSplitter.builder()
    .build();

List<TextSegment> segments = splitter.split(document);
```

**Metadata added to segments:**
Each segment gets the following metadata:
- `md_section_level`: The heading level (0-based: H1=0, H2=1, etc.)
- `md_section_header`: The text of the section header
- `md_section_index_in_parent`: The index of this section within its parent
- `md_parent_header`: The text of the parent section header (if any)

**Example - accessing metadata:**
```java
for (TextSegment segment : segments) {
    Integer level = segment.metadata().getInteger("md_section_level");
    String header = segment.metadata().getString("md_section_header");
    String parentHeader = segment.metadata().getString("md_parent_header");

    System.out.println("Section: " + header + " (level " + level + ")");
    if (parentHeader != null) {
        System.out.println("  Parent: " + parentHeader);
    }
}
```

**When to use:** Ideal for markdown documentation, wiki pages, blog posts, or any markdown-formatted content where you want to maintain section boundaries and hierarchy. The metadata makes it easier to provide context to the LLM about where the retrieved information comes from.

## Complete Example

Here's a complete example demonstrating document chunking in a RAG pipeline:

```java
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import java.nio.file.Path;
import java.util.List;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class ChunkingExample {

    public static void main(String[] args) {

        // 1. Load documents
        Document document = FileSystemDocumentLoader.loadDocument(Path.of("document.txt"));

        // 2. Create a splitter with appropriate settings
        DocumentSplitter splitter = DocumentSplitters.recursive(
                300,  // max segment size in tokens
                30,   // overlap in tokens
                new OpenAiTokenCountEstimator(GPT_4_O_MINI)
        );

        // 3. Split the document
        List<TextSegment> segments = splitter.split(document);

        System.out.println("Document split into " + segments.size() + " segments");

        // 4. Create embeddings for each segment
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 5. Store segments and embeddings
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);

        System.out.println("Segments embedded and stored successfully!");
    }
}    
```

## Examples

An example demonstrating document splitting can be found in the [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) repository:

- [RAG with document splitting](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_4_low_level/_01_Low_Level_Naive_RAG_Example.java)

## Related Tutorials

- [RAG (Retrieval-Augmented Generation)](/tutorials/rag)
- [AI Services](/tutorials/ai-services)
- [Embedding Stores](/tutorials/embedding-stores)
