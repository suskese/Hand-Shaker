package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ClientInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CommandRuntimeOperations {
    private CommandRuntimeOperations() {
    }

    public static void registerModFingerprint(
        String modToken,
        PlayerHistoryDatabase db,
        boolean hashMods,
        boolean modVersioning,
        Iterable<ClientInfo> connectedClients,
        boolean async,
        Executor executor
    ) {
        Runnable task = () -> CommandModUtil.registerFromCommand(
            modToken,
            db,
            hashMods,
            modVersioning,
            connectedClients
        );

        if (async) {
            CompletableFuture.runAsync(task, executor);
        } else {
            task.run();
        }
    }

    public static <T> int executeAsyncDatabase(
        boolean async,
        Executor backgroundExecutor,
        Consumer<Runnable> mainThreadExecutor,
        Supplier<T> supplier,
        Consumer<T> onSuccess,
        Consumer<Throwable> onFailure,
        int successCode,
        int failureCode
    ) {
        if (!async) {
            try {
                onSuccess.accept(supplier.get());
                return successCode;
            } catch (Throwable throwable) {
                onFailure.accept(throwable);
                return failureCode;
            }
        }

        CompletableFuture.supplyAsync(supplier, backgroundExecutor)
            .thenAccept(result -> mainThreadExecutor.accept(() -> onSuccess.accept(result)))
            .exceptionally(throwable -> {
                mainThreadExecutor.accept(() -> onFailure.accept(throwable));
                return null;
            });

        return successCode;
    }
}