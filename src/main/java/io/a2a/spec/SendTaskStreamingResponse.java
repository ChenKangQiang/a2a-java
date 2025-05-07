package io.a2a.spec;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.a2a.util.Assert;

/**
 * The response after receiving a request to initiate a task with streaming.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SendTaskStreamingResponse extends JSONRPCResponse {

    @JsonCreator
    public SendTaskStreamingResponse(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id,
                                     @JsonProperty("result") Object result, @JsonProperty("error") JSONRPCError error) {
        Assert.checkNotNullParam("jsonrpc", jsonrpc);
        if (result != null && ! (result instanceof TaskStatusUpdateEvent) && ! (result instanceof TaskArtifactUpdateEvent)) {
            throw new IllegalArgumentException("Invalid result");
        }

        this.jsonrpc = jsonrpc;
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.result = result;
        this.error = error;
    }
}
