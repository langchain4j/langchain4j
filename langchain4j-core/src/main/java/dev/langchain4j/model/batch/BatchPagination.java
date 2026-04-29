package dev.langchain4j.model.batch;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
    public Integer getPageSize() {
        return pageSize;
    }

    @Nullable
    public String getPageToken() {
        return pageToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final BatchPagination that)) {
            return false;
        }
        return Objects.equals(pageSize, that.pageSize) && Objects.equals(pageToken, that.pageToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSize, pageToken);
    }

    @Override
    public String toString() {
        return "BatchPagination{" + "pageSize=" + pageSize + ", pageToken='" + pageToken + '\'' + '}';
    }
}
