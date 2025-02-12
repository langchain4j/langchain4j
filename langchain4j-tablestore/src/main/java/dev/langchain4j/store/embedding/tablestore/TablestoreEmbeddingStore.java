package dev.langchain4j.store.embedding.tablestore;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.core.utils.ValueUtil;
import com.alicloud.openservices.tablestore.model.CapacityUnit;
import com.alicloud.openservices.tablestore.model.Column;
import com.alicloud.openservices.tablestore.model.ColumnType;
import com.alicloud.openservices.tablestore.model.ColumnValue;
import com.alicloud.openservices.tablestore.model.CreateTableRequest;
import com.alicloud.openservices.tablestore.model.DeleteRowRequest;
import com.alicloud.openservices.tablestore.model.DeleteTableRequest;
import com.alicloud.openservices.tablestore.model.Direction;
import com.alicloud.openservices.tablestore.model.GetRangeRequest;
import com.alicloud.openservices.tablestore.model.GetRangeResponse;
import com.alicloud.openservices.tablestore.model.ListTableResponse;
import com.alicloud.openservices.tablestore.model.PrimaryKey;
import com.alicloud.openservices.tablestore.model.PrimaryKeyBuilder;
import com.alicloud.openservices.tablestore.model.PrimaryKeySchema;
import com.alicloud.openservices.tablestore.model.PrimaryKeyType;
import com.alicloud.openservices.tablestore.model.PrimaryKeyValue;
import com.alicloud.openservices.tablestore.model.PutRowRequest;
import com.alicloud.openservices.tablestore.model.RangeRowQueryCriteria;
import com.alicloud.openservices.tablestore.model.ReservedThroughput;
import com.alicloud.openservices.tablestore.model.Row;
import com.alicloud.openservices.tablestore.model.RowDeleteChange;
import com.alicloud.openservices.tablestore.model.RowPutChange;
import com.alicloud.openservices.tablestore.model.TableMeta;
import com.alicloud.openservices.tablestore.model.TableOptions;
import com.alicloud.openservices.tablestore.model.search.CreateSearchIndexRequest;
import com.alicloud.openservices.tablestore.model.search.DeleteSearchIndexRequest;
import com.alicloud.openservices.tablestore.model.search.FieldSchema;
import com.alicloud.openservices.tablestore.model.search.FieldType;
import com.alicloud.openservices.tablestore.model.search.IndexSchema;
import com.alicloud.openservices.tablestore.model.search.ListSearchIndexRequest;
import com.alicloud.openservices.tablestore.model.search.ListSearchIndexResponse;
import com.alicloud.openservices.tablestore.model.search.SearchHit;
import com.alicloud.openservices.tablestore.model.search.SearchIndexInfo;
import com.alicloud.openservices.tablestore.model.search.SearchQuery;
import com.alicloud.openservices.tablestore.model.search.SearchRequest;
import com.alicloud.openservices.tablestore.model.search.SearchResponse;
import com.alicloud.openservices.tablestore.model.search.query.KnnVectorQuery;
import com.alicloud.openservices.tablestore.model.search.query.Query;
import com.alicloud.openservices.tablestore.model.search.query.QueryBuilders;
import com.alicloud.openservices.tablestore.model.search.sort.ScoreSort;
import com.alicloud.openservices.tablestore.model.search.sort.Sort;
import com.alicloud.openservices.tablestore.model.search.vector.VectorDataType;
import com.alicloud.openservices.tablestore.model.search.vector.VectorMetricType;
import com.alicloud.openservices.tablestore.model.search.vector.VectorOptions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class TablestoreEmbeddingStore implements EmbeddingStore<TextSegment> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SyncClient client;
    private final String tableName;
    private final String searchIndexName;
    private final String pkName;
    private final String textField;
    private final String embeddingField;
    private final int vectorDimension;
    private final VectorMetricType vectorMetricType;
    private final List<FieldSchema> metadataSchemaList;

    private static final String DEFAULT_TABLE_NAME = "langchain4j_embedding_store_ots_v1";
    private static final String DEFAULT_INDEX_NAME = "langchain4j_embedding_ots_index_v1";
    private static final String DEFAULT_TABLE_PK_NAME = "id";
    private static final String DEFAULT_TEXT_FIELD_NAME = "default_content";
    private static final String DEFAULT_VECTOR_FIELD_NAME = "default_embedding";
    private static final VectorMetricType DEFAULT_VECTOR_METRIC_TYPE = VectorMetricType.COSINE;


    public TablestoreEmbeddingStore(SyncClient client, int vectorDimension) {
        this(client, vectorDimension, Collections.emptyList());
    }

    public TablestoreEmbeddingStore(SyncClient client, int vectorDimension, List<FieldSchema> metadataSchemaList) {
        this(client, DEFAULT_TABLE_NAME, DEFAULT_INDEX_NAME, DEFAULT_TABLE_PK_NAME, DEFAULT_TEXT_FIELD_NAME, DEFAULT_VECTOR_FIELD_NAME, vectorDimension, DEFAULT_VECTOR_METRIC_TYPE, metadataSchemaList);
    }

    public TablestoreEmbeddingStore(SyncClient client, String tableName, String searchIndexName, String pkName, String textField, String embeddingField, int vectorDimension, VectorMetricType vectorMetricType, List<FieldSchema> metadataSchemaList) {
        this.client = ValidationUtils.ensureNotNull(client, "client");
        this.tableName = ValidationUtils.ensureNotBlank(tableName, "tableName");
        this.searchIndexName = ValidationUtils.ensureNotBlank(searchIndexName, "searchIndexName");
        this.pkName = ValidationUtils.ensureNotBlank(pkName, "pkName");
        this.textField = ValidationUtils.ensureNotBlank(textField, "textField");
        this.embeddingField = ValidationUtils.ensureNotBlank(embeddingField, "embeddingField");
        this.vectorDimension = ValidationUtils.ensureGreaterThanZero(vectorDimension, "vectorDimension");
        this.vectorMetricType = ValidationUtils.ensureNotNull(vectorMetricType, "vectorMetricType");
        ValidationUtils.ensureNotNull(metadataSchemaList, "metadataSchemaList");
        List<FieldSchema> tmpMetaList = new ArrayList<>();
        tmpMetaList.add(new FieldSchema(textField, FieldType.TEXT).setIndex(true).setAnalyzer(FieldSchema.Analyzer.MaxWord));
        tmpMetaList.add(new FieldSchema(embeddingField, FieldType.VECTOR).setIndex(true).setVectorOptions(new VectorOptions(VectorDataType.FLOAT_32, vectorDimension, vectorMetricType)));
        for (FieldSchema fieldSchema : metadataSchemaList) {
            if (fieldSchema.getFieldName().equals(textField)) {
                throw Exceptions.illegalArgument("the custom meta data field name matches the system text field:{}", textField);
            }
            if (fieldSchema.getFieldName().equals(embeddingField)) {
                throw Exceptions.illegalArgument("the custom meta data field name matches the system embedding field:{}", embeddingField);
            }
            tmpMetaList.add(fieldSchema);
        }
        this.metadataSchemaList = Collections.unmodifiableList(tmpMetaList);
    }

    public void init() {
        createTableIfNotExist();
        createSearchIndexIfNotExist();
    }

    public SyncClient getClient() {
        return client;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSearchIndexName() {
        return searchIndexName;
    }

    public String getPkName() {
        return pkName;
    }

    public String getTextField() {
        return textField;
    }

    public String getEmbeddingField() {
        return embeddingField;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }

    public VectorMetricType getVectorMetricType() {
        return vectorMetricType;
    }

    public List<FieldSchema> getMetadataSchemaList() {
        return metadataSchemaList;
    }

    @Override
    public String add(Embedding embedding) {
        String id = UUID.randomUUID().toString();
        innerAdd(id, embedding, null);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        innerAdd(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        innerAdd(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embedded != null) {
            ValidationUtils.ensureEq(embeddings.size(), embedded.size(), "the size of embeddings should be the same as the size of embedded");
        }
        List<Exception> exceptions = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = null;
            if (embedded != null) {
                textSegment = embedded.get(i);
            }
            try {
                innerAdd(ids.get(i), embedding, textSegment);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            IllegalStateException exception = new IllegalStateException("Add all embeddings with error, failed:" + exceptions.size());
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    @Override
    public void remove(String id) {
        ensureNotBlank(id, "id");
        innerDelete(id);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw Exceptions.illegalArgument("ids cannot be null or empty");
        }
        log.debug("remove all:{}", ids);
        List<Exception> exceptions = new ArrayList<>();
        for (String id : ids) {
            try {
                remove(id);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            IllegalStateException exception = new IllegalStateException("remove all embeddings with error, failed:" + exceptions.size());
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    @Override
    public void removeAll(Filter filter) {
        if (filter == null) {
            throw Exceptions.illegalArgument("filter cannot be null");
        }
        forEachAllData(Collections.emptyList(), (row -> {
            Metadata metadata = rowToMetadata(row);
            if (filter.test(metadata)) {
                remove(row.getPrimaryKey().getPrimaryKeyColumn(pkName).getValue().asString());
            }
        }));
    }

    @Override
    public void removeAll() {
        log.debug("remove all");
        forEachAllData(Collections.emptyList(), (row) -> {
            this.innerDelete(row.getPrimaryKey().getPrimaryKeyColumn(pkName).getValue().asString());
        });
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        log.debug("search ([...{}...], {}, {})", request.queryEmbedding().vector().length, request.maxResults(), request.minScore());
        KnnVectorQuery knnVectorQuery = QueryBuilders.knnVector(embeddingField, request.maxResults(), request.queryEmbedding().vector())
                .filter(mapFilterToQuery(request.filter()))
                .build();
        SearchQuery searchQuery = SearchQuery.newBuilder()
                .query(knnVectorQuery)
                .getTotalCount(false)
                .limit(request.maxResults())
                .offset(0)
                .sort(new Sort(Collections.singletonList(new ScoreSort())))
                .build();
        SearchRequest searchRequest = SearchRequest.newBuilder()
                .tableName(tableName)
                .indexName(searchIndexName)
                .searchQuery(searchQuery)
                .returnAllColumns(true)
                .build();
        SearchResponse response = client.search(searchRequest);
        log.debug("search requestId:{}", response.getRequestId());
        return searchResponseToEmbeddingSearchResult(request, response);
    }

    protected Query mapFilterToQuery(Filter filter) {
        return TablestoreMetadataFilterMapper.map(filter);
    }

    private EmbeddingSearchResult<TextSegment> searchResponseToEmbeddingSearchResult(EmbeddingSearchRequest request, SearchResponse response) {
        List<SearchHit> searchHits = response.getSearchHits();
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(searchHits.size());
        for (SearchHit hit : searchHits) {
            Double score = hit.getScore();
            if (score < request.minScore()) {
                continue;
            }
            Row row = hit.getRow();

            String text = null;
            if (row.getLatestColumn(textField) != null) {
                text = row.getLatestColumn(textField).getValue().asString();
            }

            float[] embedding = null;
            if (row.getLatestColumn(embeddingField) != null) {
                String embeddingString = row.getLatestColumn(embeddingField).getValue().asString();
                embedding = TablestoreUtils.parseEmbeddingString(embeddingString);
            }

            Metadata metadata = rowToMetadata(row);

            TextSegment textSegment = null;
            if (text != null && embedding != null) {
                textSegment = new TextSegment(text, metadata);
            }

            EmbeddingMatch<TextSegment> match = new EmbeddingMatch<TextSegment>(
                    score,
                    row.getPrimaryKey().getPrimaryKeyColumn(pkName).getValue().asString(),
                    new Embedding(embedding),
                    textSegment
            );
            matches.add(match);
        }
        return new EmbeddingSearchResult<>(matches);
    }

    private void createTableIfNotExist() {
        if (tableExists()) {
            log.info("table:{} already exists", tableName);
            return;
        }
        TableMeta tableMeta = new TableMeta(this.tableName);
        tableMeta.addPrimaryKeyColumn(new PrimaryKeySchema(pkName, PrimaryKeyType.STRING));
        TableOptions tableOptions = new TableOptions(-1, 1);
        CreateTableRequest request = new CreateTableRequest(tableMeta, tableOptions);
        request.setReservedThroughput(new ReservedThroughput(new CapacityUnit(0, 0)));
        client.createTable(request);
        log.info("create table:{}", tableName);
    }

    private void createSearchIndexIfNotExist() {
        if (searchindexExists()) {
            log.info("index:{} already exists", searchIndexName);
            return;
        }
        CreateSearchIndexRequest request = new CreateSearchIndexRequest();
        request.setTableName(tableName);
        request.setIndexName(searchIndexName);
        IndexSchema indexSchema = new IndexSchema();
        indexSchema.setFieldSchemas(metadataSchemaList);
        request.setIndexSchema(indexSchema);
        client.createSearchIndex(request);
        log.info("create index:{}", searchIndexName);
    }

    protected void deleteTableAndIndex() {
        List<SearchIndexInfo> searchIndexInfos = listSearchIndex();
        deleteIndex(searchIndexInfos);
        deleteTable();
    }

    private boolean tableExists() {
        ListTableResponse listTableResponse = client.listTable();
        return listTableResponse.getTableNames().contains(tableName);
    }

    private boolean searchindexExists() {
        List<SearchIndexInfo> searchIndexInfos = listSearchIndex();
        for (SearchIndexInfo indexInfo : searchIndexInfos) {
            if (indexInfo.getIndexName().equals(searchIndexName)) {
                return true;
            }
        }
        return false;
    }

    private void deleteIndex(List<SearchIndexInfo> indexNames) {
        indexNames.forEach(info -> {
            DeleteSearchIndexRequest request = new DeleteSearchIndexRequest();
            request.setTableName(info.getTableName());
            request.setIndexName(info.getIndexName());
            client.deleteSearchIndex(request);
            log.info("delete table:{}, index:{}", info.getTableName(), info.getIndexName());
        });
    }

    private void deleteTable() {
        DeleteTableRequest request = new DeleteTableRequest(tableName);
        client.deleteTable(request);
        log.info("delete table:{}", tableName);
    }

    private List<SearchIndexInfo> listSearchIndex() {
        ListSearchIndexRequest request = new ListSearchIndexRequest();
        request.setTableName(tableName);
        ListSearchIndexResponse listSearchIndexResponse = client.listSearchIndex(request);
        return listSearchIndexResponse.getIndexInfos();
    }

    protected void innerAdd(String id, Embedding embedding, TextSegment textSegment) {
        ValidationUtils.ensureNotNull(embedding, "embedding");
        PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName, PrimaryKeyValue.fromString(id));
        PrimaryKey primaryKey = primaryKeyBuilder.build();
        RowPutChange rowPutChange = new RowPutChange(this.tableName, primaryKey);
        String embeddinged = TablestoreUtils.embeddingToString(embedding.vector());
        rowPutChange.addColumn(new Column(this.embeddingField, ColumnValue.fromString(embeddinged)));
        if (textSegment != null) {
            String text = textSegment.text();
            if (text != null) {
                rowPutChange.addColumn(new Column(this.textField, ColumnValue.fromString(text)));
            }
            Metadata metadata = textSegment.metadata();
            if (metadata != null) {
                Map<String, Object> map = metadata.toMap();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (this.textField.equals(key)) {
                        throw Exceptions.illegalArgument("there is a metadata(%s,%s) that is consistent with the name of the text field:%s", key, value, this.textField);
                    }
                    if (this.embeddingField.equals(key)) {
                        throw Exceptions.illegalArgument("there is a metadata(%s,%s) that is consistent with the name of the vector field:%s", key, value, this.embeddingField);
                    }
                    if (value instanceof Float) {
                        rowPutChange.addColumn(new Column(key, ColumnValue.fromDouble((Float) value)));
                    } else if (value instanceof UUID) {
                        rowPutChange.addColumn(new Column(key, ColumnValue.fromString(((UUID) value).toString())));
                    } else {
                        rowPutChange.addColumn(new Column(key, ValueUtil.toColumnValue(value)));
                    }
                }
            }
        }
        try {
            client.putRow(new PutRowRequest(rowPutChange));
            if (log.isDebugEnabled()) {
                log.debug("add id:{}, textSegment:{}, embedding:{}", id, textSegment, TablestoreUtils.maxLogOrNull(embedding.toString()));
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("add embedding data failed, id:%s, textSegment:%s,embedding:%s", id, textSegment, embedding), e);
        }
    }

    protected void innerDelete(String id) {
        PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName, PrimaryKeyValue.fromString(id));
        PrimaryKey primaryKey = primaryKeyBuilder.build();
        RowDeleteChange rowDeleteChange = new RowDeleteChange(this.tableName, primaryKey);
        try {
            client.deleteRow(new DeleteRowRequest(rowDeleteChange));
            log.debug("delete id:{}", id);
        } catch (Exception e) {
            throw new RuntimeException(String.format("delete embedding data failed, id:%s", id), e);
        }
    }

    private void forEachAllData(Collection<String> columnsToGet, Consumer<Row> rowConsumer) {
        RangeRowQueryCriteria rangeRowQueryCriteria = new RangeRowQueryCriteria(this.tableName);
        PrimaryKeyBuilder start = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        start.addPrimaryKeyColumn(this.pkName, PrimaryKeyValue.INF_MIN);
        PrimaryKeyBuilder end = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        end.addPrimaryKeyColumn(this.pkName, PrimaryKeyValue.INF_MAX);
        rangeRowQueryCriteria.setInclusiveStartPrimaryKey(start.build());
        rangeRowQueryCriteria.setExclusiveEndPrimaryKey(end.build());
        rangeRowQueryCriteria.setMaxVersions(1);
        rangeRowQueryCriteria.setLimit(5000);
        rangeRowQueryCriteria.addColumnsToGet(columnsToGet);
        rangeRowQueryCriteria.setDirection(Direction.FORWARD);
        GetRangeRequest getRangeRequest = new GetRangeRequest(rangeRowQueryCriteria);
        GetRangeResponse getRangeResponse;
        while (true) {
            getRangeResponse = client.getRange(getRangeRequest);
            for (Row row : getRangeResponse.getRows()) {
                rowConsumer.accept(row);
            }
            if (getRangeResponse.getNextStartPrimaryKey() != null) {
                rangeRowQueryCriteria.setInclusiveStartPrimaryKey(getRangeResponse.getNextStartPrimaryKey());
            } else {
                break;
            }
        }
    }

    private Metadata rowToMetadata(Row row) {
        Metadata metadata = new Metadata();
        for (Column column : row.getColumns()) {
            if (column.getName().equals(embeddingField)) {
                continue;
            }
            if (column.getName().equals(textField)) {
                continue;
            }
            ColumnType columnType = column.getValue().getType();
            switch (columnType) {
                case STRING:
                    metadata.put(column.getName(), column.getValue().asString());
                    break;
                case INTEGER:
                    metadata.put(column.getName(), column.getValue().asLong());
                    break;
                case DOUBLE:
                    metadata.put(column.getName(), column.getValue().asDouble());
                    break;
                default:
                    log.warn("unsupported columnType:{}, key:{}, value:{}", columnType, column.getName(), column.getValue());
            }
        }
        return metadata;
    }

}
