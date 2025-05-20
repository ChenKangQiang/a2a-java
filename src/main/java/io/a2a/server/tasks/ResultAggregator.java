package io.a2a.server.tasks;

import static io.a2a.util.AsyncUtils.createTubeConfig;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.a2a.server.events.Event;
import io.a2a.server.events.EventConsumer;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.EventType;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.util.AsyncUtils.ConsumingSubscriber;
import io.a2a.util.AsyncUtils.PublishingSubscriber;
import mutiny.zero.ZeroPublisher;

public class ResultAggregator {
    private final TaskManager taskManager;
    private volatile Message message;

    public ResultAggregator(TaskManager taskManager, Message message) {
        this.taskManager = taskManager;
        this.message = message;
    }

    public EventType getCurrentResult() {
        if (message != null) {
            return message;
        }
        return taskManager.getTask();
    }

    public Flow.Publisher<Event> consumeAndEmit(EventConsumer consumer) {
        Flow.Publisher<Event> all = consumer.consumeAll();

        return ZeroPublisher.create(createTubeConfig(), tube -> {
            all.subscribe(
                    new PublishingSubscriber<>(
                            tube,
                            ((subscriber, event) -> {
                                callTaskManagerProcess(event);
                                return true;
                            })));
        });
    }

    public EventType consumeAll(EventConsumer consumer) {
        AtomicReference<EventType> returnedEvent = new AtomicReference<>();
        Flow.Publisher<Event> all = consumer.consumeAll();

        ZeroPublisher.create(createTubeConfig(), tube -> {
            all.subscribe(
                    new ConsumingSubscriber<>((subscriber, event) -> {
                        if (event instanceof Message msg) {
                            message = msg;
                            if (returnedEvent.get() == null) {
                                returnedEvent.set(msg);
                                return false;
                            }
                            callTaskManagerProcess(event);
                        }
                        return true;
                    }));
        });

        if (returnedEvent.get() != null) {
            return returnedEvent.get();
        }
        return taskManager.getTask();
    }

    public EventTypeAndInterrupt consumeAndBreakOnInterrupt(EventConsumer consumer) {
        Flow.Publisher<Event> all = consumer.consumeAll();
        AtomicReference<Message> message = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean(false);

        ZeroPublisher.create(createTubeConfig(), tube -> {
            all.subscribe(
                    new ConsumingSubscriber<>(((subscriber, event) -> {
                        if (event instanceof Message msg) {
                            this.message = msg;
                            message.set(msg);
                            return false;
                        }

                        callTaskManagerProcess(event);

                        if ((event instanceof Task task && task.getStatus().state() == TaskState.AUTH_REQUIRED)
                                || (event instanceof TaskStatusUpdateEvent tsue && tsue.getStatus().state() == TaskState.AUTH_REQUIRED)) {
                            // auth-required is a special state: the message should be
                            // escalated back to the caller, but the agent is expected to
                            // continue producing events once the authorization is received
                            // out-of-band. This is in contrast to input-required, where a
                            // new request is expected in order for the agent to make progress,
                            // so the agent should exit.

                            // TODO There is the following line in the Python code I don't totally get
                            //      asyncio.create_task(self._continue_consuming(event_stream))
                            //  I think it means the continueConsuming() call should be done in another thread
                            continueConsuming(all);

                            interrupted.set(true);
                            return false;
                        }
                        return true;
                    })));
        });

        return new EventTypeAndInterrupt(
                message.get() != null ? message.get() : taskManager.getTask(), interrupted.get());
    }

    private void continueConsuming(Flow.Publisher<Event> all) {
        ZeroPublisher.create(createTubeConfig(), tube -> {
            all.subscribe(
                    new ConsumingSubscriber<>(((subscriber, event) -> {
                        callTaskManagerProcess(event);
                        return true;
                    })));
        });
    }

    private void callTaskManagerProcess(Event event) {
         try {
            taskManager.process(event);
        } catch (A2AServerException e) {
            // TODO Decide what to do in case of failure
            e.printStackTrace();
        }
    }

    public record EventTypeAndInterrupt(EventType eventType, boolean interrupted) {

    }
}
