package dev.langchain4j.chain;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.ParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Result;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.InMemoryEmbeddingStore;
import lombok.Builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class ConversationalRetrievalChain implements Chain<String, String> {

    private static final DocumentSplitter DEFAULT_DOCUMENT_SPLITTER = new ParagraphSplitter();
    private static final EmbeddingStore<DocumentSegment> DEFAULT_EMBEDDING_STORE = new InMemoryEmbeddingStore();
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from("Answer the following question to the best of your ability: {{question}}\n\nBase your answer on the following information:\n{{information}}");

    private final DocumentLoader documentLoader;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<DocumentSegment> embeddingStore;
    private final Integer maxSegmentsToRetrieve;
    private final PromptTemplate promptTemplate;
    private final ChatLanguageModel chatLanguageModel;

    @Builder
    public ConversationalRetrievalChain(DocumentLoader documentLoader,
                                        DocumentSplitter documentSplitter,
                                        EmbeddingModel embeddingModel,
                                        EmbeddingStore<DocumentSegment> embeddingStore,
                                        Integer maxSegmentsToRetrieve,
                                        PromptTemplate promptTemplate,
                                        ChatLanguageModel chatLanguageModel) {
        this.documentLoader = documentLoader;
        this.documentSplitter = documentSplitter == null ? DEFAULT_DOCUMENT_SPLITTER : documentSplitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore == null ? DEFAULT_EMBEDDING_STORE : embeddingStore;
        this.maxSegmentsToRetrieve = maxSegmentsToRetrieve == null ? 5 : maxSegmentsToRetrieve;
        this.promptTemplate = promptTemplate == null ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
        this.chatLanguageModel = chatLanguageModel;

        init();
    }

    private void init() {
        Document document = documentLoader.load();
        List<DocumentSegment> documentSegments = documentSplitter.split(document);
        List<Embedding> embeddings = embeddingModel.embedAll(documentSegments).get();
        embeddingStore.addAll(embeddings, documentSegments);
    }

    @Override
    public String execute(String question) {
        Embedding questionEmbedding = embeddingModel.embed(question).get();

        List<EmbeddingMatch<DocumentSegment>> relevantEmbeddings = embeddingStore.findRelevant(questionEmbedding, maxSegmentsToRetrieve);

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", format(relevantEmbeddings));

        Prompt prompt = promptTemplate.apply(variables);

        Result<AiMessage> result = chatLanguageModel.sendUserMessage(prompt);

        return result.get().text();
    }

    private static String format(List<EmbeddingMatch<DocumentSegment>> relevantEmbeddings) {

        String concatenatedEmbeddings = relevantEmbeddings.stream()
                .map(match -> ofNullable(match.embedded()).map(DocumentSegment::text).orElse(""))
                .filter(it -> !it.isEmpty())
                .map(it -> "..." + it + "...")
                .collect(joining("\n\n"));

        if (concatenatedEmbeddings.isEmpty()) {
            return "";
        }

        return concatenatedEmbeddings;
    }
}
