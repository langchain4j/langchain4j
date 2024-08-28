package dev.langchain4j.store.embedding.qdrant;

import static io.qdrant.client.ValueFactory.list;
import static io.qdrant.client.ValueFactory.nullValue;
import static io.qdrant.client.ValueFactory.value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.qdrant.client.grpc.JsonWithInt.Struct;
import io.qdrant.client.grpc.JsonWithInt.Value;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class to convert Map<String, Object> into Map<String,
 * io.qdrant.client.grpc.JsonWithInt.Value>;
 */
// We need to do some extra work to convert com.google.protobuf.Value to
// io.qdrant.client.grpc.JsonWithInt.Value
// Because, com.google.protobuf.util.JsonFormat.parser() only allows conversion to
// com.google.protobuf.Value.
final class ValueMapFactory {
  private ValueMapFactory() {}

  static final Map<String, Value> valueMap(Object object)
      throws InvalidProtocolBufferException, JsonProcessingException {

    ObjectMapper mapper = new ObjectMapper();
    return parse(mapper.writeValueAsString(object));
  }

  static final Map<String, Value> parse(String json) throws InvalidProtocolBufferException {
    com.google.protobuf.Struct.Builder structBuilder = com.google.protobuf.Struct.newBuilder();

    JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);

    return parse(structBuilder.build().getFieldsMap());
  }

  static final Map<String, Value> parse(Map<String, com.google.protobuf.Value> map) {
    return map.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> parse(e.getValue())));
  }

  static final Value parse(com.google.protobuf.Value val) {
    switch (val.getKindCase()) {
      case NULL_VALUE:
        return nullValue();

      case BOOL_VALUE:
        return value(val.getBoolValue());

      case STRING_VALUE:
        return value(val.getStringValue());

      case NUMBER_VALUE:
        Double numberValue = val.getNumberValue();
        return value(numberValue);

      case STRUCT_VALUE:
        Struct.Builder structBuilder = Struct.newBuilder();
        val.getStructValue()
            .getFieldsMap()
            .forEach(
                (key, value) -> {
                  structBuilder.putFields(key, parse(value));
                });
        return Value.newBuilder().setStructValue(structBuilder).build();

      case LIST_VALUE:
        List<Value> values =
            val.getListValue().getValuesList().stream()
                .map(ValueMapFactory::parse)
                .collect(Collectors.toList());

        return list(values);

      case KIND_NOT_SET:
        return Value.newBuilder().build();

      default:
        throw new IllegalArgumentException("Unsupported value type: " + val.getKindCase());
    }
  }
}