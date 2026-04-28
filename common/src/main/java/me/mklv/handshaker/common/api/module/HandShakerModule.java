package me.mklv.handshaker.common.api.module;

/**
 * Entry point interface for HandShaker modules.
 *
 * <p>Module JARs must declare an implementing class via Java's ServiceLoader mechanism:
 * create {@code META-INF/services/me.mklv.handshaker.common.api.module.HandShakerModule}
 * in the JAR with the fully-qualified class name as content.
 */
public interface HandShakerModule {

    /** Stable, unique identifier for this module (e.g. {@code "handshaker-discord"}). */
    String getId();

    /**
     * Called once after server config and database are ready.
     * Register event subscriptions and API routes here.
     */
    void onEnable(ModuleContext ctx);

    /** Called on server stop. Release all resources (threads, connections) here. */
    void onDisable();
}
