package me.mklv.handshaker.common.utils;

import java.util.Set;

public record ClientInfo(
	boolean hasHandshakeClient,
	Set<String> mods,
	boolean signatureVerified,
	boolean veltonVerified,
	String modListNonce,
	String integrityNonce,
	String veltonNonce,
	boolean handshakeChecked
) {

	public ClientInfo(Set<String> mods,
					  boolean signatureVerified,
					  boolean veltonVerified,
					  String modListNonce,
					  String integrityNonce,
					  String veltonNonce) {
		this(false, mods, signatureVerified, veltonVerified, modListNonce, integrityNonce, veltonNonce, false);
	}

	public ClientInfo withHandshakeChecked(boolean handshakeChecked) {
		return new ClientInfo(hasHandshakeClient, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, handshakeChecked);
	}

	@Deprecated
	public ClientInfo withChecked(boolean checked) {
		return withHandshakeChecked(checked);
	}

	public ClientInfo withMods(Set<String> mods) {
		return new ClientInfo(hasHandshakeClient, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, handshakeChecked);
	}

	public ClientInfo withHasHandshakeClient(boolean hasHandshakeClient) {
		return new ClientInfo(hasHandshakeClient, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, handshakeChecked);
	}

	@Deprecated
	public ClientInfo withFabric(boolean fabric) {
		return withHasHandshakeClient(fabric);
	}

	public ClientInfo withSignatureVerified(boolean signatureVerified) {
		return new ClientInfo(hasHandshakeClient, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, handshakeChecked);
	}

	public ClientInfo withVeltonVerified(boolean veltonVerified) {
		return new ClientInfo(hasHandshakeClient, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, handshakeChecked);
	}
}
