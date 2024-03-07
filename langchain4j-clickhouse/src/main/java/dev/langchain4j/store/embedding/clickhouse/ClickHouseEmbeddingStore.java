package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.client.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

import static com.clickhouse.data.ClickHouseFormat.RowBinaryWithNamesAndTypes;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * TODO
 */
public class ClickHouseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final ClickHouseNodes servers;
    private final ClickHouseClient client;
    private final ClickHouseSetting setting;

    public ClickHouseEmbeddingStore(ClickHouseClient client,
                                    ClickHouseSetting setting) {
        this.setting = ensureNotNull(setting, "setting");
        servers = ClickHouseNodes.of(ensureNotNull(setting.getEndpoint(), "endpoint"));

        if (client != null) {
            this.client = client;
        } else {
            ClickHouseCredentials credentials = ClickHouseCredentials.fromUserAndPassword(
                    ensureNotNull(setting.getUsername(), "username"), ensureNotNull(setting.getPassword(), "password"));
            this.client = ClickHouseClient.newInstance(credentials, ClickHouseProtocol.HTTP);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ClickHouseClient client;
        private ClickHouseSetting setting;

        public Builder client(ClickHouseClient client) {
            this.client = client;
            return this;
        }

        public Builder setting(ClickHouseSetting setting) {
            this.setting = setting;
            return this;
        }

        public ClickHouseEmbeddingStore build() {
            return new ClickHouseEmbeddingStore(client, setting);
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // TODO
        return null;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        try {
            ClickHouseResponse response = client.read(servers).write()
                    .format(RowBinaryWithNamesAndTypes)
                    .query(buildInsertSql(ids, embeddings, embedded))
                    .executeAndWait();

            ClickHouseResponseSummary summary = response.getSummary();
        } catch (ClickHouseException e) {
            // TODO: log exception
            throw new RuntimeException(e);
        }
    }

    private String buildInsertSql(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        // TODO
        return String.format("insert into %s.%s", setting.getDatabase(), setting.getTable());
    }
}
