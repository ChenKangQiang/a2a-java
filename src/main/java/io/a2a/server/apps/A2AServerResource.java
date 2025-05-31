package io.a2a.server.apps;

import java.util.concurrent.Flow;

import io.a2a.server.requesthandlers.JSONRPCHandler;
import io.a2a.spec.AgentCard;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.ExtendedAgentCard;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.JSONErrorResponse;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCRequest;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.TaskResubscriptionRequest;
import io.a2a.spec.UnsupportedOperationError;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@Path("/")
public class A2AServerResource {

    @Inject
    JSONRPCHandler jsonRpcHandler;

    @Inject
    @ExtendedAgentCard
    Instance<AgentCard> extendedAgentCard;

    /**
     * Handles incoming POST requests to the main A2A endpoint. Dispatches the
     * request to the appropriate JSON-RPC handler method and returns the response.
     *
     * @param request the JSON-RPC request
     * @return the JSON-RPC response which may be an error response
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleNonStreamingRequests(JSONRPCRequest<?> request) {
        if (request instanceof SendStreamingMessageRequest || request instanceof TaskResubscriptionRequest) {
            JSONRPCResponse<?> response = generateErrorResponse(request, new UnsupportedOperationError());
            return Response.ok(response).type(MediaType.APPLICATION_JSON).build();
        }
        return processNonStreamingRequest(request);
    }

    /**
     * Handles incoming POST requests to the main A2A endpoint that involve Server-Sent Events (SSE).
     * Dispatches the request to the appropriate JSON-RPC handler method and returns the response.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response handleStreamingRequests(JSONRPCRequest<?> request, @Context SseEventSink sseEventSink, @Context Sse sse) {
        if (!(request instanceof SendStreamingMessageRequest) && !(request instanceof TaskResubscriptionRequest)) {
            JSONRPCResponse<?> response = generateErrorResponse(request, new UnsupportedOperationError());
            return Response.ok(response).type(MediaType.APPLICATION_JSON).build();
        }
        return processStreamingRequest(request, sseEventSink, sse);
    }

    /**
     * Handles incoming GET requests to the agent card endpoint.
     * Returns the agent card in JSON format.
     *
     * @return the agent card
     */
    @GET
    @Path("/.well-known/agent.json")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentCard getAgentCard() {
        return jsonRpcHandler.getAgentCard();
    }

    /**
     * Handles incoming GET requests to the authenticated extended agent card endpoint.
     * Returns the agent card in JSON format.
     *
     * @return the authenticated extended agent card
     */
    @GET
    @Path("/agent/authenticatedExtendedCard")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuthenticatedExtendedAgentCard() {
        // TODO need to add authentication for this endpoint
        // https://github.com/fjuma/a2a-java-sdk/issues/77
        if (! jsonRpcHandler.getAgentCard().supportsAuthenticatedExtendedCard()) {
            JSONErrorResponse errorResponse = new JSONErrorResponse("Extended agent card not supported or not enabled.");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse).build();
        }
        if (! extendedAgentCard.isResolvable()) {
            JSONErrorResponse errorResponse = new JSONErrorResponse("Authenticated extended agent card is supported but not configured on the server.");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse).build();
        }
        return Response.ok(extendedAgentCard.get())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response processNonStreamingRequest(JSONRPCRequest<?> request) {
        JSONRPCResponse<?> response;
        if (request instanceof GetTaskRequest) {
            response = jsonRpcHandler.onGetTask((GetTaskRequest) request);
        } else if (request instanceof CancelTaskRequest) {
            response = jsonRpcHandler.onCancelTask((CancelTaskRequest) request);
        } else if (request instanceof SetTaskPushNotificationConfigRequest) {
            response = jsonRpcHandler.setPushNotification((SetTaskPushNotificationConfigRequest) request);
        } else if (request instanceof GetTaskPushNotificationConfigRequest) {
            response = jsonRpcHandler.getPushNotification((GetTaskPushNotificationConfigRequest) request);
        } else if (request instanceof SendMessageRequest) {
            response = jsonRpcHandler.onMessageSend((SendMessageRequest) request);
        } else {
            response = generateErrorResponse(request, new UnsupportedOperationError());
        }
        return Response.ok(response).type(MediaType.APPLICATION_JSON).build();
    }

    private Response processStreamingRequest(JSONRPCRequest<?> request, SseEventSink sseEventSink, Sse sse) {
        Flow.Publisher<? extends JSONRPCResponse<?>> publisher;
        if (request instanceof SendStreamingMessageRequest) {
            publisher = jsonRpcHandler.onMessageSendStream((SendStreamingMessageRequest) request);
            return handleStreamingResponse(publisher, sseEventSink, sse);
        } else if (request instanceof TaskResubscriptionRequest) {
            publisher = jsonRpcHandler.onResubscribeToTask((TaskResubscriptionRequest) request);
            return handleStreamingResponse(publisher, sseEventSink, sse);
        } else {
            JSONRPCResponse<?> response = generateErrorResponse(request, new UnsupportedOperationError());
            return Response.ok(response).type(MediaType.APPLICATION_JSON).build();
        }
    }

    private Response handleStreamingResponse(Flow.Publisher<? extends JSONRPCResponse<?>> publisher, SseEventSink sseEventSink, Sse sse) {
        publisher.subscribe(new Flow.Subscriber<JSONRPCResponse<?>>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(JSONRPCResponse<?> item) {
                sseEventSink.send(sse.newEventBuilder()
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .data(item)
                        .build());
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO
                sseEventSink.close();
            }

            @Override
            public void onComplete() {
                sseEventSink.close();
            }
        });

        return Response.ok().type(MediaType.SERVER_SENT_EVENTS).build();
    }

    private JSONRPCResponse<?> generateErrorResponse(JSONRPCRequest<?> request, JSONRPCError error) {
        return new JSONRPCErrorResponse(request.getId(), error);
    }
}

