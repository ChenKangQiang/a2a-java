package io.a2a.server.tasks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.a2a.spec.Task;

public class InMemoryTaskStore implements TaskStore {

    private final Map<String, Task> tasks = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void save(Task task) {
        tasks.put(task.getId(), task);
    }

    @Override
    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public void delete(String taskId) {
        tasks.remove(taskId);
    }
}
