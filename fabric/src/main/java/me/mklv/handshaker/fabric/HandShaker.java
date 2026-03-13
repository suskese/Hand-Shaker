package me.mklv.handshaker.fabric;

import me.mklv.handlib.network.PayloadTypeCompat;
import me.mklv.handshaker.common.loader.CommonClientHandshakeOrchestrator;
import me.mklv.handshaker.common.loader.CommonClientHashPayloadService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandShaker implements ClientModInitializer {
	public static final String MOD_ID = "hand-shaker";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private final ClientHashPayloadService payloadService = new ClientHashPayloadService();
	private final CommonClientHandshakeOrchestrator handshakeOrchestrator = new CommonClientHandshakeOrchestrator();
	@Override
	public void onInitializeClient() {
		LOGGER.info("HandShaker client initializing");
		payloadService.precomputeAtBoot();

		// Register payload types for 1.21 custom payload system
		PayloadTypeRegistry.playC2S().register(ModsListPayload.TYPE, ModsListPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IntegrityPayload.TYPE, IntegrityPayload.CODEC);

		// Register event handlers to send data on server join
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			handshakeOrchestrator.onJoin(
				this::isConnectionReady,
				new CommonClientHandshakeOrchestrator.PayloadProvider() {
					@Override
					public CommonClientHashPayloadService.ModListData getModListData() {
						ClientHashPayloadService.ModListData data = payloadService.getOrBuildModListData();
						return new CommonClientHashPayloadService.ModListData(data.transportPayload(), data.modListHash());
					}

					@Override
					public CommonClientHashPayloadService.IntegrityData getIntegrityData() {
						ClientHashPayloadService.IntegrityData data = payloadService.getOrBuildIntegrityData();
						return new CommonClientHashPayloadService.IntegrityData(data.signature(), data.jarHash());
					}
				},
				new CommonClientHandshakeOrchestrator.Sender() {
					@Override
					public void sendModList(String transportPayload, String modListHash, String nonce) {
						ClientPlayNetworking.send(new ModsListPayload(transportPayload, modListHash, nonce));
					}

					@Override
					public void sendIntegrity(byte[] signature, String jarHash, String nonce) {
						ClientPlayNetworking.send(new IntegrityPayload(signature, jarHash, nonce));
					}
				},
				new CommonClientHandshakeOrchestrator.Logger() {
					@Override
					public void info(String format, Object... args) {
						LOGGER.info(format, args);
					}

					@Override
					public void warn(String message) {
						LOGGER.warn(message);
					}
				}
			);
		});
	}

	private boolean isConnectionReady() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.getConnection() != null;
	}

	public record ModsListPayload(String mods, String modListHash, String nonce) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ModsListPayload> TYPE = PayloadTypeCompat.payloadType(MOD_ID, "mods");
		public static final StreamCodec<ByteBuf, ModsListPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ModsListPayload::mods,
				ByteBufCodecs.STRING_UTF8, ModsListPayload::modListHash,
				ByteBufCodecs.STRING_UTF8, ModsListPayload::nonce,
				ModsListPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record IntegrityPayload(byte[] signature, String jarHash, String nonce) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<IntegrityPayload> TYPE = PayloadTypeCompat.payloadType(MOD_ID, "integrity");
		public static final StreamCodec<ByteBuf, IntegrityPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.BYTE_ARRAY, IntegrityPayload::signature,
				ByteBufCodecs.STRING_UTF8, IntegrityPayload::jarHash,
				ByteBufCodecs.STRING_UTF8, IntegrityPayload::nonce,
				IntegrityPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}
}