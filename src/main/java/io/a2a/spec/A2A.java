package io.a2a.spec;

import static io.a2a.util.Utils.unmarshalFrom;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * Constants and utility methods related to the A2A protocol.
 */
public class A2A {

    public static final String CANCEL_TASK_METHOD = "tasks/cancel";
    public static final String GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD = "tasks/pushNotificationConfig/get";
    public static final String GET_TASK_METHOD = "tasks/get";
    public static final String SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD = "tasks/pushNotificationConfig/set";
    public static final String SEND_TASK_RESUBSCRIPTION_METHOD = "tasks/resubscribe";
    public static final String SEND_STREAMING_MESSAGE_METHOD = "message/stream";
    public static final String SEND_MESSAGE_METHOD = "message/send";

    public static final String JSONRPC_VERSION = "2.0";

    public static final String AGENT_CARD_REQUEST = ".well-known/agent.json";

    private static final TypeReference<AgentCard> AGENT_CARD_TYPE_REFERENCE = new TypeReference<>() {};

    public static String getRequestEndpoint(String agentUrl, String request) {
        return agentUrl.endsWith("/") ? agentUrl + request : agentUrl + "/" + request;
    }

    /**
     * Convert the given text to a user message.
     *
     * @param text the message text
     * @return the user message
     */
    public static Message toUserMessage(String text) {
        return toMessage(text, Message.Role.USER, null);
    }

    /**
     * Convert the given text to a user message.
     *
     * @param text the message text
     * @param messageId the message ID to use
     * @return the user message
     */
    public static Message toUserMessage(String text, String messageId) {
        return toMessage(text, Message.Role.USER, messageId);
    }

    /**
     * Convert the given text to an agent message.
     *
     * @param text the message text
     * @return the agent message
     */
    public static Message toAgentMessage(String text) {
        return toMessage(text, Message.Role.AGENT, null);
    }

    /**
     * Convert the given text to an agent message.
     *
     * @param text the message text
     * @param messageId the message ID to use
     * @return the agent message
     */
    public static Message toAgentMessage(String text, String messageId) {
        return toMessage(text, Message.Role.AGENT, messageId);
    }


    private static Message toMessage(String text, Message.Role role, String messageId) {
        Message.Builder messageBuilder = new Message.Builder()
                .role(role)
                .parts(Collections.singletonList(new TextPart(text)));
        if (messageId != null) {
            messageBuilder.messageId(messageId);
        }
        return messageBuilder.build();
    }

    /**
     * Get the agent card for an A2A agent.
     *
     * @param agentUrl the base URL for the agent whose agent card we want to retrieve
     * @return the agent card
     * @throws A2AServerException if the agent card cannot be retrieved for any reason
     */
    public static AgentCard getAgentCard(String agentUrl) throws A2AServerException {
        return getAgentCard(new OkHttpClient(), agentUrl);
    }

    /**
     * Get the agent card for an A2A agent.
     *
     * @param httpClient the http client to use
     * @param agentUrl the base URL for the agent whose agent card we want to retrieve
     * @return the agent card
     * @throws A2AServerException if the agent card cannot be retrieved for any reason
     */
    public static AgentCard getAgentCard(OkHttpClient httpClient, String agentUrl) throws A2AServerException {
        return getAgentCard(httpClient, agentUrl, null, null);
    }

    /**
     * Get the agent card for an A2A agent.
     *
     * @param agentUrl the base URL for the agent whose agent card we want to retrieve
     * @param relativeCardPath optional path to the agent card endpoint relative to the base
     *                         agent URL, defaults to ".well-known/agent.json"
     * @param authHeaders the HTTP authentication headers to use
     * @return the agent card
     * @throws A2AServerException if the agent card cannot be retrieved for any reason
     */
    public static AgentCard getAgentCard(String agentUrl, String relativeCardPath, Map<String, String> authHeaders) throws A2AServerException {
        return getAgentCard(new OkHttpClient(), agentUrl, relativeCardPath, authHeaders);
    }

    /**
     * Get the agent card for an A2A agent.
     *
     * @param httpClient the http client to use
     * @param agentUrl the base URL for the agent whose agent card we want to retrieve
     * @param relativeCardPath optional path to the agent card endpoint relative to the base
     *                         agent URL, defaults to ".well-known/agent.json"
     * @param authHeaders the HTTP authentication headers to use
     * @return the agent card
     * @throws A2AServerException if the agent card cannot be retrieved for any reason
     */
    public static AgentCard getAgentCard(OkHttpClient httpClient, String agentUrl, String relativeCardPath, Map<String, String> authHeaders) throws A2AServerException {
        if (relativeCardPath == null || relativeCardPath.isEmpty()) {
            relativeCardPath = AGENT_CARD_REQUEST;
        } else {
            if (relativeCardPath.startsWith("/")) {
                relativeCardPath = relativeCardPath.substring(1);
            }
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(getRequestEndpoint(agentUrl, relativeCardPath))
                .addHeader("Content-Type", "application/json")
                .get();

        if (authHeaders != null) {
            for (Map.Entry<String, String> entry : authHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (! response.isSuccessful()) {
                throw new A2AServerException("Failed to obtain agent card: " + response.code());
            }
            String responseBody = response.body().string();
            return unmarshalFrom(responseBody, AGENT_CARD_TYPE_REFERENCE);
        } catch (IOException e) {
            throw new A2AServerException("Failed to obtain agent card", e);
        }
    }

}
