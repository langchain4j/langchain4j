package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@Experimental
public final class BatchPagination {

    @Nullable
    private final Integer pageSize;

    @Nullable
    private final String pageToken;

    public BatchPagination(@Nullable Integer pageSize, @Nullable String pageToken) {
        this.pageSize = pageSize;
        this.pageToken = pageToken;
    }

    @Nullable
    public Integer pageSize() {
        return pageSize;
    }

    @Nullable
    public String pageToken() {
        return pageToken;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BatchPagination that = (BatchPagination) o;
        return Objects.equals(pageSize, that.pageSize) && Objects.equals(pageToken, that.pageToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSize, pageToken);
    }

    @Override
    public String toString() {
        return "BatchPagination{" +
                "pageSize=" + pageSize +
                ", pageToken='" + pageToken + '\'' +
                '}';
    }
}
