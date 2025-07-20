package dev.langchain4j.rag.content.retriever.azure.search;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore.DEFAULT_INDEX_NAME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchContentRetrieverIT extends EmbeddingStoreWithFilteringIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetrieverIT.class);

    private final EmbeddingModel embeddingModel;

    private final AzureAiSearchContentRetriever contentRetrieverWithVector;

    private AzureAiSearchContentRetriever contentRetrieverWithFullText;

    private final AzureAiSearchContentRetriever contentRetrieverWithHybrid;

    private final AzureAiSearchContentRetriever contentRetrieverWithHybridAndReranking;

    public AzureAiSearchContentRetrieverIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        SearchIndexClient searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .credential(new AzureKeyCredential(System.getenv("AZURE_SEARCH_KEY")))
                .buildClient();

        searchIndexClient.deleteIndex(DEFAULT_INDEX_NAME);

        contentRetrieverWithVector = createContentRetriever(AzureAiSearchQueryType.VECTOR);
        contentRetrieverWithFullText = createFullTextSearchContentRetriever();
        contentRetrieverWithHybrid = createContentRetriever(AzureAiSearchQueryType.HYBRID);
        contentRetrieverWithHybridAndReranking = createContentRetriever(AzureAiSearchQueryType.HYBRID_WITH_RERANKING);
    }

    private AzureAiSearchContentRetriever createContentRetriever(AzureAiSearchQueryType azureAiSearchQueryType) {
        return AzureAiSearchContentRetriever.builder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .dimensions(embeddingModel.dimension())
                .embeddingModel(embeddingModel)
                .queryType(azureAiSearchQueryType)
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    private AzureAiSearchContentRetriever createFullTextSearchContentRetriever() {
        return AzureAiSearchContentRetriever.builder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .embeddingModel(null)
                .queryType(AzureAiSearchQueryType.FULL_TEXT)
                .createOrUpdateIndex(false)
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_AI_SEARCH");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

    @Test
    void addEmbeddingsAndRetrieveRelevant() {
        String content1 = "banana";
        String content2 = "computer";
        String content3 = "apple";
        String content4 = "pizza";
        String content5 = "strawberry";
        String content6 = "chess";
        List<String> contents = asList(content1, content2, content3, content4, content5, content6);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithVector.add(embedding, textSegment);
        }

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(contents.size()));

        String content = "fruit";
        Query query = Query.from(content);

        List<Content> relevant = contentRetrieverWithVector.retrieve(query);
        assertThat(relevant).hasSize(3);
        assertContent(relevant.get(0));
        assertThat(relevant.get(0).textSegment().text()).isIn(content1, content3, content5);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertContent(relevant.get(1));
        assertThat(relevant.get(1).textSegment().text()).isIn(content1, content3, content5);
        log.info("#2 relevant item: {}", relevant.get(1).textSegment().text());
        assertContent(relevant.get(2));
        assertThat(relevant.get(2).textSegment().text()).isIn(content1, content3, content5);
        log.info("#3 relevant item: {}", relevant.get(2).textSegment().text());
    }

    @Test
    @Disabled("no semantic ranker in a free Azure tier")
    void allTypesOfSearch() {
        String content1 = "This book is about politics";
        String content2 = "Cats sleeps a lot.";
        String content3 = "Sandwiches taste good.";
        String content4 = "The house is open";
        List<String> contents = asList(content1, content2, content3, content4);

        for (int index = 0; index < contents.size(); index++) {
            Map<String, String> meta = new HashMap<>();
            meta.put("id", "content" + index);
            String content = contents.get(index);
            TextSegment textSegment = new TextSegment(content, new Metadata(meta));
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithVector.add(embedding, textSegment);
        }

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(contents.size()));

        String content = "house";
        Query query = Query.from(content);

        log.info("Testing Vector Search");
        List<Content> relevant = contentRetrieverWithVector.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        assertContent(relevant.get(0));
        assertThat(relevant.get(0).textSegment().text()).isEqualTo("The house is open");
        assertThat(relevant.get(0).textSegment().metadata().getString("id")).isEqualTo("content3");

        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());

        log.info("Testing Full Text Search");
        // This uses the same storage as the vector search, so we don't need to add the content again
        List<Content> relevant2 = contentRetrieverWithFullText.retrieve(query);
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertContent(relevant2.get(0));
        assertThat(relevant2.get(0).textSegment().text()).isEqualTo("The house is open");
        assertThat(relevant2.get(0).textSegment().metadata().getString("id")).isEqualTo("content3");
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());

        log.info("Testing Hybrid Search");
        List<Content> relevant3 = contentRetrieverWithHybrid.retrieve(query);
        assertThat(relevant3).hasSizeGreaterThan(0);
        assertContent(relevant3.get(0));
        assertThat(relevant3.get(0).textSegment().text()).isEqualTo("The house is open");
        assertThat(relevant3.get(0).textSegment().metadata().getString("id")).isEqualTo("content3");
        log.info("#1 relevant item: {}", relevant3.get(0).textSegment().text());

        log.info("Testing Hybrid Search with Reranking");
        List<Content> relevant4 = contentRetrieverWithHybridAndReranking.retrieve(query);
        assertThat(relevant4).hasSizeGreaterThan(0);
        assertContent(relevant4.get(0));
        assertThat(relevant4.get(0).textSegment().text()).isEqualTo("The house is open");
        assertThat(relevant4.get(0).textSegment().metadata().getString("id")).isEqualTo("content3");
        log.info("#1 relevant item: {}", relevant4.get(0).textSegment().text());

        log.info("Test complete");
    }

    @Test
    void fullTextSearch() {
        String content1 =
                "Émile-Auguste Chartier (3 March 1868 – 2 June 1951), commonly known as Alain, was a French philosopher, journalist, essayist, pacifist, and teacher of philosophy. He adopted his pseudonym as the most banal he could find. There is no evidence he ever thought in so doing of the 15th century Norman poet Alain Chartier.";
        String content2 =
                "Emmanuel Levinas (12 January 1906 – 25 December 1995) was a French philosopher of Lithuanian Jewish ancestry who is known for his work within Jewish philosophy, existentialism, and phenomenology, focusing on the relationship of ethics to metaphysics and ontology.";
        String content3 =
                "Maurice Jean Jacques Merleau-Ponty (14 March 1908 – 3 May 1961) was a French phenomenological philosopher, strongly influenced by Edmund Husserl and Martin Heidegger. The constitution of meaning in human experience was his main interest and he wrote on perception, art, politics, religion, biology, psychology, psychoanalysis, language, nature, and history. He was the lead editor of Les Temps modernes, the leftist magazine he established with Jean-Paul Sartre and Simone de Beauvoir in 1945.";
        List<String> contents = asList(content1, content2, content3);

        for (String content : contents) {
            contentRetrieverWithFullText.add(content);
        }

        awaitUntilAsserted(() -> assertThat(contentRetrieverWithFullText.retrieve(Query.from("a")))
                .hasSize(contents.size()));

        Query query = Query.from("Alain");
        List<Content> relevant = contentRetrieverWithFullText.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertContent(relevant.get(0));
        assertThat(relevant.get(0).textSegment().text()).contains("Émile-Auguste Chartier");

        Query query2 = Query.from("Heidegger");
        List<Content> relevant2 = contentRetrieverWithFullText.retrieve(query2);
        assertThat(relevant2).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());
        assertContent(relevant2.get(0));
        assertThat(relevant2.get(0).textSegment().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }

    @Test
    void fullTextSearchWithSpecificSearchIndex() {
        // This doesn't reuse the existing search index, but creates a specialized one only for full text search
        contentRetrieverWithVector.deleteIndex();
        contentRetrieverWithFullText = AzureAiSearchContentRetriever.builder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .embeddingModel(null)
                .queryType(AzureAiSearchQueryType.FULL_TEXT)
                .createOrUpdateIndex(true) // This is where we force the creation of the specific search index
                .maxResults(3)
                .minScore(0.0)
                .build();
        fullTextSearch();
        clearStore();
        contentRetrieverWithFullText = createFullTextSearchContentRetriever();
    }

    @Test
    void addEmbeddingsAndRetrieveRelevantWithHybrid() {
        String content1 =
                "Albert Camus (7 November 1913 – 4 January 1960) was a French philosopher, author, dramatist, journalist, world federalist, and political activist. He was the recipient of the 1957 Nobel Prize in Literature at the age of 44, the second-youngest recipient in history. His works include The Stranger, The Plague, The Myth of Sisyphus, The Fall, and The Rebel.\n"
                        + "\n"
                        + "Camus was born in Algeria during the French colonization, to pied-noir parents. He spent his childhood in a poor neighbourhood and later studied philosophy at the University of Algiers. He was in Paris when the Germans invaded France during World War II in 1940. Camus tried to flee but finally joined the French Resistance where he served as editor-in-chief at Combat, an outlawed newspaper. After the war, he was a celebrity figure and gave many lectures around the world. He married twice but had many extramarital affairs. Camus was politically active; he was part of the left that opposed Joseph Stalin and the Soviet Union because of their totalitarianism. Camus was a moralist and leaned towards anarcho-syndicalism. He was part of many organisations seeking European integration. During the Algerian War (1954–1962), he kept a neutral stance, advocating for a multicultural and pluralistic Algeria, a position that was rejected by most parties.\n"
                        + "\n"
                        + "Philosophically, Camus' views contributed to the rise of the philosophy known as absurdism. Some consider Camus' work to show him to be an existentialist, even though he himself firmly rejected the term throughout his lifetime.";
        String content2 =
                "Gilles Louis René Deleuze (18 January 1925 – 4 November 1995) was a French philosopher who, from the early 1950s until his death in 1995, wrote on philosophy, literature, film, and fine art. His most popular works were the two volumes of Capitalism and Schizophrenia: Anti-Oedipus (1972) and A Thousand Plateaus (1980), both co-written with psychoanalyst Félix Guattari. His metaphysical treatise Difference and Repetition (1968) is considered by many scholars to be his magnum opus.\n"
                        + "\n"
                        + "An important part of Deleuze's oeuvre is devoted to the reading of other philosophers: the Stoics, Leibniz, Hume, Kant, Nietzsche, and Bergson, with particular influence derived from Spinoza. A. W. Moore, citing Bernard Williams's criteria for a great thinker, ranks Deleuze among the \"greatest philosophers\". Although he once characterized himself as a \"pure metaphysician\", his work has influenced a variety of disciplines across the humanities, including philosophy, art, and literary theory, as well as movements such as post-structuralism and postmodernism.";
        String content3 =
                "Paul-Michel Foucault (15 October 1926 – 25 June 1984) was a French philosopher, historian of ideas, writer, political activist, and literary critic. Foucault's theories primarily address the relationships between power and knowledge, and how they are used as a form of social control through societal institutions. Though often cited as a structuralist and postmodernist, Foucault rejected these labels. His thought has influenced academics, especially those working in communication studies, anthropology, psychology, sociology, criminology, cultural studies, literary theory, feminism, Marxism and critical theory.\n"
                        + "\n"
                        + "Born in Poitiers, France, into an upper-middle-class family, Foucault was educated at the Lycée Henri-IV, at the École Normale Supérieure, where he developed an interest in philosophy and came under the influence of his tutors Jean Hyppolite and Louis Althusser, and at the University of Paris (Sorbonne), where he earned degrees in philosophy and psychology. After several years as a cultural diplomat abroad, he returned to France and published his first major book, The History of Madness (1961). After obtaining work between 1960 and 1966 at the University of Clermont-Ferrand, he produced The Birth of the Clinic (1963) and The Order of Things (1966), publications that displayed his increasing involvement with structuralism, from which he later distanced himself. These first three histories exemplified a historiographical technique Foucault was developing called \"archaeology\".\n"
                        + "\n"
                        + "From 1966 to 1968, Foucault lectured at the University of Tunis before returning to France, where he became head of the philosophy department at the new experimental university of Paris VIII. Foucault subsequently published The Archaeology of Knowledge (1969). In 1970, Foucault was admitted to the Collège de France, a membership he retained until his death. He also became active in several left-wing groups involved in campaigns against racism and human rights abuses and for penal reform. Foucault later published Discipline and Punish (1975) and The History of Sexuality (1976), in which he developed archaeological and genealogical methods that emphasized the role that power plays in society.\n"
                        + "\n"
                        + "Foucault died in Paris from complications of HIV/AIDS; he became the first public figure in France to die from complications of the disease. His partner Daniel Defert founded the AIDES charity in his memory.";
        List<String> contents = asList(content1, content2, content3);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithHybrid.add(embedding, textSegment);
        }

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(contents.size()));

        Query query = Query.from("Algeria");
        List<Content> relevant = contentRetrieverWithHybrid.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertThat(relevant.get(0).textSegment().text()).contains("Albert Camus");
        assertContent(relevant.get(0));

        Query query2 = Query.from("École Normale Supérieure");
        List<Content> relevant2 = contentRetrieverWithHybrid.retrieve(query2);
        assertThat(relevant2).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());
        assertContent(relevant2.get(0));
        assertThat(relevant2.get(0).textSegment().text()).contains("Paul-Michel Foucault");
    }

    @Test
    @Disabled("no semantic ranker in a free Azure tier")
    void addEmbeddingsAndRetrieveRelevantWithHybridAndReranking() {
        String content1 =
                "Albert Camus (7 November 1913 – 4 January 1960) was a French philosopher, author, dramatist, journalist, world federalist, and political activist. He was the recipient of the 1957 Nobel Prize in Literature at the age of 44, the second-youngest recipient in history. His works include The Stranger, The Plague, The Myth of Sisyphus, The Fall, and The Rebel.\n"
                        + "\n"
                        + "Camus was born in Algeria during the French colonization, to pied-noir parents. He spent his childhood in a poor neighbourhood and later studied philosophy at the University of Algiers. He was in Paris when the Germans invaded France during World War II in 1940. Camus tried to flee but finally joined the French Resistance where he served as editor-in-chief at Combat, an outlawed newspaper. After the war, he was a celebrity figure and gave many lectures around the world. He married twice but had many extramarital affairs. Camus was politically active; he was part of the left that opposed Joseph Stalin and the Soviet Union because of their totalitarianism. Camus was a moralist and leaned towards anarcho-syndicalism. He was part of many organisations seeking European integration. During the Algerian War (1954–1962), he kept a neutral stance, advocating for a multicultural and pluralistic Algeria, a position that was rejected by most parties.\n"
                        + "\n"
                        + "Philosophically, Camus' views contributed to the rise of the philosophy known as absurdism. Some consider Camus' work to show him to be an existentialist, even though he himself firmly rejected the term throughout his lifetime.";
        String content2 =
                "Gilles Louis René Deleuze (18 January 1925 – 4 November 1995) was a French philosopher who, from the early 1950s until his death in 1995, wrote on philosophy, literature, film, and fine art. His most popular works were the two volumes of Capitalism and Schizophrenia: Anti-Oedipus (1972) and A Thousand Plateaus (1980), both co-written with psychoanalyst Félix Guattari. His metaphysical treatise Difference and Repetition (1968) is considered by many scholars to be his magnum opus.\n"
                        + "\n"
                        + "An important part of Deleuze's oeuvre is devoted to the reading of other philosophers: the Stoics, Leibniz, Hume, Kant, Nietzsche, and Bergson, with particular influence derived from Spinoza. A. W. Moore, citing Bernard Williams's criteria for a great thinker, ranks Deleuze among the \"greatest philosophers\". Although he once characterized himself as a \"pure metaphysician\", his work has influenced a variety of disciplines across the humanities, including philosophy, art, and literary theory, as well as movements such as post-structuralism and postmodernism.";
        String content3 =
                "Paul-Michel Foucault (15 October 1926 – 25 June 1984) was a French philosopher, historian of ideas, writer, political activist, and literary critic. Foucault's theories primarily address the relationships between power and knowledge, and how they are used as a form of social control through societal institutions. Though often cited as a structuralist and postmodernist, Foucault rejected these labels. His thought has influenced academics, especially those working in communication studies, anthropology, psychology, sociology, criminology, cultural studies, literary theory, feminism, Marxism and critical theory.\n"
                        + "\n"
                        + "Born in Poitiers, France, into an upper-middle-class family, Foucault was educated at the Lycée Henri-IV, at the École Normale Supérieure, where he developed an interest in philosophy and came under the influence of his tutors Jean Hyppolite and Louis Althusser, and at the University of Paris (Sorbonne), where he earned degrees in philosophy and psychology. After several years as a cultural diplomat abroad, he returned to France and published his first major book, The History of Madness (1961). After obtaining work between 1960 and 1966 at the University of Clermont-Ferrand, he produced The Birth of the Clinic (1963) and The Order of Things (1966), publications that displayed his increasing involvement with structuralism, from which he later distanced himself. These first three histories exemplified a historiographical technique Foucault was developing called \"archaeology\".\n"
                        + "\n"
                        + "From 1966 to 1968, Foucault lectured at the University of Tunis before returning to France, where he became head of the philosophy department at the new experimental university of Paris VIII. Foucault subsequently published The Archaeology of Knowledge (1969). In 1970, Foucault was admitted to the Collège de France, a membership he retained until his death. He also became active in several left-wing groups involved in campaigns against racism and human rights abuses and for penal reform. Foucault later published Discipline and Punish (1975) and The History of Sexuality (1976), in which he developed archaeological and genealogical methods that emphasized the role that power plays in society.\n"
                        + "\n"
                        + "Foucault died in Paris from complications of HIV/AIDS; he became the first public figure in France to die from complications of the disease. His partner Daniel Defert founded the AIDES charity in his memory.";
        List<String> contents = asList(content1, content2, content3);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithHybridAndReranking.add(embedding, textSegment);
        }

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(contents.size()));

        Query query = Query.from("A philosopher who was in the French Resistance");
        List<Content> relevant = contentRetrieverWithHybridAndReranking.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertContent(relevant.get(0));
        assertThat(relevant.get(0).textSegment().text()).contains("Albert Camus");

        Query query2 = Query.from("A philosopher who studied at the École Normale Supérieure");
        List<Content> relevant2 = contentRetrieverWithHybridAndReranking.retrieve(query2);
        assertThat(relevant2).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());
        assertContent(relevant2.get(0));
        assertThat(relevant2.get(0).textSegment().text()).contains("Paul-Michel Foucault");
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return contentRetrieverWithVector;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        log.debug("Deleting the search index");
        AzureAiSearchContentRetriever azureAiSearchContentRetriever = contentRetrieverWithVector;
        try {
            azureAiSearchContentRetriever.deleteIndex();
            azureAiSearchContentRetriever.createOrUpdateIndex(embeddingModel.dimension());
        } catch (RuntimeException e) {
            log.error("Failed to clean up the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected boolean assertEmbedding() {
        return false; // TODO remove this hack after https://github.com/langchain4j/langchain4j/issues/1617 is closed
    }

    private void assertContent(Content content) {
        assertThat(content.textSegment()).isNotNull();
        assertThat(content.metadata().get(ContentMetadata.SCORE)).isNotNull();
        assertThat(content.metadata().get(ContentMetadata.SCORE)).isInstanceOf(Double.class);
        assertThat((Double) content.metadata().get(ContentMetadata.SCORE)).isGreaterThanOrEqualTo(0d);
        assertThat(content.metadata().get(ContentMetadata.EMBEDDING_ID)).isNotNull();
        assertThat(content.metadata().get(ContentMetadata.EMBEDDING_ID)).isInstanceOf(String.class);
    }
}
