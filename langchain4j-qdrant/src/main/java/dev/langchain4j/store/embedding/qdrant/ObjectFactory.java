package dev.langchain4j.store.embedding.qdrant;

import java.util.Map;
import java.util.stream.Collectors;

import io.qdrant.client.grpc.JsonWithInt.ListValue;
import io.qdrant.client.grpc.JsonWithInt.Value;
/**
 * Utility methods for building Java objects from io.qdrant.client.grpc.JsonWithInt.Value.
 *
 * @author Anush Shetty
 * @since 0.8.1
 */
class ObjectFactory {

	private ObjectFactory() {
	}

    public static Object object(Value value) {
        return switch (value.getKindCase()) {
            case INTEGER_VALUE -> value.getIntegerValue();
            case STRING_VALUE -> value.getStringValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case BOOL_VALUE -> value.getBoolValue();
            case LIST_VALUE -> object(value.getListValue());
            case STRUCT_VALUE -> objectMap(value.getStructValue().getFieldsMap());
            case NULL_VALUE -> null;
            default -> throw new IllegalArgumentException("Unknown value type: " + value.getKindCase());
        };
	}

	private static Map<String, Object> objectMap(Map<String, Value> payload) {
		return payload.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> object(e.getValue())));
	}

	private static Object object(ListValue listValue) {
		return listValue.getValuesList().stream().map(ObjectFactory::object).collect(Collectors.toList());
	}

}