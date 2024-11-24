package dev.langchain4j.store.embedding.duckdb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

/**
 * Implementation of  {@link EmbeddingStore} using <a href="https://duckdb.org/">DuckDB</a>
 * This implementation uses cosine distance and supports storing {@link Metadata}
 */
public class DuckDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(DuckDBEmbeddingStore.class);

    private static final String CREATE_TABLE_TEMPLATE = """
            create table if not exists %s (id UUID, embedding FLOAT[], text TEXT NULL, metadata JSON NULL);
            """;

    private static final String SEARCH_QUERY_TEMPLATE = """
            select *, (list_cosine_similarity(embedding,%s)+1)/2 as score
            from %s
            where score >= %s %s
            order by score DESC
            limit(%d)
            """;

    private final String tableName;
    private final DuckDBConnection duckDBConnection;
    private final DuckDBJsonFilterMapper jsonFilterMapper = new DuckDBJsonFilterMapper();
    private final Gson jsonMetadataSerializer = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();


    /**
     * Initializes a new instance of DuckDBEmbeddingStore with the specified parameters.
     *
     * @param filePath       File used to persist DuckDB database. If not specified, the database will be stored in-memory.
     * @param tableName      The database table name to use. If not specified, "embeddings" will be used
     */
    public DuckDBEmbeddingStore(String filePath, String tableName) {
        try {
            var dbUrl = filePath != null ? "jdbc:duckdb:"+filePath : "jdbc:duckdb:";
            this.tableName = getOrDefault(tableName, "embeddings");
            this.duckDBConnection = (DuckDBConnection) DriverManager.getConnection(dbUrl);
            initTable();
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to load duckdb connection",e);
        }
    }


    /**
     * @return a new instance of DuckDBEmbeddingStore with the default configuration and database stored in-memory
     */
    public static DuckDBEmbeddingStore inMemory(){
        return new DuckDBEmbeddingStore(null,null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String filePath;
        private String tableName;

        /**
         * @param filePath File used to persist DuckDB database. If not specified, the database will be stored in-memory.
         * @return builder
         */
        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * @param tableName The database table name to use. If not specified, "embeddings" will be used
         * @return builder
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * @param tableName The database table name to use. If not specified, "embeddings" will be used
         * @return builder
         */
        public Builder inMemory(String tableName) {
            return filePath(null);
        }


        public DuckDBEmbeddingStore build() {
            return new DuckDBEmbeddingStore(filePath,tableName);
        }
    }

    public String add(final Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    public void add(final String id, final Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(final Embedding embedding, final TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    public List<String> addAll(final List<Embedding> embeddings) {
        return addAll(embeddings,null);
    }

    public List<String> addAll(final List<Embedding> embeddings, final List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .toList();
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void removeAll(final Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        var idsParam = ids.stream().map(id -> "'"+id+"'").collect(Collectors.joining(","));
        String sql = format("DELETE FROM %s WHERE id in (%s)", tableName,idsParam);
        try(var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()){
            log.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove embeddings by ids",e);
        }
    }

    @Override
    public void removeAll(final Filter filter) {
        ensureNotNull(filter, "filter");
        var whereClause = jsonFilterMapper.map(filter);
        String sql = format("delete from %s where %s", tableName,whereClause);
        try(var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()){
            log.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove embeddings with filter",e);
        }
    }

    @Override
    public void removeAll() {
        var sql = format("truncate table %s",tableName);
        try(var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()){
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove all embeddings",e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(final EmbeddingSearchRequest request) {
        try(var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()){
            var matches = new ArrayList<EmbeddingMatch<TextSegment>>();
            var param = embeddingToParam(request.queryEmbedding());
            var filterClause = request.filter() != null ? "and "+jsonFilterMapper.map(request.filter()) : "";
            var query = format(SEARCH_QUERY_TEMPLATE,param,tableName, request.minScore(),filterClause,request.maxResults());
            log.debug(query);
            var resultSet = statement.executeQuery(query);
            while (resultSet.next()){

                var id = resultSet.getString("id");
                var text = resultSet.getString("text");
                var score = resultSet.getDouble("score");
                var sqlArray = resultSet.getArray("embedding");
                var metadataJson = resultSet.getString("metadata");

                var typeToken = new TypeToken<Map<String, ?>>(){}.getType();
                Map<String,?> metadataMap = metadataJson != null ? jsonMetadataSerializer.fromJson(metadataJson,typeToken) : Collections.emptyMap();

                var sqlList = (Object[]) sqlArray.getArray();
                var vector =new float[sqlList.length];
                for(int i=0;i<sqlList.length;i++){
                    vector[i] = (float) sqlList[i];
                }
                var ts = text != null ? TextSegment.from(text,Metadata.from(metadataMap)) : null;
                matches.add(new EmbeddingMatch<>(score,id,new Embedding(vector),ts));
            }
            return new EmbeddingSearchResult<>(matches);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Error while searching embeddings",e);
        }
    }


    private void addInternal(final String id, final Embedding embedding, final TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), embedding == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[no embeddings to add to DuckDB]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");
        var insertQuery = "insert into %s (id, embedding, text, metadata) values %s";

        try (var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()) {
            var values = IntStream.range(0, ids.size())
                    .mapToObj(i->{
                        var text = "null";
                        if(embedded != null && embedded.get(i) != null){
                            text = "'"+embedded.get(i).text()+"'";
                        }
                        var metadata = embedded != null && embedded.get(i) != null ? embedded.get(i).metadata().toMap() : null;
                        return format("('%s',%s, %s, '%s')",ids.get(i),embeddingToParam(embeddings.get(i)),text, jsonMetadataSerializer.toJson(metadata));
                    })
                    .collect(Collectors.joining(","));

            var sql = format(insertQuery, tableName, values);
            log.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to add embeddings in DuckDB",e);
        }
    }

    private void initTable(){
        var sql = format(CREATE_TABLE_TEMPLATE, tableName);
        try(var connection = duckDBConnection.duplicate(); var statement = connection.createStatement()){
            log.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException(format("Failed to init duckDB table:  '%s'", sql), e);
        }
    }

    protected String embeddingToParam(Embedding embedding){
        return embedding.vectorAsList()
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(",","[","]"));
    }
}
