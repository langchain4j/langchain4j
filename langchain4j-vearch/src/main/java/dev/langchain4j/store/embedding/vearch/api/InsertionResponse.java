package dev.langchain4j.store.embedding.vearch.api;

public class InsertionResponse {

    private Integer code;
    private String msg;
    private Integer total;
    private InsertedDocument documentIds;

    public static class InsertedDocument {

        private String _id;
        private Integer status;
        private String error;

        public String get_id() {
            return _id;
        }

        public void set_id(String _id) {
            this._id = _id;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
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

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public InsertedDocument getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(InsertedDocument documentIds) {
        this.documentIds = documentIds;
    }
}
