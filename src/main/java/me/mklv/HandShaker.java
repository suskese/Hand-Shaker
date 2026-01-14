package me.mklv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class HandShaker implements ClientModInitializer {
	public static final String MOD_ID = "hand-shaker";
	public static final Identifier MODS_CHANNEL = Identifier.of(MOD_ID, "mods");
	public static final Identifier INTEGRITY_CHANNEL = Identifier.of(MOD_ID, "integrity");

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("HandShaker client initializing");

		// Register payload types for 1.21 custom payload system
		PayloadTypeRegistry.playC2S().register(ModsListPayload.ID, ModsListPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IntegrityPayload.ID, IntegrityPayload.CODEC);

		// Register event handlers to send data on server join
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			sendModList();
			sendSignature();
		});
	}

	private String generateNonce() {
		return java.util.UUID.randomUUID().toString();
	}

	private void sendModList() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) return;
		String payload = FabricLoader.getInstance().getAllMods().stream()
				.map(m -> m.getMetadata().getId())
				.sorted()
				.reduce((a,b) -> a + "," + b)
				.orElse("");
		String nonce = generateNonce();
		ClientPlayNetworking.send(new ModsListPayload(payload, nonce));
		LOGGER.info("Sent mod list ({} chars) with nonce: {}", payload.length(), nonce);
	}

	private void sendSignature() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) return;

		Optional<byte[]> certificate = getEmbeddedCertificate();
		String nonce = generateNonce();
		if (certificate.isPresent()) {
			ClientPlayNetworking.send(new IntegrityPayload(certificate.get(), nonce));
			LOGGER.info("Sent integrity certificate ({} bytes) with nonce: {}", certificate.get().length, nonce);
		} else {
			LOGGER.warn("Could not find own embedded certificate. Sending empty payload with nonce.");
			ClientPlayNetworking.send(new IntegrityPayload(new byte[0], nonce));
		}
	}

	private Optional<byte[]> getEmbeddedCertificate() {
		try (var is = HandShaker.class.getClassLoader().getResourceAsStream("public.cer")) {
			if (is == null) {
				LOGGER.error("Could not find 'public.cer' in the mod JAR.");
				return Optional.empty();
			}
			return Optional.of(is.readAllBytes());
		} catch (IOException e) {
			LOGGER.error("Failed to load embedded certificate", e);
			return Optional.empty();
		}
	}

	public record ModsListPayload(String mods, String nonce) implements CustomPayload {
		public static final CustomPayload.Id<ModsListPayload> ID = new CustomPayload.Id<>(MODS_CHANNEL);
		public static final PacketCodec<PacketByteBuf, ModsListPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, ModsListPayload::mods,
				PacketCodecs.STRING, ModsListPayload::nonce,
				ModsListPayload::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record IntegrityPayload(byte[] signature, String nonce) implements CustomPayload {
		public static final CustomPayload.Id<IntegrityPayload> ID = new CustomPayload.Id<>(INTEGRITY_CHANNEL);
		public static final PacketCodec<PacketByteBuf, IntegrityPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.BYTE_ARRAY, IntegrityPayload::signature,
				PacketCodecs.STRING, IntegrityPayload::nonce,
				IntegrityPayload::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}
}
