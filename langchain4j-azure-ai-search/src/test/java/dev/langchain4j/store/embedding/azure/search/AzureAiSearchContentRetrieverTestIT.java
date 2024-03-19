package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchContentRetrieverTestIT extends EmbeddingStoreIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetrieverTestIT.class);

    private final EmbeddingModel embeddingModel;

    private final AzureAiSearchContentRetriever contentRetrieverWithSimilarity;

    private final AzureAiSearchContentRetriever contentRetrieverWithFullText;

    private final AzureAiSearchContentRetriever contentRetrieverWithHybrid;

    private final AzureAiSearchContentRetriever contentRetrieverWithSemantic;

    private final int dimensions;

    public AzureAiSearchContentRetrieverTestIT() {

        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        dimensions = embeddingModel.embed("test").content().vector().length;

        contentRetrieverWithSimilarity =  createContentRetriever(AzureAiSearchQueryType.VECTOR);

        contentRetrieverWithFullText =  createContentRetriever(AzureAiSearchQueryType.FULL_TEXT);

        contentRetrieverWithHybrid =  createContentRetriever(AzureAiSearchQueryType.HYBRID);

        contentRetrieverWithSemantic =  createContentRetriever(AzureAiSearchQueryType.HYBRID_WITH_RRF);
    }

    private AzureAiSearchContentRetriever createContentRetriever(AzureAiSearchQueryType azureAiSearchQueryType) {
        return AzureAiSearchContentRetriever.contentRetrieverBuilder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .dimensions(dimensions)
                .embeddingModel(embeddingModel)
                .queryType(azureAiSearchQueryType)
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    @Test
    void testAddEmbeddingsAndFindRelevant() {
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
            contentRetrieverWithSimilarity.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = contentRetrieverWithSimilarity.findRelevant(relevantEmbedding, 3);
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).embedding()).isNotNull();
        assertThat(relevant.get(0).embedded().text()).isIn(content1, content3, content5);
        log.info("#1 relevant item: {}", relevant.get(0).embedded().text());
        assertThat(relevant.get(1).embedding()).isNotNull();
        assertThat(relevant.get(1).embedded().text()).isIn(content1, content3, content5);
        log.info("#2 relevant item: {}", relevant.get(1).embedded().text());
        assertThat(relevant.get(2).embedding()).isNotNull();
        assertThat(relevant.get(2).embedded().text()).isIn(content1, content3, content5);
        log.info("#3 relevant item: {}", relevant.get(2).embedded().text());
    }

    @Test
    void testAllTypesOfSearch() {
        String content1 = "This book is about politics";
        String content2 = "Cats sleeps a lot.";
        String content3 = "Sandwiches taste good.";
        String content4 = "The house is open";
        List<String> contents = asList(content1, content2, content3, content4);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithSimilarity.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        String content = "house";
        Query query = Query.from(content);

        log.info("Testing Vector Search");
        List<Content> relevant = contentRetrieverWithSimilarity.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        assertThat(relevant.get(0).textSegment().text()).isEqualTo("The house is open");
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());

        log.info("Testing Full Text Search");
        List<Content> relevant2 = contentRetrieverWithFullText.retrieve(query);
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertThat(relevant2.get(0).textSegment().text()).isEqualTo("The house is open");
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());

        log.info("Testing Hybrid Search");
        List<Content> relevant3 = contentRetrieverWithHybrid.retrieve(query);
        assertThat(relevant3).hasSizeGreaterThan(0);
        assertThat(relevant3.get(0).textSegment().text()).isEqualTo("The house is open");
        log.info("#1 relevant item: {}", relevant3.get(0).textSegment().text());

        log.info("Testing Semantic Search");
        List<Content> relevant4 = contentRetrieverWithSemantic.retrieve(query);
        assertThat(relevant4).hasSizeGreaterThan(0);
        assertThat(relevant4.get(0).textSegment().text()).isEqualTo("The house is open");
        log.info("#1 relevant item: {}", relevant4.get(0).textSegment().text());

        log.info("Test complete");
    }

    @Test
    void testAddEmbeddingsAndFindRelevantWithHybrid() {
        String content1 = "Albert Camus (7 November 1913 – 4 January 1960) was a French philosopher, author, dramatist, journalist, world federalist, and political activist. He was the recipient of the 1957 Nobel Prize in Literature at the age of 44, the second-youngest recipient in history. His works include The Stranger, The Plague, The Myth of Sisyphus, The Fall, and The Rebel.\n" +
                "\n" +
                "Camus was born in Algeria during the French colonization, to pied-noir parents. He spent his childhood in a poor neighbourhood and later studied philosophy at the University of Algiers. He was in Paris when the Germans invaded France during World War II in 1940. Camus tried to flee but finally joined the French Resistance where he served as editor-in-chief at Combat, an outlawed newspaper. After the war, he was a celebrity figure and gave many lectures around the world. He married twice but had many extramarital affairs. Camus was politically active; he was part of the left that opposed Joseph Stalin and the Soviet Union because of their totalitarianism. Camus was a moralist and leaned towards anarcho-syndicalism. He was part of many organisations seeking European integration. During the Algerian War (1954–1962), he kept a neutral stance, advocating for a multicultural and pluralistic Algeria, a position that was rejected by most parties.\n" +
                "\n" +
                "Philosophically, Camus' views contributed to the rise of the philosophy known as absurdism. Some consider Camus' work to show him to be an existentialist, even though he himself firmly rejected the term throughout his lifetime.";
        String content2 = "Gilles Louis René Deleuze (18 January 1925 – 4 November 1995) was a French philosopher who, from the early 1950s until his death in 1995, wrote on philosophy, literature, film, and fine art. His most popular works were the two volumes of Capitalism and Schizophrenia: Anti-Oedipus (1972) and A Thousand Plateaus (1980), both co-written with psychoanalyst Félix Guattari. His metaphysical treatise Difference and Repetition (1968) is considered by many scholars to be his magnum opus.\n" +
                "\n" +
                "An important part of Deleuze's oeuvre is devoted to the reading of other philosophers: the Stoics, Leibniz, Hume, Kant, Nietzsche, and Bergson, with particular influence derived from Spinoza. A. W. Moore, citing Bernard Williams's criteria for a great thinker, ranks Deleuze among the \"greatest philosophers\". Although he once characterized himself as a \"pure metaphysician\", his work has influenced a variety of disciplines across the humanities, including philosophy, art, and literary theory, as well as movements such as post-structuralism and postmodernism.";
        String content3 = "Paul-Michel Foucault (15 October 1926 – 25 June 1984) was a French philosopher, historian of ideas, writer, political activist, and literary critic. Foucault's theories primarily address the relationships between power and knowledge, and how they are used as a form of social control through societal institutions. Though often cited as a structuralist and postmodernist, Foucault rejected these labels. His thought has influenced academics, especially those working in communication studies, anthropology, psychology, sociology, criminology, cultural studies, literary theory, feminism, Marxism and critical theory.\n" +
                "\n" +
                "Born in Poitiers, France, into an upper-middle-class family, Foucault was educated at the Lycée Henri-IV, at the École Normale Supérieure, where he developed an interest in philosophy and came under the influence of his tutors Jean Hyppolite and Louis Althusser, and at the University of Paris (Sorbonne), where he earned degrees in philosophy and psychology. After several years as a cultural diplomat abroad, he returned to France and published his first major book, The History of Madness (1961). After obtaining work between 1960 and 1966 at the University of Clermont-Ferrand, he produced The Birth of the Clinic (1963) and The Order of Things (1966), publications that displayed his increasing involvement with structuralism, from which he later distanced himself. These first three histories exemplified a historiographical technique Foucault was developing called \"archaeology\".\n" +
                "\n" +
                "From 1966 to 1968, Foucault lectured at the University of Tunis before returning to France, where he became head of the philosophy department at the new experimental university of Paris VIII. Foucault subsequently published The Archaeology of Knowledge (1969). In 1970, Foucault was admitted to the Collège de France, a membership he retained until his death. He also became active in several left-wing groups involved in campaigns against racism and human rights abuses and for penal reform. Foucault later published Discipline and Punish (1975) and The History of Sexuality (1976), in which he developed archaeological and genealogical methods that emphasized the role that power plays in society.\n" +
                "\n" +
                "Foucault died in Paris from complications of HIV/AIDS; he became the first public figure in France to die from complications of the disease. His partner Daniel Defert founded the AIDES charity in his memory.";
        List<String> contents = asList(content1, content2, content3);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithHybrid.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Query query = Query.from("Algeria");
        List<Content> relevant = contentRetrieverWithHybrid.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertThat(relevant.get(0).textSegment().text()).contains("Albert Camus");

        Query query2 = Query.from("École Normale Supérieure");
        List<Content> relevant2 = contentRetrieverWithHybrid.retrieve(query2);
        assertThat(relevant2).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());
        assertThat(relevant2.get(0).textSegment().text()).contains("Paul-Michel Foucault");
    }

    @Test
    void testAddEmbeddingsAndFindRelevantWithSemantic() {
        String content1 = "Albert Camus (7 November 1913 – 4 January 1960) was a French philosopher, author, dramatist, journalist, world federalist, and political activist. He was the recipient of the 1957 Nobel Prize in Literature at the age of 44, the second-youngest recipient in history. His works include The Stranger, The Plague, The Myth of Sisyphus, The Fall, and The Rebel.\n" +
                "\n" +
                "Camus was born in Algeria during the French colonization, to pied-noir parents. He spent his childhood in a poor neighbourhood and later studied philosophy at the University of Algiers. He was in Paris when the Germans invaded France during World War II in 1940. Camus tried to flee but finally joined the French Resistance where he served as editor-in-chief at Combat, an outlawed newspaper. After the war, he was a celebrity figure and gave many lectures around the world. He married twice but had many extramarital affairs. Camus was politically active; he was part of the left that opposed Joseph Stalin and the Soviet Union because of their totalitarianism. Camus was a moralist and leaned towards anarcho-syndicalism. He was part of many organisations seeking European integration. During the Algerian War (1954–1962), he kept a neutral stance, advocating for a multicultural and pluralistic Algeria, a position that was rejected by most parties.\n" +
                "\n" +
                "Philosophically, Camus' views contributed to the rise of the philosophy known as absurdism. Some consider Camus' work to show him to be an existentialist, even though he himself firmly rejected the term throughout his lifetime.";
        String content2 = "Gilles Louis René Deleuze (18 January 1925 – 4 November 1995) was a French philosopher who, from the early 1950s until his death in 1995, wrote on philosophy, literature, film, and fine art. His most popular works were the two volumes of Capitalism and Schizophrenia: Anti-Oedipus (1972) and A Thousand Plateaus (1980), both co-written with psychoanalyst Félix Guattari. His metaphysical treatise Difference and Repetition (1968) is considered by many scholars to be his magnum opus.\n" +
                "\n" +
                "An important part of Deleuze's oeuvre is devoted to the reading of other philosophers: the Stoics, Leibniz, Hume, Kant, Nietzsche, and Bergson, with particular influence derived from Spinoza. A. W. Moore, citing Bernard Williams's criteria for a great thinker, ranks Deleuze among the \"greatest philosophers\". Although he once characterized himself as a \"pure metaphysician\", his work has influenced a variety of disciplines across the humanities, including philosophy, art, and literary theory, as well as movements such as post-structuralism and postmodernism.";
        String content3 = "Paul-Michel Foucault (15 October 1926 – 25 June 1984) was a French philosopher, historian of ideas, writer, political activist, and literary critic. Foucault's theories primarily address the relationships between power and knowledge, and how they are used as a form of social control through societal institutions. Though often cited as a structuralist and postmodernist, Foucault rejected these labels. His thought has influenced academics, especially those working in communication studies, anthropology, psychology, sociology, criminology, cultural studies, literary theory, feminism, Marxism and critical theory.\n" +
                "\n" +
                "Born in Poitiers, France, into an upper-middle-class family, Foucault was educated at the Lycée Henri-IV, at the École Normale Supérieure, where he developed an interest in philosophy and came under the influence of his tutors Jean Hyppolite and Louis Althusser, and at the University of Paris (Sorbonne), where he earned degrees in philosophy and psychology. After several years as a cultural diplomat abroad, he returned to France and published his first major book, The History of Madness (1961). After obtaining work between 1960 and 1966 at the University of Clermont-Ferrand, he produced The Birth of the Clinic (1963) and The Order of Things (1966), publications that displayed his increasing involvement with structuralism, from which he later distanced himself. These first three histories exemplified a historiographical technique Foucault was developing called \"archaeology\".\n" +
                "\n" +
                "From 1966 to 1968, Foucault lectured at the University of Tunis before returning to France, where he became head of the philosophy department at the new experimental university of Paris VIII. Foucault subsequently published The Archaeology of Knowledge (1969). In 1970, Foucault was admitted to the Collège de France, a membership he retained until his death. He also became active in several left-wing groups involved in campaigns against racism and human rights abuses and for penal reform. Foucault later published Discipline and Punish (1975) and The History of Sexuality (1976), in which he developed archaeological and genealogical methods that emphasized the role that power plays in society.\n" +
                "\n" +
                "Foucault died in Paris from complications of HIV/AIDS; he became the first public figure in France to die from complications of the disease. His partner Daniel Defert founded the AIDES charity in his memory.";
        List<String> contents = asList(content1, content2, content3);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            contentRetrieverWithSemantic.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Query query = Query.from("A philosopher who was in the French Resistance");
        List<Content> relevant = contentRetrieverWithSemantic.retrieve(query);
        assertThat(relevant).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant.get(0).textSegment().text());
        assertThat(relevant.get(0).textSegment().text()).contains("Albert Camus");

        Query query2 = Query.from("A philosopher who studied at the École Normale Supérieure");
        List<Content> relevant2 = contentRetrieverWithSemantic.retrieve(query2);
        assertThat(relevant2).hasSizeGreaterThan(0);
        log.info("#1 relevant item: {}", relevant2.get(0).textSegment().text());
        assertThat(relevant2.get(0).textSegment().text()).contains("Paul-Michel Foucault");
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return contentRetrieverWithSimilarity;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        AzureAiSearchContentRetriever azureAiSearchContentRetriever = (AzureAiSearchContentRetriever) contentRetrieverWithSimilarity;
        try {
            azureAiSearchContentRetriever.deleteIndex();
            azureAiSearchContentRetriever.createOrUpdateIndex(dimensions);
        } catch (RuntimeException e) {
            log.error("Failed to clean up the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected void awaitUntilPersisted() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
