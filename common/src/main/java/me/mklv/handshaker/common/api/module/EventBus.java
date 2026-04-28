package me.mklv.handshaker.common.api.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Simple async event bus for HandShaker module events.
 * Event dispatch stays serialized, while handlers run on a worker pool so one
 * slow handler does not block all remaining listeners.
 */
public final class EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger("handshaker-event-bus");

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<HandShakerEvent>>> listeners =
            new ConcurrentHashMap<>();

    private static ThreadFactory daemonFactory(String threadNamePrefix) {
        return r -> {
            Thread t = new Thread(r, threadNamePrefix);
            t.setDaemon(true);
            return t;
        };
    }

    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(daemonFactory("handshaker-event-bus"));
    private final ExecutorService handlerExecutor = Executors.newFixedThreadPool(4, daemonFactory("handshaker-event-handler"));

    /**
     * Subscribes to events of the given type.
     * Safe to call from any thread; handlers run on event worker threads.
     */
    @SuppressWarnings("unchecked")
    public <T extends HandShakerEvent> void subscribe(Class<T> type, Consumer<T> handler) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                 .add(e -> handler.accept((T) e));
    }

    /**
     * Fires an event asynchronously. Returns immediately.
     */
    public void fire(HandShakerEvent event) {
        CopyOnWriteArrayList<Consumer<HandShakerEvent>> handlers = listeners.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) return;
        dispatcher.submit(() -> {
            for (Consumer<HandShakerEvent> handler : handlers) {
                handlerExecutor.submit(() -> {
                    try {
                        handler.accept(event);
                    } catch (Exception e) {
                        LOGGER.warn("[EventBus] Handler threw for {}: {}",
                                event.getClass().getSimpleName(), e.getMessage());
                    }
                });
            }
        });
    }

    /** Shuts down the event bus executors. Call this on server stop. */
    public void shutdown() {
        dispatcher.shutdownNow();
        handlerExecutor.shutdownNow();
    }
}
