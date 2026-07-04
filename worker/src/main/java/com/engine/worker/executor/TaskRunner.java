package com.engine.worker.executor;

import com.engine.common.model.Task;
import com.engine.common.model.TaskResult;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.worker.network.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Executes a single task on the worker and sends the result back to the master.
 *
 * @author Engine Team
 */
public class TaskRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);

    private final Task task;
    private final WorkerClient client;
    private final Serializer serializer;

    /**
     * Constructs a new TaskRunner.
     *
     * @param task   the task to execute
     * @param client the worker client to send the result
     */
    public TaskRunner(Task task, WorkerClient client) {
        this.task = Objects.requireNonNull(task, "Task cannot be null");
        this.client = Objects.requireNonNull(client, "WorkerClient cannot be null");
        this.serializer = new JavaSerializer();
    }

    @Override
    public void run() {
        logger.info("Executing task: {}", task.getTaskId());
        TaskResult result = null;
        try {
            // Simulated execution: We can sleep based on payload or just do a simple op
            // In a real framework, this would invoke a TaskHandler dynamically.
            Thread.sleep(100); 
            
            result = new TaskResult.Builder()
                    .taskId(task.getTaskId())
                    .workerId(client.getAssignedWorkerId())
                    .success(true)
                    .result(serializer.serialize("Success"))
                    .build();
            logger.info("Task {} completed successfully", task.getTaskId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = new TaskResult.Builder()
                    .taskId(task.getTaskId())
                    .workerId(client.getAssignedWorkerId())
                    .success(false)
                    .errorMessage("Execution interrupted")
                    .build();
            logger.warn("Task {} was interrupted", task.getTaskId());
        } catch (Exception e) {
            result = new TaskResult.Builder()
                    .taskId(task.getTaskId())
                    .workerId(client.getAssignedWorkerId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            logger.error("Task {} failed during execution", task.getTaskId(), e);
        }

        // Send the result back to master
        try {
            Message msg = new Message.Builder(MessageType.TASK_RESULT)
                    .payload(serializer.serialize(result))
                    .build();
            client.sendMessage(msg);
            logger.debug("Sent result for task {} to master", task.getTaskId());
        } catch (IOException e) {
            logger.error("Failed to send task result to master for task {}", task.getTaskId(), e);
        }
    }
}
