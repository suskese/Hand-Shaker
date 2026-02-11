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
import java.io.IOException;
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
				.map(m -> m.getMetadata().getId())
				.sorted()
				.reduce((a,b) -> a + "," + b)
				.orElse("");
		String modListHash = HashUtils.sha256Hex(payload);
		String nonce = generateNonce();
		ClientPlayNetworking.send(new ModsListPayload(payload, modListHash, nonce));
		LOGGER.info("Sent mod list ({} chars, hash: {}) with nonce: {}", payload.length(), modListHash.substring(0, 8), nonce);
	}

	private void sendSignature() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) return;

		Optional<byte[]> jarSignature = getJarSignature();
		Optional<String> jarContentHash = computeJarContentHash();
		String nonce = generateNonce();
		
		if (jarSignature.isPresent() && jarContentHash.isPresent()) {
			// Also verify the signature is valid on our end before sending
			Optional<Boolean> isSignatureValid = verifyJarSignatureLocally();
			if (isSignatureValid.isPresent() && isSignatureValid.get()) {
				ClientPlayNetworking.send(new IntegrityPayload(jarSignature.get(), jarContentHash.get(), nonce));
				LOGGER.info("Sent JAR signature ({} bytes) with content hash {} and nonce: {}", jarSignature.get().length, jarContentHash.get().substring(0, 8), nonce);
			} else {
				LOGGER.error("JAR signature verification FAILED on client side - rejecting!");
				ClientPlayNetworking.send(new IntegrityPayload(new byte[0], "", nonce));
			}
		} else {
			LOGGER.warn("Could not find JAR signature or hash. Sending empty payload.");
			ClientPlayNetworking.send(new IntegrityPayload(new byte[0], "", nonce));
		}
	}

	private Optional<byte[]> getJarSignature() {
		try {
			// Get the location of this mod's JAR file
			var modContainer = FabricLoader.getInstance().getModContainer(MOD_ID);
			if (modContainer.isEmpty()) {
				LOGGER.warn("Could not find Hand-Shaker mod container");
				return Optional.empty();
			}

			// Get the actual JAR/directory the mod was loaded from
			var origin = modContainer.get().getOrigin();
			if (origin == null) {
				LOGGER.warn("Could not get mod origin");
				return Optional.empty();
			}

			var paths = origin.getPaths();
			if (paths.isEmpty()) {
				LOGGER.warn("Mod origin paths is empty");
				return Optional.empty();
			}
			var path = paths.get(0);
			
			// Try to read signature from JAR's META-INF
			try (var zipFile = new java.util.zip.ZipFile(path.toFile())) {
				// Look for RSA signature file (the actual cryptographic signature)
				var entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					var entry = entries.nextElement();
					@SuppressWarnings("null")
					String name = entry.getName().toUpperCase();
					// Look for .RSA file (actual RSA signature, not .SF which is just a manifest)
					if (name.startsWith("META-INF/") && name.endsWith(".RSA")) {
						try (var is = zipFile.getInputStream(entry)) {
							byte[] signature = is.readAllBytes();
							LOGGER.info("Found RSA signature file in JAR: {}", entry.getName());
							return Optional.of(signature);
						}
					}
				}
				
				// Fallback: if no .RSA found, log error
				LOGGER.warn("No .RSA signature file found in META-INF");
			} catch (Exception e) {
				LOGGER.debug("Could not read signature from JAR: " + e.getMessage());
			}

			return Optional.empty();
		} catch (Exception e) {
			LOGGER.error("Failed to extract JAR signature", e);
			return Optional.empty();
		}
	}

	private Optional<String> computeJarContentHash() {
		try {
			var modContainer = FabricLoader.getInstance().getModContainer(MOD_ID);
			if (modContainer.isEmpty()) {
				LOGGER.error("Could not find Hand-Shaker mod container");
				return Optional.empty();
			}

			// Get the actual JAR/directory the mod was loaded from
			var origin = modContainer.get().getOrigin();
			if (origin == null) {
				LOGGER.error("Could not get mod origin/path");
				return Optional.empty();
			}

			var paths = origin.getPaths();
			if (paths.isEmpty()) {
				LOGGER.error("Mod origin paths is empty");
				return Optional.empty();
			}
			var path = paths.get(0);
			if (path == null) {
				LOGGER.error("Mod origin path is null");
				return Optional.empty();
			}
			
			// MUST be a JAR file - reject development directories
			if (!java.nio.file.Files.isRegularFile(path)) {
				LOGGER.error("Hand-Shaker is not running from a JAR file (path: {}). Only signed JARs are supported.", path);
				return Optional.empty();
			}

			// Hash all JAR contents except signature files
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			try (var zipFile = new java.util.zip.ZipFile(path.toFile())) {
				// Collect all entries, sort them, and hash in order for consistent results
				var entries = java.util.Collections.list(zipFile.entries()).stream()
					.filter(e -> {
						@SuppressWarnings("null")
						String name = e.getName().toUpperCase();
						// Skip signature files - we're verifying the content, not the signatures
						return !name.startsWith("META-INF/") || (!name.endsWith(".SF") && !name.endsWith(".RSA") && !name.endsWith(".DSA") && !name.equals("META-INF/MANIFEST.MF"));
					})
					.sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName))
					.toList();

				if (entries.isEmpty()) {
					LOGGER.error("JAR has no content files (only signatures?)");
					return Optional.empty();
				}

				// Hash all content files
				for (var entry : entries) {
					try (var is = zipFile.getInputStream(entry)) {
						byte[] buffer = new byte[8192];
						int read;
						while ((read = is.read(buffer)) > 0) {
							digest.update(buffer, 0, read);
						}
					}
				}

				byte[] hash = digest.digest();
				String hexHash = HashUtils.toHex(hash);
				LOGGER.info("Computed JAR content hash ({} files): {}", entries.size(), hexHash.substring(0, 8));
				return Optional.of(hexHash);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to compute JAR content hash", e);
			return Optional.empty();
		}
	}

	private Optional<Boolean> verifyJarSignatureLocally() {
		try {
			var modContainer = FabricLoader.getInstance().getModContainer(MOD_ID);
			if (modContainer.isEmpty()) return Optional.empty();

			var origin = modContainer.get().getOrigin();
			if (origin == null) return Optional.empty();

			var paths = origin.getPaths();
			if (paths.isEmpty()) return Optional.empty();
			
			var path = paths.get(0);
			if (path == null || !java.nio.file.Files.isRegularFile(path)) return Optional.empty();

			// Use Java's built-in JarFile verification
			try (var jarFile = new java.util.jar.JarFile(path.toFile())) {
				// Read all entries - this triggers signature verification
				var entries = java.util.Collections.list(jarFile.entries());
				for (var entry : entries) {
					try (var is = jarFile.getInputStream(entry)) {
						// Read the entire entry to trigger verification
						byte[] buffer = new byte[8192];
						while (is.read(buffer) > 0) {
							// Reading data triggers signature checks
						}
					}
				}
				
				// If we got here without exception, signature is valid
				LOGGER.info("JAR signature verified successfully on client side");
				return Optional.of(true);
			} catch (IOException e) {
				LOGGER.error("JAR signature verification FAILED - JAR was tampered with: {}", e.getMessage());
				return Optional.of(false);
			}
		} catch (Exception e) {
			LOGGER.error("Error verifying JAR signature locally: {}", e.getMessage());
			return Optional.of(false);
		}
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