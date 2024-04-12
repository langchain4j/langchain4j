package dev.langchain4j.store.embedding.milvus.parameter;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.milvus.param.IndexType;

import java.util.Arrays;
import java.util.List;


public abstract class IndexParam {
    // Gson instance exclude indexTypes field, because it's not necessary to serialize, just a functional filed
    private final static Gson gson = new GsonBuilder().setExclusionStrategies(
            new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return "indexTypes".equals(f.getName());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            }
    ).create();

    private final static IndexType[] NullableIndexTypes = new IndexType[]{
            IndexType.FLAT,
            IndexType.BIN_FLAT,
            IndexType.DISKANN,
    };
    public final static IndexParam EMPTY_INSTANCE = new IndexParam(NullableIndexTypes) {
        @Override
        public String toExtraParam() {
            return "{}";
        }
    };

    private final List<IndexType> indexTypes;

    protected IndexParam(IndexType... indexTypes) {
        this.indexTypes = Arrays.asList(indexTypes);
    }

    public static boolean isIndexParamNullable(IndexType indexType) {
        return Arrays.asList(NullableIndexTypes).contains(indexType);
    }

    /**
     * convert to JSON string
     *
     * @return extra param json
     */
    public String toExtraParam() {
        return gson.toJson(this);
    }

    public boolean support(IndexType indexType) {
        return this.indexTypes.contains(indexType);
    }

}
