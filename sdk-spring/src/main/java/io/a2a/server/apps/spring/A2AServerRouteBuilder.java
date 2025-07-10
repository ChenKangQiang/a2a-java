package io.a2a.server.apps.spring;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.JSONRPCHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.PushNotifier;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.AgentCard;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Executor;

/**
 * 简化的 A2A 服务器构建器，帮助用户快速创建 A2AServerRoute
 */
public class A2AServerRouteBuilder {

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private QueueManager queueManager;

    @Autowired
    private PushNotifier pushNotifier;

    @Autowired
    private Executor executor;

    private AgentCard agentCard;

    private AgentExecutor agentExecutor;

    public A2AServerRouteBuilder() {

    }

    /**
     * 设置代理执行器
     */
    public A2AServerRouteBuilder agentExecutor(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
        return this;
    }

    public A2AServerRouteBuilder agentCard(AgentCard agentCard) {
        this.agentCard = agentCard;
        return this;
    }

    /**
     * 设置任务存储
     */
    public A2AServerRouteBuilder taskStore(TaskStore taskStore) {
        this.taskStore = taskStore;
        return this;
    }

    /**
     * 设置队列管理器
     */
    public A2AServerRouteBuilder queueManager(QueueManager queueManager) {
        this.queueManager = queueManager;
        return this;
    }

    /**
     * 设置推送通知器
     */
    public A2AServerRouteBuilder pushNotifier(PushNotifier pushNotifier) {
        this.pushNotifier = pushNotifier;
        return this;
    }

    /**
     * 设置自定义执行器
     */
    public A2AServerRouteBuilder executor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * 构建完整的 A2AServerRoute
     */
    public A2AServerRoute build() {
        RequestHandler requestHandler = new DefaultRequestHandler(agentExecutor, taskStore, queueManager, pushNotifier, executor);
        JSONRPCHandler jsonrpcHandler = new JSONRPCHandler(agentCard, requestHandler);
        return new A2AServerRoute(jsonrpcHandler, agentCard);
    }
}
