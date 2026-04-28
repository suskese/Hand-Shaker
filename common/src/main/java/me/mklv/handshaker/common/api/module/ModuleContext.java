package me.mklv.handshaker.common.api.module;

import com.sun.net.httpserver.HttpHandler;
import me.mklv.handshaker.common.api.local.ApiDataProvider;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Provided to each module on {@link HandShakerModule#onEnable(ModuleContext)}.
 * Gives access to data, the event bus, logging, storage, and HTTP route registration.
 */
public interface ModuleContext {

    /** Access to HandShaker's player/mod/history data. */
    ApiDataProvider dataProvider();

    /** Shared event bus. Subscribe here to react to HandShaker events. */
    EventBus eventBus();

    /**
     * Returns a logger for the given category name.
     * Convention: use your module id, e.g. {@code ctx.logger("handshaker-discord")}.
     */
    Logger logger(String category);

    /**
     * Per-module data directory: {@code <HandShaker config dir>/Modules/<moduleId>/}.
     * Guaranteed to exist when this method is called.
     */
    Path dataFolder();

    /**
     * Contributes an HTTP handler to the local REST API.
     * The handler will be mounted at {@code /api/v1/modules/<subPath>}.
     * The API key authorization check is automatically applied.
     * Must be called from within {@link HandShakerModule#onEnable(ModuleContext)}.
     */
    void registerApiRoute(String subPath, HttpHandler handler);
}
