package httpserver.nio.http.eventloop;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLoopGroup {

    private final WorkerEventLoop[] workers;
    private final AtomicInteger nextWorkerIndex;
    private final AtomicInteger connectionIdSequence;

    public EventLoopGroup(int workerCount) throws IOException {
        this.workers = new WorkerEventLoop[workerCount];
        this.nextWorkerIndex = new AtomicInteger(0);
        this.connectionIdSequence = new AtomicInteger(1);

        for (int i = 0; i < workerCount; i++) {
            workers[i] = new WorkerEventLoop(i + 1);
        }
    }

    public void start() {
        for (WorkerEventLoop worker : workers) {
            Thread thread = new Thread(worker, worker.name());
            thread.start();
        }
    }

    public int nextConnectionId() {
        return connectionIdSequence.getAndIncrement();
    }

    public WorkerEventLoop nextWorker() {
        int index = Math.floorMod(nextWorkerIndex.getAndIncrement(), workers.length);
        return workers[index];
    }

    public void register(SocketChannel channel, int connectionId) {
        nextWorker().register(channel, connectionId);
    }
}
