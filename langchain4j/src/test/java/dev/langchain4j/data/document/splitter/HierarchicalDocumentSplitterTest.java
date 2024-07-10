package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ExampleTestTokenizer;
import dev.langchain4j.model.Tokenizer;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.*;

class HierarchicalDocumentSplitterTest implements WithAssertions {
    public static class ExampleImpl extends HierarchicalDocumentSplitter {
        public ExampleImpl(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
            super(maxSegmentSizeInChars, maxOverlapSizeInChars);
        }

        public ExampleImpl(int maxSegmentSizeInChars, int maxOverlapSizeInChars, HierarchicalDocumentSplitter subSplitter) {
            super(maxSegmentSizeInChars, maxOverlapSizeInChars, subSplitter);
        }

        public ExampleImpl(int maxSegmentSizeInTokens, int maxOverlapSizeInTokens, Tokenizer tokenizer) {
            super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer);
        }

        public ExampleImpl(int maxSegmentSizeInTokens, int maxOverlapSizeInTokens, Tokenizer tokenizer, HierarchicalDocumentSplitter subSplitter) {
            super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, subSplitter);
        }

        @Override
        protected String[] split(String text) {
            return text.split("\\.");
        }

        @Override
        protected String joinDelimiter() {
            return " ";
        }

        @Override
        protected DocumentSplitter defaultSubSplitter() {
            return null;
        }
    }

    @Test
    public void test_constructor() {
        {
            ExampleImpl splitter = new ExampleImpl(1, 1);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenizer).isNull();
            assertThat(splitter.subSplitter).isNull();

            assertThat(splitter.estimateSize("abc def")).isEqualTo(7);
        }
        {
            DocumentByWordSplitter subSplitter = new DocumentByWordSplitter(2, 2);
            ExampleImpl splitter = new ExampleImpl(1, 1, subSplitter);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenizer).isNull();
            assertThat(splitter.subSplitter).isSameAs(subSplitter);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(7);
        }
        {
            Tokenizer tokenizer = new ExampleTestTokenizer();
            ExampleImpl splitter = new ExampleImpl(1, 1, tokenizer);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenizer).isSameAs(tokenizer);
            assertThat(splitter.subSplitter).isNull();

            assertThat(splitter.estimateSize("abc def")).isEqualTo(2);
        }
        {
            DocumentByWordSplitter subSplitter = new DocumentByWordSplitter(2, 2);
            Tokenizer tokenizer = new ExampleTestTokenizer();
            ExampleImpl splitter = new ExampleImpl(1, 1, tokenizer, subSplitter);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenizer).isSameAs(tokenizer);
            assertThat(splitter.subSplitter).isSameAs(subSplitter);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(2);
        }
    }


    private void performStartIndexTest(String inputString, DocumentSplitter splitter) {
        Set<String> uniqueWords = new LinkedHashSet<>(Arrays.asList(inputString.split("\\s+")));
        StringBuilder uniqueText = new StringBuilder();
        Map<String, Integer> wordMap = new HashMap<>();
        for (String word : uniqueWords) {
            wordMap.put(word, uniqueText.length());
            uniqueText.append(word).append(" ");
        }

        String text = uniqueText.toString().trim();

        List<TextSegment> textSegments = splitter.split(Document.from(text));
        for (TextSegment textSegment : textSegments) {
            String firstWord = textSegment.text().split("\\s+")[0];
            Integer startIndex = textSegment.metadata().getInteger("start_index");
            Integer mapIndex = wordMap.get(firstWord);
            assertThat(startIndex).isEqualTo(mapIndex);
        }
    }

    @Test
    public void test_start_indexes() {
        {
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 250);

            String langchain = "LangChain is a framework designed to simplify the creation of applications using large language models (LLMs). As a language model integration framework, LangChain's use-cases largely overlap with those of language models in general, including document analysis and summarization, chatbots, and code analysis.[2] History\n LangChain was launched in October 2022 as an open source project by Harrison Chase, while working at machine learning startup Robust Intelligence. The project quickly garnered popularity,[3] with improvements from hundreds of contributors on GitHub, trending discussions on Twitter, lively activity on the project's Discord server, many YouTube tutorials, and meetups in San Francisco and London. In April 2023, LangChain had incorporated and the new startup raised over $20 million in funding at a valuation of at least $200 million from venture firm Sequoia Capital, a week after announcing a $10 million seed investment from Benchmark.[4][5] In the third quarter of 2023, the LangChain Expression Language (LCEL) was introduced, which provides a declarative way to define chains of actions.[6][7] In October 2023 LangChain introduced LangServe, a deployment tool to host LCEL code as a production-ready API.[8] Capabilities\n LangChain's developers highlight the framework's applicability to use-cases including chatbots,[9] retrieval-augmented generation,[10] document summarization,[11] and synthetic data generation.[12] As of March 2023, LangChain included integrations with systems including Amazon, Google, and Microsoft Azure cloud storage; API wrappers for news, movie information, and weather; Bash for summarization, syntax and semantics checking, and execution of shell scripts; multiple web scraping subsystems and templates; few-shot learning prompt generation support; finding and summarizing \"todo\" tasks in code; Google Drive documents, spreadsheets, and presentations summarization, extraction, and creation; Google Search and Microsoft Bing web search; OpenAI, Anthropic, and Hugging Face language models; iFixit repair guides and wikis search and summarization; MapReduce for question answering, combining documents, and question generation; N-gram overlap scoring; PyPDF, pdfminer, fitz, and pymupdf for PDF file text extraction and manipulation; Python and JavaScript code generation, analysis, and debugging; Milvus vector database[13] to store and retrieve vector embeddings; Weaviate vector database[14] to cache embedding and data objects; Redis cache database storage; Python RequestsWrapper and other methods for API requests; SQL and NoSQL databases including JSON support; Streamlit, including for logging; text mapping for k-nearest neighbors search; time zone conversion and calendar operations; tracing and recording stack symbols in threaded and asynchronous subprocess runs; and the Wolfram Alpha website and SDK.[15] As of April 2023, it can read from more than 50 document types and data sources.[16]";
            String repeated = new String(new char[50]).replace("\0", langchain);

            performStartIndexTest(langchain, splitter);
            performStartIndexTest(repeated, splitter);

        }
    }
}
