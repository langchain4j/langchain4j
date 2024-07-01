package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import oracle.jdbc.driver.json.tree.OracleJsonStringImpl;
import oracle.sql.VECTOR;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

class OracleDataAdapter {
    float[] toFloatArray(double[] vector) {
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    Map<String, Object> toMap(OracleJsonObject ojson) {
        Map<String, Object> map = new HashMap<>();
        for (String key : ojson.keySet()) {
            OracleJsonValue jsonValue = ojson.get(key);
            if (jsonValue instanceof OracleJsonStringImpl) {
                map.put(key, ((OracleJsonStringImpl) jsonValue).getString());
            } else {
                map.put(key, ojson.get(key));
            }
        }
        return map;
    }

    VECTOR toVECTOR(Embedding embedding, boolean normalizeVector) throws SQLException {
        float[] vector = embedding.vector();
        if (normalizeVector) {
            vector = normalize(vector);
        }
        return VECTOR.ofFloat64Values(vector);
    }

    OracleJsonObject toJSON(Map<String, Object> metadata) {
        OracleJsonObject ojson = new OracleJsonFactory().createObject();
        if (metadata == null) {
            return ojson;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            final Object o = entry.getValue();
            if (o instanceof String) {
                ojson.put(entry.getKey(), (String) o);
            }
            else if (o instanceof Integer) {
                ojson.put(entry.getKey(), (Integer) o);
            }
            else if (o instanceof Float) {
                ojson.put(entry.getKey(), (Float) o);
            }
            else if (o instanceof Double) {
                ojson.put(entry.getKey(), (Double) o);
            }
            else if (o instanceof Boolean) {
                ojson.put(entry.getKey(), (Boolean) o);
            }
            ojson.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return ojson;
    }

    private float[] normalize(float[] v) {
        double squaredSum = 0d;

        for (float e : v) {
            squaredSum += e * e;
        }

        final float magnitude = (float) Math.sqrt(squaredSum);

        if (magnitude > 0) {
            final float multiplier = 1f / magnitude;
            final int length = v.length;
            for (int i = 0; i < length; i++) {
                v[i] *= multiplier;
            }
        }

        return v;
    }
}
