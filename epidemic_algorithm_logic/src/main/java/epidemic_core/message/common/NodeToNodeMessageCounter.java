package epidemic_core.message.common;

import java.util.concurrent.atomic.AtomicLong;

public final class NodeToNodeMessageCounter {

    private static final NodeToNodeMessageCounter INSTANCE = new NodeToNodeMessageCounter();
    private final AtomicLong count = new AtomicLong(0);

    private NodeToNodeMessageCounter() {}

    public static NodeToNodeMessageCounter getInstance() {
        return INSTANCE;
    }

    public void increment() {
        count.incrementAndGet();
    }

    public long get() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }
}
