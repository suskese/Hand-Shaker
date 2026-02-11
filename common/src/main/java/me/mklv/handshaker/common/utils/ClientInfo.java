package me.mklv.handshaker.common.utils;

import java.util.Set;

public record ClientInfo(
	boolean fabric,
	Set<String> mods,
	boolean signatureVerified,
	boolean veltonVerified,
	String modListNonce,
	String integrityNonce,
	String veltonNonce,
	boolean checked
) {

	public ClientInfo(Set<String> mods,
					  boolean signatureVerified,
					  boolean veltonVerified,
					  String modListNonce,
					  String integrityNonce,
					  String veltonNonce) {
		this(false, mods, signatureVerified, veltonVerified, modListNonce, integrityNonce, veltonNonce, false);
	}

	public ClientInfo withChecked(boolean checked) {
		return new ClientInfo(fabric, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, checked);
	}

	public ClientInfo withMods(Set<String> mods) {
		return new ClientInfo(fabric, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, checked);
	}

	public ClientInfo withFabric(boolean fabric) {
		return new ClientInfo(fabric, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, checked);
	}

	public ClientInfo withSignatureVerified(boolean signatureVerified) {
		return new ClientInfo(fabric, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, checked);
	}

	public ClientInfo withVeltonVerified(boolean veltonVerified) {
		return new ClientInfo(fabric, mods, signatureVerified, veltonVerified,
			modListNonce, integrityNonce, veltonNonce, checked);
	}
}
