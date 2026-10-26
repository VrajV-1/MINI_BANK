package minibank.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class TransactionProcessor implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private boolean stopped;

    public synchronized <T> Future<T> submit(Callable<T> task) {
        if (stopped) {
            throw new IllegalStateException("Transaction processor is stopped");
        }
        return executor.submit(task);
    }

    public synchronized void stop() throws InterruptedException {
        if (!stopped) {
            stopped = true;
            executor.shutdown();
        }
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            executor.shutdownNow();
            throw new IllegalStateException("Transaction tasks did not finish in time");
        }
    }

    @Override
    public void close() {
        try {
            stop();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping transaction tasks",
                    exception);
        }
    }

    public static List<String> processWithQueue(
            List<String> commands, Function<String, String> processor)
            throws InterruptedException {
        Objects.requireNonNull(commands, "Commands are required");
        Objects.requireNonNull(processor, "Command processor is required");
        TransactionBuffer buffer = new TransactionBuffer(5);
        List<String> results = new ArrayList<>();
        Thread producer = new Thread(() -> {
            try {
                for (String command : commands) {
                    buffer.put(command);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                buffer.close();
            }
        }, "transaction-producer");
        Thread consumer = new Thread(() -> {
            try {
                String command;
                while ((command = buffer.take()) != null) {
                    try {
                        results.add(processor.apply(command));
                    } catch (RuntimeException exception) {
                        results.add("Transaction rejected: " + exception.getMessage());
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "transaction-consumer");

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
        return results;
    }

    public static final class BatchFailure extends Exception {
        private static final long serialVersionUID = 1L;

        public BatchFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static <T> T getResult(Future<T> future)
            throws InterruptedException, BatchFailure {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            throw new BatchFailure("A transaction task failed", exception.getCause());
        }
    }

    private static final class TransactionBuffer {
        private final String[] items;
        private int head;
        private int tail;
        private int size;
        private boolean closed;

        private TransactionBuffer(int capacity) {
            items = new String[capacity];
        }

        synchronized void put(String value) throws InterruptedException {
            while (size == items.length && !closed) {
                wait();
            }
            if (closed) {
                throw new IllegalStateException("Transaction buffer is closed");
            }
            items[tail] = value;
            tail = (tail + 1) % items.length;
            size++;
            notifyAll();
        }

        synchronized String take() throws InterruptedException {
            while (size == 0 && !closed) {
                wait();
            }
            if (size == 0) {
                return null;
            }
            String value = items[head];
            items[head] = null;
            head = (head + 1) % items.length;
            size--;
            notifyAll();
            return value;
        }

        synchronized void close() {
            closed = true;
            notifyAll();
        }
    }
}
