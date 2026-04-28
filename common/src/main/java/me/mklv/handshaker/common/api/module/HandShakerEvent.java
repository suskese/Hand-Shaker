package me.mklv.handshaker.common.api.module;

/**
 * Sealed interface for events fired by the HandShaker core.
 * Modules subscribe via {@link EventBus#subscribe(Class, java.util.function.Consumer)}.
 */
public sealed interface HandShakerEvent
        permits HandShakerEvent.PlayerKicked,
                HandShakerEvent.PlayerBanned,
                HandShakerEvent.ModDetected,
                HandShakerEvent.IntegrityFailed {

    record PlayerKicked(String playerName, String modId, String reason) implements HandShakerEvent {}

    record PlayerBanned(String playerName, String modId, String reason) implements HandShakerEvent {}

    record ModDetected(String playerName, String modId) implements HandShakerEvent {}

    record IntegrityFailed(String playerName, String reason) implements HandShakerEvent {}
}
