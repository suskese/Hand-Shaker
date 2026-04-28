package me.mklv.handshaker.common.protocols;

import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public final class CertLoader {
	public interface LogSink {
		void info(String message);
		void warn(String message);
	}

	private CertLoader() {
	}

	public static PublicKey loadPublicKey(Class<?> resourceBase, String resourcePath, LogSink logger) {
		if (resourceBase == null) {
			return null;
		}
		try (InputStream certStream = resourceBase.getResourceAsStream(resourcePath)) {
			return loadFromStream(certStream, logger);
		} catch (Exception e) {
			if (logger != null) {
				logger.warn("Failed to load public certificate: " + e.getMessage());
				logger.warn("Signature verification will be disabled.");
			}
			return null;
		}
	}

	public static PublicKey loadPublicKey(ClassLoader classLoader, String resourcePath, LogSink logger) {
		if (classLoader == null) {
			return null;
		}
		try (InputStream certStream = classLoader.getResourceAsStream(resourcePath)) {
			return loadFromStream(certStream, logger);
		} catch (Exception e) {
			if (logger != null) {
				logger.warn("Failed to load public certificate: " + e.getMessage());
				logger.warn("Signature verification will be disabled.");
			}
			return null;
		}
	}

	private static PublicKey loadFromStream(InputStream certStream, LogSink logger) throws Exception {
		if (certStream == null) {
			if (logger != null) {
				logger.warn("public.cer not found in resources. Signature verification will be disabled.");
				logger.warn("Mods signed with any certificate will be accepted.");
			}
			return null;
		}

		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate cert = cf.generateCertificate(certStream);
		PublicKey publicKey = cert.getPublicKey();
		if (logger != null) {
			logger.info("Loaded public certificate for signature verification");
		}
		return publicKey;
	}
}
