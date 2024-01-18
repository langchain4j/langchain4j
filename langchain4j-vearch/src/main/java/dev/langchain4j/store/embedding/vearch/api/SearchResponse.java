package dev.langchain4j.store.embedding.vearch.api;

import java.util.List;
import java.util.Map;

public class SearchResponse {

    private Integer code;
    private String msg;
    private List<SearchedDocument> documents;

    public static class SearchedDocument {

        private String _id;
        private Double _score;
        private Map<String, Object> _source;

        public String get_id() {
            return _id;
        }

        public void set_id(String _id) {
            this._id = _id;
        }

        public Double get_score() {
            return _score;
        }

        public void set_score(Double _score) {
            this._score = _score;
        }

        public Map<String, Object> get_source() {
            return _source;
        }

        public void set_source(Map<String, Object> _source) {
            this._source = _source;
        }
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public List<SearchedDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<SearchedDocument> documents) {
        this.documents = documents;
    }
}
