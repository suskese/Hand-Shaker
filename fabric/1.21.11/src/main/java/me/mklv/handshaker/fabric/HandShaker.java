package me.mklv.handshaker.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.JarIntegrityProof;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

public class HandShaker implements ClientModInitializer {
	public static final String MOD_ID = "hand-shaker";
	public static final Identifier MODS_CHANNEL = Identifier.of(MOD_ID, "mods");
	public static final Identifier INTEGRITY_CHANNEL = Identifier.of(MOD_ID, "integrity");

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@SuppressWarnings("null")
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
				.map(m -> {
					String id = m.getMetadata().getId();
					String normalizedId = MOD_ID.equals(id) ? MOD_ID : id;
					String displayName = m.getMetadata().getName();
					String encodedDisplayName = encodeDisplayName(displayName);
					String version = m.getMetadata().getVersion().getFriendlyString();
					Path modPath = safeFirstOriginPath(m).orElse(null);
					String modHash = computeModFileHash(modPath).orElse("null");
					return normalizedId + "~" + encodedDisplayName + ":" + version + ":" + modHash;
				})
				.sorted()
				.reduce((a,b) -> a + "," + b)
				.orElse("");
		String modListHash = HashUtils.sha256Hex(payload);
		String nonce = generateNonce();
		ClientPlayNetworking.send(new ModsListPayload(payload, modListHash, nonce));
		LOGGER.info("Sent mod list ({} chars, hash: {}) with nonce: {}", payload.length(), modListHash.substring(0, 8), nonce);
	}

	private String encodeDisplayName(String name) {
		if (name == null || name.isBlank()) {
			return "null";
		}
		return URLEncoder.encode(name, StandardCharsets.UTF_8);
	}

	private Optional<Path> safeFirstOriginPath(net.fabricmc.loader.api.ModContainer modContainer) {
		if (modContainer == null || modContainer.getOrigin() == null) {
			return Optional.empty();
		}
		try {
			var paths = modContainer.getOrigin().getPaths();
			if (paths == null || paths.isEmpty()) {
				return Optional.empty();
			}
			return Optional.ofNullable(paths.get(0));
		} catch (UnsupportedOperationException ignored) {
			return Optional.empty();
		}
	}

	private void sendSignature() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) return;

		Optional<JarIntegrityProof.Proof> proof = JarIntegrityProof.buildFromRuntimeJar(HandShaker.class, new JarIntegrityProof.LogSink() {
			@Override
			public void info(String message) {
				LOGGER.info(message);
			}

			@Override
			public void warn(String message) {
				LOGGER.warn(message);
			}
		});
		String nonce = generateNonce();

		if (proof.isPresent()) {
			JarIntegrityProof.Proof integrityProof = proof.get();
			ClientPlayNetworking.send(new IntegrityPayload(integrityProof.signature(), integrityProof.jarHash(), nonce));
			LOGGER.info("Sent detached integrity proof ({} bytes) with content hash {} and nonce: {}",
				integrityProof.signature().length,
				integrityProof.jarHash().substring(0, 8),
				nonce);
		} else {
			LOGGER.warn("Could not build runtime integrity proof. Sending empty payload.");
			ClientPlayNetworking.send(new IntegrityPayload(new byte[0], "", nonce));
		}
	}

	private Optional<String> computeModFileHash(Path modPath) {
		return HashUtils.sha256FileHex(modPath);
	}

	public record ModsListPayload(String mods, String modListHash, String nonce) implements CustomPayload {
		public static final CustomPayload.Id<ModsListPayload> ID = new CustomPayload.Id<>(MODS_CHANNEL);
		public static final PacketCodec<PacketByteBuf, ModsListPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, ModsListPayload::mods,
				PacketCodecs.STRING, ModsListPayload::modListHash,
				PacketCodecs.STRING, ModsListPayload::nonce,
				ModsListPayload::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record IntegrityPayload(byte[] signature, String jarHash, String nonce) implements CustomPayload {
		public static final CustomPayload.Id<IntegrityPayload> ID = new CustomPayload.Id<>(INTEGRITY_CHANNEL);
		public static final PacketCodec<PacketByteBuf, IntegrityPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.BYTE_ARRAY, IntegrityPayload::signature,
				PacketCodecs.STRING, IntegrityPayload::jarHash,
				PacketCodecs.STRING, IntegrityPayload::nonce,
				IntegrityPayload::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}
}