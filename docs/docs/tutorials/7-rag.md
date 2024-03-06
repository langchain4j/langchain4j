---
sidebar_position: 8
---

# RAG (Retrieval-Augmented Generation)

[Great tutorial on RAG](https://www.sivalabs.in/langchain4j-retrieval-augmented-generation-tutorial/)
by [Siva](https://www.sivalabs.in/).

LLM's knowledge is limited to the data that it has seen during training.
If you want LLM to have access to your private data, such as internal company documentation or your notes,
you can use RAG.

RAG is widely known as "Chat with your PDF".

Simply put, RAG is the way to find and inject relevant pieces of information from your private knowledge base
into the prompt before sending it to the LLM.
This way LLM will see (hopefully) relevant information and will be able to reply using this information.

There are multiple reasons why you might want to include only a few relevant pieces instead of the whole knowledge base at once:
- LLMs have a limited context window, so the whole knowledge base might not fit
- The more information you provide in the input prompt, the longer it takes for the LLM to process it and respond
- The more information you provide in the input prompt, the more you pay
- Irrelevant information present in the prompt might confuse/distract LLM and increase chance of hallucinations

RAG addresses these concerns by splitting your knowledge base into smaller, more digestible pieces.

## Easy RAG
TODO

## Documents
LangChain4j's domain model has a Document class that represents a whole document, for example, a single PDF file.
Right now Document can represent only textual information, but in the future it will also support images and tables.

## Document Loaders
You can create a Document from a String, but the easier way is to use one of our document loaders included in the library:
- FileSystemDocumentLoader from main (langchain4j) module
- UrlDocumentLoader from main (langchain4j) module
- AmazonS3DocumentLoader from langchain4j-document-loader-amazon-s3 module
- AzureBlobStorageDocumentLoader from langchain4j-document-loader-azure-storage-blob module
- GitHubDocumentLoader from langchain4j-document-loader-github module
- TencentCosDocumentLoader from langchain4j-document-loader-tencent-cos module

## Document Parsers
Documents can represent files of different formats, such as PDF, DOC, TXT, etc.
In order to be able to parse each of the formats, there is a DocumentParser interface with multiple implementations 
included in the library:
- TextDocumentParser from main (langchain4j) module which can parse files in plain text format (e.g. txt, html, md, etc.)
- ApachePdfBoxDocumentParser from langchain4j-document-parser-apache-pdfbox module which can parse pdf files
- ApachePoiDocumentParser from langchain4j-document-parser-apache-poi module which can parse MS Office file formats (doc, docx, ppt, pptx, xls, xlsx)
- ApacheTikaDocumentParser from langchain4j-document-parser-apache-tika module which can automatically detect file format and parse almost all existing ones

Here is an example of how to load one or multiple Documents from the file system:
```java
Document document = FileSystemDocumentLoader.loadDocument("/home/langchain4j/file.txt", new TextDocumentParser());

List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j", new TextDocumentParser());
```

TODO globs, recursive

## Text Segments
Once your Documents are loaded, it is time to split (segment) them into smaller pieces (segments).
LangChain4j's domain model has a TextSegment class that represents a segment of a Document.
As is seen from the name, TextSegment can represent only textual information.

## Document Splitters
LangChain4j has a DocumentSplitter interface with multiple implementations:
- DocumentByParagraphSplitter
- DocumentByLineSplitter
- DocumentBySentenceSplitter
- DocumentByWordSplitter
- DocumentByCharacterSplitter
- DocumentByRegexSplitter
- Recursive: DocumentSplitters.recursive() TODO

All of them work like this:
1. You create an instance of a DocumentSplitter defining desired size of TextSegments and an optional overlap (TODO) in characters or tokens
2. You invoke split(Document) or splitAll(List<Document>) methods of DocumentSplitter
3. DocumentSplitter splits the given Document(s) into smaller units. The unit is different depending on the splitter.
For example, DocumentByParagraphSplitter splits Document into paragraphs (paragraphs are separated by 2 or more consecutive newline characters (\n)).
DocumentBySentenceSplitter splits Document into sentences, using sentence detector from OpenNLP library. And so on.
4. DocumentSplitter iterates through smaller units (paragraphs/sentences/words/etc.) and merges them back together into a TextSegment,
trying to fit as many units as possible into a single TextSegment, but not more than was defined in the step 1.

TODO recursive


## Ingestion

[![](/img/rag-ingestion.png)](/tutorials/rag)

## Retrieval

[![](/img/rag-retrieval.png)](/tutorials/rag)

## Advanced Retrieval

More info on advanced RAG can be found [here](https://github.com/langchain4j/langchain4j/pull/538).

[![](/img/advanced-rag.png)](/tutorials/rag)

## Examples

- [Naive (simple) RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_01_Naive_RAG.java)
- [Advanced RAG: Query Compression](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_02_Advanced_RAG_with_Query_Compression.java)
- [Advanced RAG: Query Routing](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_03_Advanced_RAG_with_Query_Routing.java)
- [Advanced RAG: Re-Ranking](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_04_Advanced_RAG_with_ReRanking.java)
- [Advanced RAG: Including Metadata](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_05_Advanced_RAG_with_Metadata.java)
- [RAG + Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)
- [Loading Documents](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java)
- [ConversationalRetrievalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)
