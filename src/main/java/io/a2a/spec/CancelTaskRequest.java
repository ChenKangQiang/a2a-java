package io.a2a.spec;

import static io.a2a.spec.A2A.CANCEL_TASK_REQUEST;
import static io.a2a.spec.A2A.JSONRPC_VERSION;
import static io.a2a.util.Utils.defaultIfNull;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.a2a.util.Assert;

/**
 * A request that can be used to cancel a task.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CancelTaskRequest extends JSONRPCRequest<TaskIdParams> {

    @JsonCreator
    public CancelTaskRequest(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id,
                             @JsonProperty("method") String method, @JsonProperty("params") TaskIdParams params) {
        Assert.checkNotNullParam("params", params);

        this.method = defaultIfNull(method, CANCEL_TASK_REQUEST);
        if (!this.method.equals(CANCEL_TASK_REQUEST)) {
            throw new IllegalArgumentException("Invalid CancelTaskRequest method");
        }

        this.jsonrpc = defaultIfNull(jsonrpc, JSONRPC_VERSION);;
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.params = params;
    }

    public CancelTaskRequest(Object id, TaskIdParams params) {
        this(null, id, null, params);
    }

    public static class Builder {
        private String jsonrpc;
        private Object id;
        private String method;
        private TaskIdParams params;

        public CancelTaskRequest.Builder jsonrpc(String jsonrpc) {
            this.jsonrpc = jsonrpc;
            return this;
        }

        public CancelTaskRequest.Builder id(Object id) {
            this.id = id;
            return this;
        }

        public CancelTaskRequest.Builder method(String method) {
            this.method = method;
            return this;
        }

        public CancelTaskRequest.Builder params(TaskIdParams params) {
            this.params = params;
            return this;
        }

        public CancelTaskRequest build() {
            return new CancelTaskRequest(jsonrpc, id, method, params);
        }
    }
}
