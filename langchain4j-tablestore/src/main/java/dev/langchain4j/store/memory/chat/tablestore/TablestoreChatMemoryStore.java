package dev.langchain4j.store.memory.chat.tablestore;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.CapacityUnit;
import com.alicloud.openservices.tablestore.model.Column;
import com.alicloud.openservices.tablestore.model.ColumnValue;
import com.alicloud.openservices.tablestore.model.CreateTableRequest;
import com.alicloud.openservices.tablestore.model.DeleteRowRequest;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TablestoreChatMemoryStore implements ChatMemoryStore {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SyncClient client;
    private final String tableName;
    private final String pkName1;
    private final String pkName2;
    private final String chatMessageFieldName;

    private static final String DEFAULT_TABLE_NAME = "langchain4j_chat_memory_store_ots_v1";
    private static final String DEFAULT_TABLE_PK_1_NAME = "memory_id";
    private static final String DEFAULT_TABLE_PK_2_NAME = "seq_no";
    private static final String DEFAULT_CHAT_MESSAGE_FIELD_NAME = "chat_message";

    public TablestoreChatMemoryStore(SyncClient client) {
        this(client, DEFAULT_TABLE_NAME, DEFAULT_TABLE_PK_1_NAME, DEFAULT_TABLE_PK_2_NAME, DEFAULT_CHAT_MESSAGE_FIELD_NAME);
    }

    public TablestoreChatMemoryStore(SyncClient client, String tableName, String pkName1, String pkName2, String chatMessageFieldName) {
        this.client = client;
        this.tableName = tableName;
        this.pkName1 = pkName1;
        this.pkName2 = pkName2;
        this.chatMessageFieldName = chatMessageFieldName;
    }

    public void init() {
        createTableIfNotExist();
    }

    /**
     * Clear all message.
     */
    public void clear() {
        forEachAllData(PrimaryKeyValue.INF_MIN, PrimaryKeyValue.INF_MAX, row -> {
                    String id = row.getPrimaryKey().getPrimaryKeyColumn(pkName1).getValue().asString();
                    long seqNo = row.getPrimaryKey().getPrimaryKeyColumn(pkName2).getValue().asLong();
                    innerDelete(id, seqNo);
                }
        );
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String memoryIdStr = getMemoryId(memoryId);
        log.debug("get messages, memoryIdStr:{}", memoryIdStr);
        List<ChatMessage> messages = new ArrayList<>();
        forEachAllData(PrimaryKeyValue.fromString(memoryIdStr), row -> {
            Column column = row.getLatestColumn(chatMessageFieldName);
            if (column != null) {
                String jsonString = column.getValue().asString();
                try {
                    ChatMessage chatMessage = ChatMessageDeserializer.messageFromJson(jsonString);
                    messages.add(chatMessage);
                } catch (Exception e) {
                    String id = row.getPrimaryKey().getPrimaryKeyColumn(pkName1).getValue().asString();
                    long seqNo = row.getPrimaryKey().getPrimaryKeyColumn(pkName2).getValue().asLong();
                    throw new RuntimeException(String.format("unable to parse message body, memoryId:%s, seqNo:%s", id, seqNo), e);
                }
            }
        });
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String memoryIdStr = getMemoryId(memoryId);
        log.debug("update messages, memoryIdStr:{}", memoryIdStr);
        ValidationUtils.ensureNotEmpty(messages, "messages");
        deleteMessages(memoryId);
        List<Exception> exceptions = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            try {
                innerAdd(memoryIdStr, i, ChatMessageSerializer.messageToJson(message));
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            IllegalStateException exception = new IllegalStateException("update messages with error, failed:" + exceptions.size());
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String memoryIdStr = getMemoryId(memoryId);
        log.debug("delete messages, memoryIdStr:{}", memoryIdStr);
        forEachAllData(PrimaryKeyValue.fromString(memoryIdStr), row -> {
            String id = row.getPrimaryKey().getPrimaryKeyColumn(pkName1).getValue().asString();
            long seqNo = row.getPrimaryKey().getPrimaryKeyColumn(pkName2).getValue().asLong();
            innerDelete(id, seqNo);
        });
    }

    private void innerDelete(String memoryId, long seqNo) {
        ValidationUtils.ensureNotNull(memoryId, "memoryId");
        ValidationUtils.ensureNotNull(seqNo, "seqNo");
        PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName1, PrimaryKeyValue.fromString(memoryId));
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName2, PrimaryKeyValue.fromLong(seqNo));
        PrimaryKey primaryKey = primaryKeyBuilder.build();
        RowDeleteChange rowDeleteChange = new RowDeleteChange(this.tableName, primaryKey);
        try {
            client.deleteRow(new DeleteRowRequest(rowDeleteChange));
            log.debug("delete memoryId:{}, seqNo:{}", memoryId, seqNo);
        } catch (Exception e) {
            throw new RuntimeException(String.format("delete embedding data failed, memoryId:%s, seqNo:%s", memoryId, seqNo), e);
        }
    }

    private void innerAdd(String memoryId, int seqNo, String chatMessage) {
        ValidationUtils.ensureNotNull(memoryId, "memoryId");
        ValidationUtils.ensureNotNull(seqNo, "seqNo");
        ValidationUtils.ensureNotNull(chatMessage, "chatMessage");
        PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName1, PrimaryKeyValue.fromString(memoryId));
        primaryKeyBuilder.addPrimaryKeyColumn(this.pkName2, PrimaryKeyValue.fromLong(seqNo));
        PrimaryKey primaryKey = primaryKeyBuilder.build();
        RowPutChange rowPutChange = new RowPutChange(this.tableName, primaryKey);
        rowPutChange.addColumn(new Column(chatMessageFieldName, ColumnValue.fromString(chatMessage)));
        try {
            client.putRow(new PutRowRequest(rowPutChange));
            if (log.isDebugEnabled()) {
                log.debug("add memoryId:{}, seqNo:{}, chatMessage:{}", memoryId, seqNo, chatMessage);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("add embedding data failed, memoryId:%s, seqNo:%s, chatMessage:%s", memoryId, seqNo, chatMessage), e);
        }
    }

    private String getMemoryId(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    private void createTableIfNotExist() {
        if (tableExists()) {
            log.info("table:{} already exists", tableName);
            return;
        }
        TableMeta tableMeta = new TableMeta(this.tableName);
        tableMeta.addPrimaryKeyColumn(new PrimaryKeySchema(pkName1, PrimaryKeyType.STRING));
        tableMeta.addPrimaryKeyColumn(new PrimaryKeySchema(pkName2, PrimaryKeyType.INTEGER));
        TableOptions tableOptions = new TableOptions(-1, 1);
        CreateTableRequest request = new CreateTableRequest(tableMeta, tableOptions);
        request.setReservedThroughput(new ReservedThroughput(new CapacityUnit(0, 0)));
        client.createTable(request);
        log.info("create table:{}", tableName);
    }


    private boolean tableExists() {
        ListTableResponse listTableResponse = client.listTable();
        return listTableResponse.getTableNames().contains(tableName);
    }

    private void forEachAllData(PrimaryKeyValue memoryId, Consumer<Row> rowConsumer) {
        forEachAllData(memoryId, memoryId, rowConsumer);
    }

    private void forEachAllData(PrimaryKeyValue memoryIdStart, PrimaryKeyValue memoryIdEnd, Consumer<Row> rowConsumer) {
        RangeRowQueryCriteria rangeRowQueryCriteria = new RangeRowQueryCriteria(this.tableName);
        PrimaryKeyBuilder start = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        start.addPrimaryKeyColumn(this.pkName1, memoryIdStart);
        start.addPrimaryKeyColumn(this.pkName2, PrimaryKeyValue.INF_MIN);
        PrimaryKeyBuilder end = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        end.addPrimaryKeyColumn(this.pkName1, memoryIdEnd);
        end.addPrimaryKeyColumn(this.pkName2, PrimaryKeyValue.INF_MAX);
        rangeRowQueryCriteria.setInclusiveStartPrimaryKey(start.build());
        rangeRowQueryCriteria.setExclusiveEndPrimaryKey(end.build());
        rangeRowQueryCriteria.setMaxVersions(1);
        rangeRowQueryCriteria.setLimit(5000);
        rangeRowQueryCriteria.addColumnsToGet(Collections.singletonList(chatMessageFieldName));
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
}
