---
sidebar_position: 8
---

# RAG (Retrieval-Augmented Generation)

[Great tutorial on RAG](https://www.sivalabs.in/langchain4j-retrieval-augmented-generation-tutorial/)
by [Siva](https://www.sivalabs.in/).

LLM's knowledge is limited to the data it has been trained on.
To enable LLM to "know" your private data, like internal company documentation, you can:
- Use RAG, which we will cover here
- Fine-tune LLM with your data
- [Combine RAG and fine-tuning](https://gorilla.cs.berkeley.edu/blogs/9_raft.html)

Simply put, RAG is the way to find and inject relevant pieces of information from your private knowledge base
into the prompt before sending it to the LLM.
This way LLM will get (hopefully) relevant information and will be able to reply using this information.

## Easy RAG
LangChain4j has an "Easy RAG" feature that makes it as easy as possible to get started with RAG.
You don't need to learn about embeddings, choose a vector store, find the right embedding model,
figure out how to parse and split documents, etc.
Just point to your document(s), and LangChain4j will work its magic.

:::note
The quality of such "Easy RAG" will, of course, by definition, be lower than that of a tailored solution.
However, this is the easiest way to start learning about RAG and/or make a proof of concept.
Later, you will be able to transition smoothly from Easy RAG to more advanced RAG,
adjusting and customizing more aspects.
:::

First, import the `langchain4j-easy-rag` dependency, which contains everything we need inside:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-easy-rag</artifactId>
    <version>0.29.0</version>
</dependency>
```

Then, let's load our documents:
```java
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j/documents");
```
This will load all documents from the specified directory.

<details>
<summary>What is happening under the hood?</summary>

The Apache Tika library, which supports a wide variety of document types,
is used to detect document types and parse them.
Since we did not explicitly specify which `DocumentParser` to use,
the `FileSystemDocumentLoader` will load an `ApacheTikaDocumentParser`,
provided by `langchain4j-easy-rag` dependency through SPI.
</details>

<details>
<summary>How to customize loading documents?</summary>

If you want to load documents from all subdirectories, you can use the `loadDocumentsRecursively` method:
```java
List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("/home/langchain4j/documents");
```
Additionally, you can filter documents by using a glob or regex:
```java
PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.pdf");
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j/documentation", pathMatcher);
```

:::note
When using `loadDocumentsRecursively` method, you might want to use a double asterisk (instead of a single one)
in glob: `glob:**.pdf`.
:::
</details>

Now, once we have loaded our documents into memory,
we need to store them in a specialized embedding (vector) store to enable semantic search.
We can use any of our 15+ [integrations](/category/embedding-stores) with various embedding stores,
but for simplicity, we will use an in-memory one:
```java
InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
```

Now, let's ingest our documents into the store:
```java
EmbeddingStoreIngestor.ingest(documents, embeddingStore);
```

<details>
<summary>What is happening under the hood?</summary>

1. Through SPI, the `EmbeddingStoreIngestor` loads a `DocumentSplitter` from the `langchain4j-easy-rag` dependency.
Each `Document` is split into smaller pieces (`TextSegment`s) each consisting of 300 tokens and with a 30-token overlap.

2. Through SPI, the `EmbeddingStoreIngestor` loads an `EmbeddingModel` from the `langchain4j-easy-rag` dependency.
For the Easy RAG, a [bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5) embedding model is used.
Each `TextSegment` is converted into an `Embedding` using the `EmbeddingModel`.

:::note
This embedding model has achieved an impressive score
on the [MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard), and its quantized version
occupies only 24 megabytes of space.
Therefore, we can easily load it into memory and run it in the same process using [ONNX Runtime](https://onnxruntime.ai/).

Yes, that's right, you can convert text into embeddings entirely offline, without any external services,
in the same JVM process.
LangChain4j offers 5 popular embedding models
[out-of-the-box](https://github.com/langchain4j/langchain4j-embeddings).
:::

3. All `TextSegment`-`Embedding` pairs are stored in the `EmbeddingStore`.
</details>

The last step is to create an [AI Service](/tutorials/ai-services) that will serve as our API to the LLM:
```java
interface Assistant {

    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(OpenAiChatModel.withApiKey(OPENAI_API_KEY))
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
    .build();
```
Here, we configure our AI Service to use an OpenAI LLM, remember the 10 latest messages in the conversation,
and use an `EmbeddingStore` with our documents.

And now we are ready to chat with it!
```java
String answer = assistant.chat("How to do RAG with LangChain4j?");
```

## RAG APIs
LangChain4j offers a broad set of APIs to make it easier for you to build your custom RAG pipelines.

### Document
LangChain4j's domain model includes a `Document` class, which represents an entire document,
such as a single PDF file.
At the moment, the `Document` can only represent textual information,
but future updates will enable it to support images and tables as well.

### Document Loader
You can create a `Document` from a `String`, but a simpler method is to use one of our `DocumentLoader`s included in the library:
- `FileSystemDocumentLoader` from the main (`langchain4j`) module
- `UrlDocumentLoader` from the main (`langchain4j`) module
- `AmazonS3DocumentLoader` from the `langchain4j-document-loader-amazon-s3` module
- `AzureBlobStorageDocumentLoader` from the `langchain4j-document-loader-azure-storage-blob` module
- `GitHubDocumentLoader` from the `langchain4j-document-loader-github` module
- `TencentCosDocumentLoader` from the `langchain4j-document-loader-tencent-cos` module

### Document Parser
`Document`s can represent files in various formats, such as PDF, DOC, TXT, etc.
To parse each of these formats, there's a `DocumentParser` interface with several implementations included in the library:
- `TextDocumentParser` from the main (`langchain4j`) module, which can parse files in plain text format (e.g. TXT, HTML, MD, etc.)
- `ApachePdfBoxDocumentParser` from the `langchain4j-document-parser-apache-pdfbox` module, which can parse PDF files
- `ApachePoiDocumentParser` from the `langchain4j-document-parser-apache-poi` module, which can parse MS Office file formats
(e.g. DOC, DOCX, PPT, PPTX, XLS, XLSX, etc.)
- `ApacheTikaDocumentParser` from the `langchain4j-document-parser-apache-tika` module,
which can automatically detect and parse almost all existing file formats

Here is an example of how to load one or multiple `Document`s from the file system:
```java
// Load a single document
Document document = FileSystemDocumentLoader.loadDocument("/home/langchain4j/file.txt", new TextDocumentParser());

// Load all documents from a directory
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j", new TextDocumentParser());

// Load all *.txt documents from a directory
PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.txt");
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j", pathMatcher, new TextDocumentParser());

// Load all documents from a directory and its subdirectories
List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("/home/langchain4j", new TextDocumentParser());
```

You can also load documents without explicitly specifying a `DocumentParser`.
In this case, a default `DocumentParser` will be used.
The default one is loaded through SPI (e.g. from `langchain4j-document-parser-apache-tika` or `langchain4j-easy-rag`).
If no `DocumentParser`s are found through SPI, a `TextDocumentParser` is used as a fallback.


### Document Transformer
More details are coming soon.

See `HtmlTextExtractor` in the `langchain4j` module.


### Text Segment
Once your `Document`s are loaded, it is time to split (chunk) them into smaller segments (pieces).
LangChain4j's domain model includes a `TextSegment` class that represents a segment of a `Document`.
As the name suggests, `TextSegment` can represent only textual information.

<details>
<summary>To split or not to split?</summary>

There are several reasons why you might want to include only a few relevant segments
instead of the entire knowledge base in the prompt:
- LLMs have a limited context window, so the entire knowledge base might not fit
- The more information you provide in the prompt, the longer it takes for the LLM to process it and respond
- The more information you provide in the prompt, the more you pay
- Irrelevant information in the prompt might confuse or distract the LLM and increase the chance of hallucinations

RAG addresses these concerns by splitting your knowledge base into smaller, more digestible segments.
How big should those segments be? That is a good question. As always, it depends.

There are currently 2 widely used approaches:
1. Each document (e.g., a PDF file, a web page, etc.) is atomic and indivisible.
During retrieval in the RAG pipeline, the N most relevant documents are retrieved and injected into the prompt.
You will most probably need to use a long-context LLM for this since documents can be quite long.
This approach is suitable if processing complete documents is important,
such as when you can't afford to miss some details.
- Pros: No context is lost.
- Cons:
  - More tokens are consumed.
  - Sometimes, documents can contain multiple sections/topics, and not all of them are relevant to the query.
  - Vector search quality suffers because complete documents of various sizes are compressed into a single vector.

2. Documents are split into smaller segments, such as chapters, paragraphs, or sometimes even sentences.
During retrieval in the RAG pipeline, the N most relevant segments are retrieved and injected into the prompt.
The challenge lies in ensuring each segment provides sufficient context/information for the LLM to understand it.
Missing context can lead to the LLM misinterpreting the given segment and hallucinating.
A common strategy is to split documents into segments with overlap, but this doesn't completely solve the problem.
Several advanced techniques can help here, for example, "sentence window retrieval", "auto-merging retrieval",
and "parent document retrieval".
We won't go into details here, but essentially, these methods help to fetch more context around the retrieved segments,
providing the LLM with additional information before and after the retrieved segment.
- Pros:
  - Better quality of vector search.
  - Reduced token consumption.
- Cons: Some context may still be lost.

</details>

### Document Splitter
LangChain4j has a DocumentSplitter interface with several out-of-the-box implementations:
- `DocumentByParagraphSplitter`
- `DocumentByLineSplitter`
- `DocumentBySentenceSplitter`
- `DocumentByWordSplitter`
- `DocumentByCharacterSplitter`
- `DocumentByRegexSplitter`
- Recursive: `DocumentSplitters.recursive(...)`

They all work as follows:
1. You instantiate a `DocumentSplitter`, specifying the desired size of `TextSegment`s and,
optionally, an overlap in characters or tokens.
2. You use the `split(Document)` or `splitAll(List<Document>)` methods of the `DocumentSplitter`.
3. The `DocumentSplitter` splits the given `Document`s into smaller units,
the nature of which varies with the splitter. For instance, `DocumentByParagraphSplitter` divides
a document into paragraphs (defined by two or more consecutive newline characters (`\n`)),
while `DocumentBySentenceSplitter` uses the OpenNLP library's sentence detector to split
a document into sentences, and so on.
4. The `DocumentSplitter` then processes these smaller units (paragraphs, sentences, words, etc.),
recombining them into a `TextSegment` and trying to include as many units as possible
into a single `TextSegment`, without exceeding the limit set in step 1.

### Text Segment Transformer
More details are coming soon.

### Embedding
More details are coming soon.

### Embedding Model
More details are coming soon.

Currently supported embedding models can be found [here](/category/embedding-models).

### Embedding Store
More details are coming soon.

Currently supported embedding stores can be found [here](/category/embedding-stores).

### Embedding Store Ingestor
More details are coming soon.

## RAG Stages

### Ingestion 

[![](/img/rag-ingestion.png)](/tutorials/rag)

### Retrieval

[![](/img/rag-retrieval.png)](/tutorials/rag)

## Advanced RAG
More details are coming soon.
In the meantime, please read [this](https://github.com/langchain4j/langchain4j/pull/538).

[![](/img/advanced-rag.png)](/tutorials/rag)

## Examples

- [Easy RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_1_easy/Easy_RAG_Example.java)
- [Naive RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_2_naive/Naive_RAG_Example.java)
- [Advanced RAG with Query Compression](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_01_Advanced_RAG_with_Query_Compression_Example.java)
- [Advanced RAG with Query Routing](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_02_Advanced_RAG_with_Query_Routing_Example.java)
- [Advanced RAG with Re-Ranking](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_03_Advanced_RAG_with_ReRanking_Example.java)
- [Advanced RAG with Including Metadata](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_04_Advanced_RAG_with_Metadata_Example.java)
- [RAG + Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)
- [Loading Documents](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java)
- [ConversationalRetrievalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)
