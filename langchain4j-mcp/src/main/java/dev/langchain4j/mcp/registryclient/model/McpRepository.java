package dev.langchain4j.mcp.registryclient.model;

public class McpRepository {
    private String id;
    private String source;
    private String subfolder;
    private String url;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getSubfolder() {
        return subfolder;
    }

    public void setSubfolder(final String subfolder) {
        this.subfolder = subfolder;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "McpRepository{" +
                "id='" + id + '\'' +
                ", source='" + source + '\'' +
                ", subfolder='" + subfolder + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
