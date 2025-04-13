package dev.langchain4j.model.workersai.client;

import java.util.List;

/**
 * Multiple models leverage the same output format, so we can use this class to parse the response.
 *
 * @param <T> Type of the result.
 */
public class ApiResponse<T> {

    /**
     * Result of the API call.
     */
    private T result;

    /**
     * Success of the API call.
     */
    private boolean success;

    /**
     * Errors of the API call.
     */
    private List<Error> errors;

    /**
     * Messages of the API call.
     */
    private List<String> messages;

    /**
     * Default constructor.
     */
    public ApiResponse() {
    }

    public T getResult() {
        return this.result;
    }

    public boolean isSuccess() {
        return this.success;
    }

    public List<Error> getErrors() {
        return this.errors;
    }

    public List<String> getMessages() {
        return this.messages;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ApiResponse)) return false;
        final ApiResponse<?> other = (ApiResponse<?>) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$result = this.getResult();
        final Object other$result = other.getResult();
        if (this$result == null ? other$result != null : !this$result.equals(other$result)) return false;
        if (this.isSuccess() != other.isSuccess()) return false;
        final Object this$errors = this.getErrors();
        final Object other$errors = other.getErrors();
        if (this$errors == null ? other$errors != null : !this$errors.equals(other$errors)) return false;
        final Object this$messages = this.getMessages();
        final Object other$messages = other.getMessages();
        if (this$messages == null ? other$messages != null : !this$messages.equals(other$messages)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ApiResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $result = this.getResult();
        result = result * PRIME + ($result == null ? 43 : $result.hashCode());
        result = result * PRIME + (this.isSuccess() ? 79 : 97);
        final Object $errors = this.getErrors();
        result = result * PRIME + ($errors == null ? 43 : $errors.hashCode());
        final Object $messages = this.getMessages();
        result = result * PRIME + ($messages == null ? 43 : $messages.hashCode());
        return result;
    }

    public String toString() {
        return "ApiResponse(result=" + this.getResult() + ", success=" + this.isSuccess() + ", errors=" + this.getErrors() + ", messages=" + this.getMessages() + ")";
    }

    /**
     * Error class.
     */
    public static class Error {
        /**
         * Message of the error.
         */
        private String message;
        /**
         * Code of the error.
         */
        private int code;

        /**
         * Default constructor.
         */
        public Error() {
        }

        public String getMessage() {
            return this.message;
        }

        public int getCode() {
            return this.code;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof ApiResponse.Error)) return false;
            final Error other = (Error) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$message = this.getMessage();
            final Object other$message = other.getMessage();
            if (this$message == null ? other$message != null : !this$message.equals(other$message)) return false;
            if (this.getCode() != other.getCode()) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof ApiResponse.Error;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $message = this.getMessage();
            result = result * PRIME + ($message == null ? 43 : $message.hashCode());
            result = result * PRIME + this.getCode();
            return result;
        }

        public String toString() {
            return "ApiResponse.Error(message=" + this.getMessage() + ", code=" + this.getCode() + ")";
        }
    }

}
