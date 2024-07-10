package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.sql.SQLException;
import java.util.List;

public class OracleEmbeddingStoreSample {

    public static void main(String[] args) throws SQLException {

        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("mypool");
        dataSource.setUser("scott");
        dataSource.setPassword("tiger");
        dataSource.setURL("jdbc:oracle:thin:@localhost:1521/FREEPDB1");

        EmbeddingStore<TextSegment> store = OracleEmbeddingStore.builder()
            .dataSource(dataSource)
            .table("sample_vector")
            .dimension(384)
            .useIndex(true)
            .createTable(true)
            .dropTableFirst(true)
            .normalizeVectors(true)
            .build();

        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        TextSegment mateText = TextSegment.from("Mate is a hot beverage, like tea");
        Embedding mate = embeddingModel
            .embed(mateText)
            .content();
        store.add(mate, mateText);

        TextSegment argentinaText = TextSegment.from("Mate is very popular in Argentina");
        Embedding argentina = embeddingModel
            .embed(argentinaText)
            .content();
        store.add(argentina, argentinaText);

        TextSegment textColombia = TextSegment.from("Coffee is very popular in Colombia");
        Embedding colombia = embeddingModel
            .embed(textColombia)
            .content();
        store.add(colombia, textColombia);

        Embedding query = embeddingModel
            .embed(TextSegment.from("What is the preferred hot beverage in Colombia?"))
            .content();

        EmbeddingSearchResult<TextSegment> result = store.search(
            EmbeddingSearchRequest
                .builder()
                .queryEmbedding(query)
                //.maxResults(5) TODO, possbile bug when results > rows
                .maxResults(1)
                .build());

        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.println(match.embedded().text());
        }

    }
}
