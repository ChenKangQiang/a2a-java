package io.a2a.server.requesthandlers;

import static io.a2a.util.AsyncUtils.createTubeConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.Event;
import io.a2a.server.events.EventConsumer;
import io.a2a.server.events.EventQueue;
import io.a2a.server.events.QueueManager;
import io.a2a.server.events.TaskQueueExistsException;
import io.a2a.server.tasks.PushNotifier;
import io.a2a.server.tasks.ResultAggregator;
import io.a2a.server.tasks.TaskManager;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.EventType;
import io.a2a.spec.GetTaskPushNotificationResponse;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.PushNotificationConfig;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.SetTaskPushNotificationResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.UnsupportedOperationError;
import io.a2a.util.AsyncUtils;
import mutiny.zero.ZeroPublisher;

public class DefaultRequestHandler implements RequestHandler {

    private final AgentExecutor agentExecutor;
    private final TaskStore taskStore;
    private final QueueManager queueManager;
    private final PushNotifier pushNotifier;

    // TODO the value upstream is asyncio.Task. Trying a Runnable
    private final Map<String, Runnable> runningAgents = Collections.synchronizedMap(new HashMap<>());

    public DefaultRequestHandler(AgentExecutor agentExecutor, TaskStore taskStore, QueueManager queueManager, PushNotifier pushNotifier) {
        this.agentExecutor = agentExecutor;
        this.taskStore = taskStore;
        this.queueManager = queueManager;
        this.pushNotifier = pushNotifier;
    }

    @Override
    public Task onGetTask(TaskQueryParams params) throws A2AServerException {
        Task task = taskStore.get(params.id());
        if (task == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }
        return task;
    }

    @Override
    public Task onCancelTask(TaskIdParams params) throws A2AServerException {
        Task task = taskStore.get(params.id());
        if (task == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }
        TaskManager taskManager = new TaskManager(
                task.getId(),
                task.getContextId(),
                taskStore,
                null);

        ResultAggregator resultAggregator = new ResultAggregator(taskManager, null);

        EventQueue queue = queueManager.tap(task.getId());
        if (queue == null) {
            queue = new EventQueue();
        }
        agentExecutor.cancel(
                new RequestContext(null, task.getId(), task.getContextId(), task, null),
                queue);

        // TODO need to cancel the asyncio.Task looked up from runningAgents

        EventConsumer consumer = new EventConsumer(queue);
        EventType type = resultAggregator.consumeAll(consumer);
        if (type instanceof Task task1) {
            return task;
        }

        throw new A2AServerException(new InternalError("Agent did not return a valid response"));
    }

    @Override
    public EventType onMessageSend(MessageSendParams params) throws A2AServerException {
        TaskManager taskManager = new TaskManager(
                params.message().getTaskId(),
                params.message().getContextId(),
                taskStore,
                params.message());

        Task task = taskManager.getTask();
        if (task != null) {
            task = taskManager.updateWithMessage(params.message(), task);
            if (shouldAddPushInfo(params)) {
                pushNotifier.setInfo(task.getId(), params.configuration().pushNotification());
            }
        }
        RequestContext requestContext = new RequestContext(
                params,
                task == null ? null : task.getId(),
                task == null ? null : task.getContextId(),
                task,
                null);

        String taskId = requestContext.getTaskId();
        EventQueue queue = queueManager.createOrTap(taskId);
        ResultAggregator resultAggregator = new ResultAggregator(taskManager, null);

        Runnable providerRunnable = new Runnable() {
            @Override
            public void run() {
                runEventStream(requestContext, queue);
            }
        };
        registerProducer(taskId, providerRunnable);

        EventConsumer consumer = new EventConsumer(queue);

        // TODO https://github.com/fjuma/a2a-java-sdk/issues/62 Add this callback

        EventType type = null;
        boolean interrupted = false;
        ResultAggregator.EventTypeAndInterrupt etai = resultAggregator.consumeAndBreakOnInterrupt(consumer);

        try {
            if (etai == null) {
                throw new A2AServerException(new InternalError());
            }
            interrupted = etai.interrupted();
        } finally {
            if (interrupted) {
                // TODO Make this async
                cleanupProducer(providerRunnable, taskId);
            } else {
                cleanupProducer(providerRunnable, taskId);
            }
        }

        return etai.eventType();
    }

    @Override
    public Flow.Publisher<Event> onMessageSendStream(MessageSendParams params) throws A2AServerException {
        TaskManager taskManager = new TaskManager(
                params.message().getTaskId(),
                params.message().getContextId(),
                taskStore,
                params.message());
        Task task = taskManager.getTask();

        if (task != null) {
            task = taskManager.updateWithMessage(params.message(), task);

            if (shouldAddPushInfo(params)) {
                pushNotifier.setInfo(task.getId(), params.configuration().pushNotification());
            }
        }

        ResultAggregator resultAggregator = new ResultAggregator(taskManager, null);
        RequestContext requestContext = new RequestContext(
                params,
                task == null ? null : task.getId(),
                task == null ? null : task.getContextId(),
                task,
                null);

        AtomicReference<String> taskId = new AtomicReference<>(requestContext.getTaskId());
        EventQueue queue = queueManager.createOrTap(taskId.get());
        Runnable producerRunnable = new Runnable() {
            @Override
            public void run() {
                runEventStream(requestContext, queue);
            }
        };
        registerProducer(taskId.get(), producerRunnable);
        // TODO https://github.com/fjuma/a2a-java-sdk/issues/62 Add this callback

        try {
            EventConsumer consumer = new EventConsumer(queue);

            Flow.Publisher<Event> all = consumer.consumeAll();

            return ZeroPublisher.create(createTubeConfig(), tube -> {
                all.subscribe(
                        new AsyncUtils.PublishingSubscriber<>(
                                tube,
                                ((subscriber, event) -> {
                                    if (event instanceof Task createdTask && !taskId.get().equals(createdTask.getId())) {
                                        try {
                                            queueManager.add(createdTask.getId(), queue);
                                            taskId.set(createdTask.getId());
                                        } catch (TaskQueueExistsException e) {
                                            // TODO Log
                                        }
                                        if (pushNotifier != null &&
                                                params.configuration() != null &&
                                                params.configuration().pushNotification() != null) {

                                            pushNotifier.setInfo(
                                                    createdTask.getId(),
                                                    params.configuration().pushNotification());
                                        }

                                    }
                                    if (pushNotifier != null && taskId.get() != null) {
                                        EventType latest = resultAggregator.getCurrentResult();
                                        if (latest instanceof Task latestTask) {
                                            pushNotifier.sendNotification(latestTask);
                                        }
                                    }

                                    return true;
                                })));
            });


        } finally {
            cleanupProducer(producerRunnable, taskId.get());
        }
    }

    @Override
    public TaskPushNotificationConfig onSetTaskPushNotificationConfig(TaskPushNotificationConfig params) throws A2AServerException {
        if (pushNotifier == null) {
            throw new A2AServerException(new UnsupportedOperationError());
        }
        Task task = taskStore.get(params.id());
        if (task == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }

        pushNotifier.setInfo(params.id(), params.pushNotificationConfig());

        return params;
    }

    @Override
    public TaskPushNotificationConfig onGetTaskPushNotificationConfig(TaskIdParams params) throws A2AServerException {
        if (pushNotifier == null) {
            throw new A2AServerException(new UnsupportedOperationError());
        }
        Task task = taskStore.get(params.id());
        if (task == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }

        PushNotificationConfig pushNotificationConfig = pushNotifier.getInfo(params.id());
        if (pushNotificationConfig == null) {
            throw new A2AServerException(new InternalError());
        }

        return new TaskPushNotificationConfig(params.id(), pushNotificationConfig);
    }

    @Override
    public Flow.Publisher<Event> onResubscribeToTask(TaskIdParams params) throws A2AServerException {
        Task task = taskStore.get(params.id());
        if (task == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }

        TaskManager taskManager = new TaskManager(task.getId(), task.getContextId(), taskStore, null);
        ResultAggregator resultAggregator = new ResultAggregator(taskManager, null);
        EventQueue queue = queueManager.tap(task.getId());

        if (queue == null) {
            throw new A2AServerException(new TaskNotFoundError());
        }

        EventConsumer consumer = new EventConsumer(queue);

        Flow.Publisher<Event> all = resultAggregator.consumeAndEmit(consumer);
        return ZeroPublisher.create(createTubeConfig(), tube -> {
            all.subscribe(
                    new AsyncUtils.PublishingSubscriber<Event>(tube, (subscriber, event) -> true)
            );
        });
    }

    private boolean shouldAddPushInfo(MessageSendParams params) {
        return pushNotifier != null && params.configuration() != null && params.configuration().pushNotification() != null;
    }

    private void runEventStream(RequestContext requestContext, EventQueue queue) {
        agentExecutor.execute(requestContext, queue);
    }

    private void registerProducer(String taskId, Runnable providerRunnable) {
        runningAgents.put(taskId, providerRunnable);
    }

    private void cleanupProducer(Runnable producerRunnable, String taskId) {
        // TODO the Python implementation waits for the producerRunnable

        queueManager.close(taskId);
        runningAgents.remove(taskId);
    }

}
