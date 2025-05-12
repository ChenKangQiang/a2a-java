# Java A2A SDK (WIP)

This project (currently WIP) will provide a Java SDK implementation of Google's [Agent2Agent protocol (A2A)](https://google.github.io/A2A/).

## Specification

The majority of the classes required by the specification can currently be found in the [src/main/java/io/a2a/spec](https://github.com/fjuma/a2a-java-sdk/tree/main/src/main/java/io/a2a/spec) directory.

## Client

An *initial* [A2AClient](https://github.com/fjuma/a2a-java-sdk/blob/main/src/main/java/io/a2a/client/A2AClient.java) class has been added. This is very much work in progress, we are working on implementing the methods required by the client side of the protocol.

### Sample Usage

#### Create a client

```java
// Create an A2AClient (the URL specified is the server agent's URL)
A2AClient client = new A2AClient("http://localhost:1234");
```

#### Send a task

```java
// Send a text message to the server agent
Message message = A2A.toUserMessage("tell me a joke");
TaskSendParams params = new TaskSendParams.Builder()
        .id("task-1234") // id is optional
        .message(message)
        .build();
SendTaskResponse response = client.sendTask(params);        
```

#### Get a task

```java
// Retrieve the task with id "task-1234"
GetTaskResponse response = client.getTask("task-1234");

// You can also specify the maximum number of items of history for the task
// to include in the response
GetTaskResponse response = client.getTask(new TaskQueryParams("task-1234", 10));
```

#### Cancel a task

```java
// Cancel the task we previously submitted with id "task-1234"
CancelTaskResponse response = client.cancelTask("task-1234");

// You can also specify additional properties using a map
Map<String, Object> metadata = ...        
CancelTaskResponse response = client.cancelTask(new TaskIdParams("task-1234", metadata));
```

#### Get the push notification configuration for a task

```java
// Get task push notification
GetTaskPushNotificationResponse response = client.getTaskPushNotificationConfig("task-1234");

// You can also specify additional properties using a map
Map<String, Object> metadata = ...
GetTaskPushNotificationResponse response = client.getTaskPushNotificationConfig(new TaskIdParams("task-1234", metadata));
```

#### Set the push notification configuration for a task

```java
// Set task push notification configuration
PushNotificationConfig pushNotificationConfig = new PushNotificationConfig.Builder()
        .url("https://example.com/callback")
        .authenticationInfo(new AuthenticationInfo(Collections.singletonList("jwt"), null))
        .build());
SetTaskPushNotificationResponse response = client.setTaskPushNotificationConfig("task-1234", pushNotificationConfig);
```

#### Retrieve details about the server agent that this client agent is communicating with
```java
AgentCard serverAgentCard = client.getAgentCard();
```

An agent card can also be retrieved using the `A2A#getAgentCard` method:
```java
// http://localhost:1234 is the base URL for the agent whose card we want to retrieve
AgentCard agentCard = A2A.getAgentCard("http://localhost:1234");
```

## Server

TODO



