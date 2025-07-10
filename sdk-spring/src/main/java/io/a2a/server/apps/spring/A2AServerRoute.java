package io.a2a.server.apps.spring;

import java.lang.reflect.Method;
import java.util.concurrent.Flow;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.a2a.server.requesthandlers.JSONRPCHandler;
import io.a2a.spec.AgentCard;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.IdJsonMappingException;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidParamsJsonMappingException;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONErrorResponse;
import io.a2a.spec.JSONParseError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCRequest;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.MethodNotFoundError;
import io.a2a.spec.MethodNotFoundJsonMappingException;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.StreamingJSONRPCRequest;
import io.a2a.spec.TaskResubscriptionRequest;
import io.a2a.spec.UnsupportedOperationError;
import io.a2a.util.Utils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Flux;

/**
 * Spring Boot REST controller for A2A (Agent2Agent) protocol endpoints.
 * Provides endpoints for JSON-RPC communication and agent card retrieval.
 */
public class A2AServerRoute implements InitializingBean {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final JSONRPCHandler jsonRpcHandler;

    private final AgentCard extendedAgentCard;

    private final String basePath;

    public A2AServerRoute(JSONRPCHandler jsonRpcHandler, AgentCard extendedAgentCard) {
        this.jsonRpcHandler = jsonRpcHandler;
        this.extendedAgentCard = extendedAgentCard;
        this.basePath = AgentCardPathResolver.resolveBasePath(jsonRpcHandler.getAgentCard());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registerMappings();
    }

    /**
     * Handles incoming POST requests to the main A2A endpoint.
     * Dispatches the request to the appropriate JSON-RPC handler method.
     *
     * @param requestBody the JSON-RPC request body as string
     * @return the JSON-RPC response
     */
    @ResponseBody
    public ResponseEntity<JSONRPCResponse<?>> handleNonStreamingRequests(@RequestBody String requestBody) {
        try {
            NonStreamingJSONRPCRequest<?> request = Utils.OBJECT_MAPPER.readValue(requestBody, NonStreamingJSONRPCRequest.class);
            JSONRPCResponse<?> response = processNonStreamingRequest(request);
            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            JSONRPCErrorResponse error = handleError(e);
            return ResponseEntity.ok(error);
        } catch (Throwable t) {
            JSONRPCErrorResponse error = new JSONRPCErrorResponse(new io.a2a.spec.InternalError(t.getMessage()));
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Handles incoming POST requests for streaming operations using Server-Sent Events (SSE).
     * Dispatches the request to the appropriate JSON-RPC handler method.
     *
     * @param requestBody the JSON-RPC request body as string
     * @return a Flux of ServerSentEvent containing the streaming responses
     */
    @ResponseBody
    public Flux<ServerSentEvent<JSONRPCResponse<?>>> handleStreamingRequests(@RequestBody String requestBody) {
        try {
            StreamingJSONRPCRequest<?> request = Utils.OBJECT_MAPPER.readValue(requestBody, StreamingJSONRPCRequest.class);
            return processStreamingRequest(request);
        } catch (JsonProcessingException e) {
            JSONRPCErrorResponse error = handleError(e);
            return Flux.just(ServerSentEvent.<JSONRPCResponse<?>>builder()
                    .data(error)
                    .build());
        } catch (Throwable t) {
            JSONRPCErrorResponse error = new JSONRPCErrorResponse(new io.a2a.spec.InternalError(t.getMessage()));
            return Flux.just(ServerSentEvent.<JSONRPCResponse<?>>builder()
                    .data(error)
                    .build());
        }
    }

    /**
     * Handles incoming GET requests to the agent card endpoint.
     * Returns the agent card in JSON format.
     *
     * @return the agent card
     */
    @ResponseBody
    public AgentCard getAgentCard() {
        return jsonRpcHandler.getAgentCard();
    }

    /**
     * Handles incoming GET requests to the authenticated extended agent card endpoint.
     * Returns the extended agent card in JSON format.
     *
     * @return the authenticated extended agent card
     */
    @ResponseBody
    public ResponseEntity<?> getAuthenticatedExtendedAgentCard() {
        // TODO: Add authentication for this endpoint
        // https://github.com/a2aproject/a2a-java/issues/77
        if (!jsonRpcHandler.getAgentCard().supportsAuthenticatedExtendedCard()) {
            JSONErrorResponse errorResponse = new JSONErrorResponse("Extended agent card not supported or not enabled.");
            return ResponseEntity.status(404).body(errorResponse);
        }
        if (extendedAgentCard == null) {
            JSONErrorResponse errorResponse = new JSONErrorResponse("Authenticated extended agent card is supported but not configured on the server.");
            return ResponseEntity.status(404).body(errorResponse);
        }
        return ResponseEntity.ok(extendedAgentCard);
    }

    private JSONRPCResponse<?> processNonStreamingRequest(NonStreamingJSONRPCRequest<?> request) {
        if (request instanceof GetTaskRequest) {
            return jsonRpcHandler.onGetTask((GetTaskRequest) request);
        } else if (request instanceof CancelTaskRequest) {
            return jsonRpcHandler.onCancelTask((CancelTaskRequest) request);
        } else if (request instanceof SetTaskPushNotificationConfigRequest) {
            return jsonRpcHandler.setPushNotification((SetTaskPushNotificationConfigRequest) request);
        } else if (request instanceof GetTaskPushNotificationConfigRequest) {
            return jsonRpcHandler.getPushNotification((GetTaskPushNotificationConfigRequest) request);
        } else if (request instanceof SendMessageRequest) {
            return jsonRpcHandler.onMessageSend((SendMessageRequest) request);
        } else {
            return generateErrorResponse(request, new UnsupportedOperationError());
        }
    }

    private Flux<ServerSentEvent<JSONRPCResponse<?>>> processStreamingRequest(StreamingJSONRPCRequest<?> request) {
        Flow.Publisher<? extends JSONRPCResponse<?>> publisher;
        if (request instanceof SendStreamingMessageRequest) {
            publisher = jsonRpcHandler.onMessageSendStream((SendStreamingMessageRequest) request);
        } else if (request instanceof TaskResubscriptionRequest) {
            publisher = jsonRpcHandler.onResubscribeToTask((TaskResubscriptionRequest) request);
        } else {
            return Flux.just(ServerSentEvent.<JSONRPCResponse<?>>builder()
                    .data(generateErrorResponse(request, new UnsupportedOperationError()))
                    .build());
        }

        return Flux.create(sink -> {
            publisher.subscribe(new Flow.Subscriber<JSONRPCResponse<?>>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(JSONRPCResponse<?> item) {
                    sink.next(ServerSentEvent.<JSONRPCResponse<?>>builder()
                            .data(item)
                            .build());
                }

                @Override
                public void onError(Throwable throwable) {
                    sink.error(throwable);
                }

                @Override
                public void onComplete() {
                    sink.complete();
                }
            });
        });
    }

    private JSONRPCErrorResponse handleError(JsonProcessingException exception) {
        Object id = null;
        JSONRPCError jsonRpcError = null;
        if (exception.getCause() instanceof JsonParseException) {
            jsonRpcError = new JSONParseError();
        } else if (exception instanceof com.fasterxml.jackson.core.io.JsonEOFException) {
            jsonRpcError = new JSONParseError(exception.getMessage());
        } else if (exception instanceof MethodNotFoundJsonMappingException err) {
            id = err.getId();
            jsonRpcError = new MethodNotFoundError();
        } else if (exception instanceof InvalidParamsJsonMappingException err) {
            id = err.getId();
            jsonRpcError = new InvalidParamsError();
        } else if (exception instanceof IdJsonMappingException err) {
            id = err.getId();
            jsonRpcError = new InvalidRequestError();
        } else {
            jsonRpcError = new InvalidRequestError();
        }
        return new JSONRPCErrorResponse(id, jsonRpcError);
    }

    private JSONRPCResponse<?> generateErrorResponse(JSONRPCRequest<?> request, JSONRPCError error) {
        return new JSONRPCErrorResponse(request.getId(), error);
    }

    private void registerMappings() throws NoSuchMethodException {
        // 注册非流式请求处理端点
        RequestMappingInfo nonStreamingInfo = RequestMappingInfo
                .paths(basePath)
                .methods(org.springframework.web.bind.annotation.RequestMethod.POST)
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();

        Method nonStreamingMethod = A2AServerRoute.class.getDeclaredMethod("handleNonStreamingRequests", String.class);
        requestMappingHandlerMapping.registerMapping(nonStreamingInfo, this, nonStreamingMethod);

        // 注册流式请求处理端点
        RequestMappingInfo streamingInfo = RequestMappingInfo
                .paths(basePath)
                .methods(org.springframework.web.bind.annotation.RequestMethod.POST)
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();

        Method streamingMethod = A2AServerRoute.class.getDeclaredMethod("handleStreamingRequests", String.class);
        requestMappingHandlerMapping.registerMapping(streamingInfo, this, streamingMethod);

        // 注册代理卡片端点
        RequestMappingInfo agentCardInfo = RequestMappingInfo
                .paths(basePath + "/.well-known/agent.json")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();

        Method agentCardMethod = A2AServerRoute.class.getDeclaredMethod("getAgentCard");
        requestMappingHandlerMapping.registerMapping(agentCardInfo, this, agentCardMethod);

        // 注册扩展代理卡片端点
        RequestMappingInfo extendedCardInfo = RequestMappingInfo
                .paths(basePath + "/agent/authenticatedExtendedCard")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();

        Method extendedCardMethod = A2AServerRoute.class.getDeclaredMethod("getAuthenticatedExtendedAgentCard");
        requestMappingHandlerMapping.registerMapping(extendedCardInfo, this, extendedCardMethod);
    }
}
