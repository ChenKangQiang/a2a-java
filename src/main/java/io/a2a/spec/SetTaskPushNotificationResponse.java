package io.a2a.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The response after receiving a set task push notification request.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SetTaskPushNotificationResponse extends JSONRPCResponse {

    @JsonCreator
    public SetTaskPushNotificationResponse(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id,
                                           @JsonProperty("result") TaskPushNotificationConfig result,
                                           @JsonProperty("error") JSONRPCError error) {
        super(jsonrpc, id, result, error);
    }
}
