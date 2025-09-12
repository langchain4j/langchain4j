package dev.langchain4j.model.googleai;

class GoogleAiListCachedContentsRequest {

    private int pageSize;

    private String pageToken;

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(final int pageSize) {
        this.pageSize = pageSize;
    }

    public String getPageToken() {
        return pageToken;
    }

    public void setPageToken(final String pageToken) {
        this.pageToken = pageToken;
    }

    @Override
    public String toString() {
        return "GoogleAiListCachedContentsRequest{" +
                "pageSize=" + pageSize +
                ", pageToken='" + pageToken + '\'' +
                '}';
    }

}
