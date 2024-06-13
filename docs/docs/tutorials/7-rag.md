---
id: rag
sidebar_position: 8
---

# RAG (Retrieval-Augmented Generation)

LLM's knowledge is limited to the data it has been trained on.
If you want to make an LLM aware of domain-specific knowledge or proprietary data, you can:
- Use RAG, which we will cover in this section
- Fine-tune the LLM with your data
- [Combine both RAG and fine-tuning](https://gorilla.cs.berkeley.edu/blogs/9_raft.html)


## What is RAG?
Simply put, RAG is the way to find and inject relevant pieces of information from your data
into the prompt before sending it to the LLM.
This way LLM will get (hopefully) relevant information and will be able to reply using this information,
which should reduce the probability of hallucinations.

Relevant pieces of information can be found using various
[information retrieval](https://en.wikipedia.org/wiki/Information_retrieval) methods.
The most popular are:
- Full-text (keyword) search. This method uses techniques like TF-IDF and BM25
to search documents by matching the keywords in a query (e.g., what the user is asking)
against a database of documents.
It ranks results based on the frequency and relevance of these keywords in each document.
- Vector search, also known as "semantic search".
Text documents are converted into vectors of numbers using embedding models.
It then finds and ranks documents based on the cosine similarity
or other similarity/distance measures between the query vector and document vectors,
thus capturing deeper semantic meanings.
- Hybrid. Combining multiple search methods (e.g., full-text + vector) usually improves the effectiveness of the search.

Currently, this page focuses mostly on vector search.
Full-text and hybrid search are currently supported only by Azure AI Search integration,
see `AzureAiSearchContentRetriever` for more details.
We plan to expand the RAG toolbox to include full-text and hybrid search in the near future.


## RAG Stages
THe RAG process is divided into 2 distinct stages: indexing and retrieval.
LangChain4j provides tools for both stages.

### Indexing

During the indexing stage, documents are pre-processed in a way that enables efficient search during the retrieval stage.

This process can vary depending on the information retrieval method used.
For vector search, this typically involves cleaning the documents, enriching them with additional data and metadata,
splitting them into smaller segments (aka chunking), embedding these segments, and finally storing them in an embedding store (aka vector database).

The indexing stage usually occurs offline, meaning it does not require end users to wait for its completion.
This can be achieved through, for example, a cron job that re-indexes internal company documentation once a week during the weekend.
The code responsible for indexing can also be a separate application that only handles indexing tasks.

However, in some scenarios, end users may want to upload their custom documents to make them accessible to the LLM.
In this case, indexing should be performed online and be a part of the main application.

Here is a simplified diagram of the indexing stage:
[![](/img/rag-ingestion.png)](/tutorials/rag)


### Retrieval

The retrieval stage usually occurs online, when a user submits a question that should be answered using the indexed documents.

This process can vary depending on the information retrieval method used.
For vector search, this typically involves embedding the user's query (question)
and performing a similarity search in the embedding store.
Relevant segments (pieces of the original documents) are then injected into the prompt and sent to the LLM.

Here is a simplified diagram of the retrieval stage:
[![](/img/rag-retrieval.png)](/tutorials/rag)


## Easy RAG
LangChain4j has an "Easy RAG" feature that makes it as easy as possible to get started with RAG.
You don't have to learn about embeddings, choose a vector store, find the right embedding model,
figure out how to parse and split documents, etc.
Just point to your document(s), and LangChain4j will do its magic.

If you need a customizable RAG, skip to the [next section](/tutorials/rag#rag-apis).

If you are using Quarkus, there is an even easier way to do Easy RAG.
Please read [Quarkus documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/easy-rag.html).

:::note
The quality of such "Easy RAG" will, of course, be lower than that of a tailored RAG setup.
However, this is the easiest way to start learning about RAG and/or make a proof of concept.
Later, you will be able to transition smoothly from Easy RAG to more advanced RAG,
adjusting and customizing more and more aspects.
:::

1. Import the `langchain4j-easy-rag` dependency:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-easy-rag</artifactId>
    <version>0.31.0</version>
</dependency>
```

2. Let's load your documents:
```java
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j/documentation");
```
This will load all files from the specified directory.

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
List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("/home/langchain4j/documentation");
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

3. Now, we need to preprocess and store documents in a specialized embedding store, also known as vector database.
This is necessary to quickly find relevant pieces of information when a user asks a question.
We can use any of our 15+ [supported embedding stores](/integrations/embedding-stores),
but for simplicity, we will use an in-memory one:
```java
InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
EmbeddingStoreIngestor.ingest(documents, embeddingStore);
```

<details>
<summary>What is happening under the hood?</summary>

1. The `EmbeddingStoreIngestor` loads a `DocumentSplitter` from the `langchain4j-easy-rag` dependency through SPI.
Each `Document` is split into smaller pieces (`TextSegment`s) each consisting of no more than 300 tokens
and with a 30-token overlap.

2. The `EmbeddingStoreIngestor` loads an `EmbeddingModel` from the `langchain4j-easy-rag` dependency through SPI.
Each `TextSegment` is converted into an `Embedding` using the `EmbeddingModel`.

:::note
We have chosen [bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5) as the default embedding model for Easy RAG.
It has achieved an impressive score on the [MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard),
and its quantized version occupies only 24 megabytes of space.
Therefore, we can easily load it into memory and run it in the same process using [ONNX Runtime](https://onnxruntime.ai/).

Yes, that's right, you can convert text into embeddings entirely offline, without any external services,
in the same JVM process.
LangChain4j offers 5 popular embedding models
[out-of-the-box](https://github.com/langchain4j/langchain4j-embeddings).
:::

3. All `TextSegment`-`Embedding` pairs are stored in the `EmbeddingStore`.
</details>

4. The last step is to create an [AI Service](/tutorials/ai-services) that will serve as our API to the LLM:
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
Here, we configure the `Assistant` to use an OpenAI LLM to answer user questions,
remember the 10 latest messages in the conversation,
and retrieve relevant content from an `EmbeddingStore` that contains our documents.

5. And now we are ready to chat with it!
```java
String answer = assistant.chat("How to do Easy RAG with LangChain4j?");
```

## Accessing Sources
If you wish to access the sources (retrieved `Content`s used to augment the message),
you can easily do so by wrapping the return type in the `Result` class:
```java
interface Assistant {

    Result<String> chat(String userMessage);
}

Result<String> result = assistant.chat("How to do Easy RAG with LangChain4j?");

String answer = result.content();
List<Content> sources = result.sources();
```

## RAG APIs
LangChain4j offers a rich set of APIs to make it easy for you to build custom RAG pipelines,
ranging from simple ones to advanced ones.
In this section, we will cover the main domain classes and APIs.


### Document
A `Document` class represents an entire document, such as a single PDF file or a web page.
At the moment, the `Document` can only represent textual information,
but future updates will enable it to support images and tables as well.

<details>
<summary>Useful methods</summary>

- `Document.text()` returns the text of the `Document`
- `Document.metadata()` returns the `Metadata` of the `Document` (see below)
- `Document.toTextSegment()` converts the `Document` into a `TextSegment` (see below)
- `Document.from(String, Metadata)` creates a `Document` from text and `Metadata`
- `Document.from(String)` creates a `Document` from text with empty `Metadata`
</details>

### Metadata
Each `Document` contains `Metadata`.
It stores meta information about the `Document`, such as its name, source, last update date, owner,
or any other relevant details.

The `Metadata` is stored as a key-value map, where the key is of the `String` type,
and the value can be one of the following types: `String`, `Integer`, `Long`, `Float`, `Double`.

`Metadata` is useful for several reasons:
- When including the content of the `Document` in a prompt to the LLM,
metadata entries can also be included, providing the LLM with additional information to consider.
For example, providing the `Document` name and source can help improve the LLM's understanding of the content.
- When searching for relevant content to include in the prompt,
one can filter by `Metadata` entries.
For example, you can narrow down a semantic search to only `Document`s
belonging to a specific owner.
- When the source of the `Document` is updated (for example, a specific page of documentation),
one can easily locate the corresponding `Document` by its metadata entry (for example, "id", "source", etc.)
and update it in the `EmbeddingStore` as well to keep it in sync.

<details>
<summary>Useful methods</summary>

- `Metadata.from(Map)` creates `Metadata` from a `Map`
- `Metadata.put(String key, String value)` / `put(String, int)` / etc., adds an entry to the `Metadata`
- `Metadata.getString(String key)` / `getInteger(String key)` / etc., returns a value of the `Metadata` entry, casting it to the required type
- `Metadata.containsKey(String key)` checks whether `Metadata` contains an entry with the specified key
- `Metadata.remove(String key)` removes an entry from the `Metadata` by key
- `Metadata.copy()` returns a copy of the `Metadata`
- `Metadata.toMap()` converts `Metadata` into a `Map`
</details>

### Document Loader
You can create a `Document` from a `String`, but a simpler method is to use one of our document loaders included in the library:
- `FileSystemDocumentLoader` from the `langchain4j` module
- `UrlDocumentLoader` from the `langchain4j` module
- `AmazonS3DocumentLoader` from the `langchain4j-document-loader-amazon-s3` module
- `AzureBlobStorageDocumentLoader` from the `langchain4j-document-loader-azure-storage-blob` module
- `GitHubDocumentLoader` from the `langchain4j-document-loader-github` module
- `TencentCosDocumentLoader` from the `langchain4j-document-loader-tencent-cos` module


### Document Parser
`Document`s can represent files in various formats, such as PDF, DOC, TXT, etc.
To parse each of these formats, there's a `DocumentParser` interface with several implementations included in the library:
- `TextDocumentParser` from the `langchain4j` module, which can parse files in plain text format (e.g. TXT, HTML, MD, etc.)
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
`DocumentTransformer` implementations can perform a variety of document transformations such as:
- Cleaning: This involves removing unnecessary noise from the `Document`'s text, which can save tokens and reduce distractions.
- Filtering: to completely exclude particular `Document`s from the search.
- Enriching: Additional information can be added to `Document`s to potentially enhance search results.
- Summarizing: The `Document` can be summarized, and its short summary can be stored in the `Metadata`
to be later included in each `TextSegment` (which we will cover below) to potentially improve the search.
- Etc.

`Metadata` entries can also be added, modified, or removed at this stage.

Currently, the only implementation provided out-of-the-box is `HtmlTextExtractor` in the `langchain4j` module,
which can extract desired text content and metadata entries from the raw HTML.

Since there is no one-size-fits-all solution, we recommend implementing your own `DocumentTransformer`,
tailored to your unique data.


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
- Irrelevant information in the prompt might distract the LLM and increase the chance of hallucinations
- The more information you provide in the prompt, the harder it is to explain based on which information the LLM responded

We can address these concerns by splitting a knowledge base into smaller, more digestible segments.
How big should those segments be? That is a good question. As always, it depends.

There are currently 2 widely used approaches:
1. Each document (e.g., a PDF file, a web page, etc.) is atomic and indivisible.
During retrieval in the RAG pipeline, the N most relevant documents are retrieved and injected into the prompt.
You will most probably need to use a long-context LLM in this case since documents can be quite long.
This approach is suitable if retrieving complete documents is important,
such as when you can't afford to miss some details.
- Pros: No context is lost.
- Cons:
  - More tokens are consumed.
  - Sometimes, documents can contain multiple sections/topics, and not all of them are relevant to the query.
  - Vector search quality suffers because complete documents of various sizes are compressed into a single, fixed-length vector.

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

<details>
<summary>Useful methods</summary>

- `TextSegment.text()` returns the text of the `TextSegment`
- `TextSegment.metadata()` returns the `Metadata` of the `TextSegment`
- `TextSegment.from(String, Metadata)` creates a `TextSegment` from text and `Metadata`
- `TextSegment.from(String)` creates a `TextSegment` from text with empty `Metadata`
</details>

### Document Splitter
LangChain4j has a `DocumentSplitter` interface with several out-of-the-box implementations:
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
2. You call the `split(Document)` or `splitAll(List<Document>)` methods of the `DocumentSplitter`.
3. The `DocumentSplitter` splits the given `Document`s into smaller units,
the nature of which varies with the splitter. For instance, `DocumentByParagraphSplitter` divides
a document into paragraphs (defined by two or more consecutive newline characters),
while `DocumentBySentenceSplitter` uses the OpenNLP library's sentence detector to split
a document into sentences, and so on.
4. The `DocumentSplitter` then combines these smaller units (paragraphs, sentences, words, etc.) into `TextSegment`s,
attempting to include as many units as possible in a single `TextSegment` without exceeding the limit set in step 1.
If some of the units are still too large to fit into a `TextSegment`, it calls a sub-splitter.
This is another `DocumentSplitter` capable of splitting units that do not fit into more granular units.
All `Metadata` entries are copied from the `Document` to each `TextSegment`.
A unique metadata entry "index" is added to each text segment.
The first `TextSegment` will contain `index=0`, the second `index=1`, and so on.


### Text Segment Transformer
`TextSegmentTransformer` is similar to `DocumentTransformer` (described above), but it transforms `TextSegment`s.

As with the `DocumentTransformer`, there is no one-size-fits-all solution,
so we recommend implementing your own `TextSegmentTransformer`, tailored to your unique data.

One technique that works quite well for improving retrieval is to include the `Document` title or a short summary
in each `TextSegment`.


### Embedding
The `Embedding` class encapsulates a numerical vector that represents the "semantic meaning"
of the content that has been embedded (usually text, such as a `TextSegment`).

Read more about vector embeddings here:
- https://www.elastic.co/what-is/vector-embedding
- https://www.pinecone.io/learn/vector-embeddings/
- https://cloud.google.com/blog/topics/developers-practitioners/meet-ais-multitool-vector-embeddings

<details>
<summary>Useful methods</summary>

- `Embedding.dimension()` returns the dimension of the embedding vector (its length)
- `CosineSimilarity.between(Embedding, Embedding)` calculates the cosine similarity between 2 `Embedding`s
- `Embedding.normalize()` normalizes the embedding vector (in place)
</details>


### Embedding Model
The `EmbeddingModel` interface represents a special type of model that converts text into an `Embedding`.

Currently supported embedding models can be found [here](/category/embedding-models).

<details>
<summary>Useful methods</summary>

- `EmbeddingModel.embed(String)` embeds the given text
- `EmbeddingModel.embed(TextSegment)` embeds the given `TextSegment`
- `EmbeddingModel.embedAll(List<TextSegment>)` embeds all the given `TextSegment`
</details>


### Embedding Store
The `EmbeddingStore` interface represents a store for `Embedding`s, also known as vector database.
It allows for the storage and efficient search of similar (close in the embedding space) `Embedding`s.

Currently supported embedding stores can be found [here](/category/embedding-stores).

`EmbeddingStore` can store `Embedding`s alone or together with the corresponding `TextSegment`:
- It can store only `Embedding`, by ID. Original embedded data can be stored elsewhere and correlated using the ID.
- It can store both `Embedding` and the original data that has been embedded (usually `TextSegment`).

<details>
<summary>Useful methods</summary>

- `EmbeddingStore.add(Embedding)` adds a given `Embedding` to the store and returns a random ID
- `EmbeddingStore.add(String id, Embedding)` adds a given `Embedding` with a specified ID to the store
- `EmbeddingStore.add(Embedding, TextSegment)` adds a given `Embedding` with an associated `TextSegment` to the store and returns a random ID
- `EmbeddingStore.addAll(List<Embedding>)` adds a list of given `Embedding`s to the store and returns a list of random IDs
- `EmbeddingStore.addAll(List<Embedding>, List<TextSegment>)` adds a list of given `Embedding`s with associated `TextSegment`s to the store and returns a list of random IDs
- `EmbeddingStore.search(EmbeddingSearchRequest)` searches for the most similar `Embedding`s
- `EmbeddingStore.remove(String id)` removes a single `Embedding` from the store by ID
- `EmbeddingStore.removeAll(Collection<String> ids)` removes multiple `Embedding`s from the store by ID
- `EmbeddingStore.removeAll(Filter)` removes all `Embedding`s that match the specified `Filter` from the store
- `EmbeddingStore.removeAll()` removes all `Embedding`s from the store
</details>


####  EmbeddingSearchRequest
The `EmbeddingSearchRequest` represents a request to search in an `EmbeddingStore`.
It has the following attributes:
- `Embedding queryEmbedding`: The embedding used as a reference.
- `int maxResults`: The maximum number of results to return. This is an optional parameter. Default: 3.
- `double minScore`: The minimum score, ranging from 0 to 1 (inclusive). Only embeddings with a score >= `minScore` will be returned. This is an optional parameter. Default: 0.
- `Filter filter`: The filter to be applied to the `Metadata` during search. Only `TextSegment`s whose `Metadata` matches the `Filter` will be returned.

#### Filter
More details about `Filter` can be found [here](https://github.com/langchain4j/langchain4j/pull/610).


#### EmbeddingSearchResult
The EmbeddingSearchResult represents a result of a search in an `EmbeddingStore`.
It contains the list of `EmbeddingMatch`es.


#### Embedding Match
The `EmbeddingMatch` represents a matched `Embedding` along with its relevance score, ID, and original embedded data (usually `TextSegment`).


### Embedding Store Ingestor
The `EmbeddingStoreIngestor` represents an ingestion pipeline and is responsible for 
ingesting `Document`s into an `EmbeddingStore`.

In the simplest configuration, `EmbeddingStoreIngestor` embeds provided `Document`s
using a specified `EmbeddingModel` and stores them, along with their `Embedding`s in a specified `EmbeddingStore`:

```java
EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
        .embeddingModel(embeddingModel)
        .embeddingStore(embeddingStore)
        .build();

ingestor.ingest(document1);
ingestor.ingest(document2, document3);
ingestor.ingest(List.of(document4, document5, document6));
```

Optionally, the `EmbeddingStoreIngestor` can transform `Document`s using a specified `DocumentTransformer`.
This can be useful if you want to clean, enrich, or format `Document`s before embedding them.

Optionally, the `EmbeddingStoreIngestor` can split `Document`s into `TextSegment`s using a specified `DocumentSplitter`.
This can be useful if `Document`s are big, and you want to split them into smaller `TextSegment`s to improve the quality
of similarity searches and reduce the size and cost of a prompt sent to the LLM.

Optionally, the `EmbeddingStoreIngestor` can transform `TextSegment`s using a specified `TextSegmentTransformer`.
This can be useful if you want to clean, enrich, or format `TextSegment`s before embedding them.

An example:
```java
EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()

    // adding userId metadata entry to each Document to be able to filter by it later
    .documentTransformer(document -> {
        document.metadata().put("userId", "12345");
        return document;
    })

    // splitting each Document into TextSegments of 1000 tokens each, with a 200-token overlap
    .documentSplitter(DocumentSplitters.recursive(1000, 200, new OpenAiTokenizer()))

    // adding a name of the Document to each TextSegment to improve the quality of search
    .textSegmentTransformer(textSegment -> TextSegment.from(
            textSegment.metadata("file_name") + "\n" + textSegment.text(),
            textSegment.metadata()
    ))

    .embeddingModel(embeddingModel)
    .embeddingStore(embeddingStore)
    .build();
```


## Advanced RAG
More details are coming soon.
In the meantime, please read [this](https://github.com/langchain4j/langchain4j/pull/538).

[![](/img/advanced-rag.png)](/tutorials/rag)


### Retrieval Augmentor
More details are coming soon.


### Default Retrieval Augmentor
More details are coming soon.


### Query Transformer
More details are coming soon.


### Query Router
More details are coming soon.


### Content Retriever
More details are coming soon.


### Content Aggregator
More details are coming soon.


### Content Injector
More details are coming soon.


## Examples

- [Easy RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_1_easy/Easy_RAG_Example.java)
- [Naive RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_2_naive/Naive_RAG_Example.java)
- [Advanced RAG with Query Compression](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_01_Advanced_RAG_with_Query_Compression_Example.java)
- [Advanced RAG with Query Routing](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_02_Advanced_RAG_with_Query_Routing_Example.java)
- [Advanced RAG with Re-Ranking](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_03_Advanced_RAG_with_ReRanking_Example.java)
- [Advanced RAG with Including Metadata](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_04_Advanced_RAG_with_Metadata_Example.java)
- [Advanced RAG with multiple Retrievers](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_07_Advanced_RAG_Multiple_Retrievers_Example.java)
- [Skipping Retrieval](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_06_Advanced_RAG_Skip_Retrieval_Example.java)
- [RAG + Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/test/java/dev/langchain4j/example/CustomerSupportAgentApplicationTest.java)
- [Loading Documents](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java)
- [ConversationalRetrievalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)
