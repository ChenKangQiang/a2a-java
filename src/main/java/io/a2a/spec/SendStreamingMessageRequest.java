package io.a2a.spec;

import static io.a2a.spec.A2A.JSONRPC_VERSION;
import static io.a2a.spec.A2A.SEND_STREAMING_MESSAGE_REQUEST;
import static io.a2a.util.Utils.OBJECT_MAPPER;
import static io.a2a.util.Utils.defaultIfNull;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.a2a.util.Assert;

/**
 * Used to initiate a task with streaming.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SendStreamingMessageRequest extends JSONRPCRequest {

    @JsonCreator
    public SendStreamingMessageRequest(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id,
                                       @JsonProperty("method") String method, @JsonProperty("params") MessageSendParams params) {
        Assert.checkNotNullParam("method", method);
        Assert.checkNotNullParam("params", params);

        if (! method.equals(SEND_STREAMING_MESSAGE_REQUEST)) {
            throw new IllegalArgumentException("Invalid SendStreamingMessageRequest method");
        }

        Map<String, Object> paramsMap = OBJECT_MAPPER.convertValue(params, Map.class);
        this.jsonrpc = defaultIfNull(jsonrpc, JSONRPC_VERSION);
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.method = method;
        this.params = paramsMap;
    }
}
