package me.mklv.handshaker.common.api.module;

import org.slf4j.Logger;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers and instantiates {@link HandShakerModule} implementations from JARs
 * placed in a modules directory.
 *
 * <p>Each JAR must declare its entry point via Java's ServiceLoader mechanism:
 * {@code META-INF/services/me.mklv.handshaker.common.api.module.HandShakerModule}
 *
 * <p>This loader only instantiates modules — it does NOT call
 * {@link HandShakerModule#onEnable(ModuleContext)}. The caller is responsible for
 * building the context and managing lifecycle.
 */
public final class ModuleLoader {
    private ModuleLoader() {}

    /**
     * Scans {@code modulesDir} for {@code *.jar} files and loads any
     * {@link HandShakerModule} services found inside them.
     *
     * @param modulesDir directory to scan (silently skipped if it doesn't exist)
     * @param parent     parent classloader — typically the base plugin/mod classloader,
     *                   so modules can see all common API classes
     * @param logger     used for load/error messages
     * @return discovered module instances, ready for {@code onEnable}
     */
    public static List<HandShakerModule> loadFrom(Path modulesDir, ClassLoader parent, Logger logger) {
        List<HandShakerModule> result = new ArrayList<>();
        if (!Files.isDirectory(modulesDir)) {
            return result;
        }
        File[] jars = modulesDir.toFile().listFiles(f -> {
            if (!f.isFile()) {
                return false;
            }
            String name = f.getName();
            return name.endsWith(".jar")
                    && !name.endsWith("-sources.jar")
                    && !name.endsWith("-javadoc.jar");
        });
        if (jars == null || jars.length == 0) {
            return result;
        }
        Arrays.sort(jars, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File jar : jars) {
            try {
                URLClassLoader cl = new URLClassLoader(
                        new java.net.URL[]{jar.toURI().toURL()}, parent);
                ServiceLoader<HandShakerModule> sl = ServiceLoader.load(HandShakerModule.class, cl);
                int before = result.size();
                for (HandShakerModule module : sl) {
                    result.add(module);
                    logger.info("[ModuleLoader] Loaded module '{}' from {}", module.getId(), jar.getName());
                }
                if (result.size() == before) {
                    logger.warn("[ModuleLoader] {} contains no HandShakerModule services — skipping", jar.getName());
                }
            } catch (Throwable t) {
                logger.error("[ModuleLoader] Failed to load {}: {}", jar.getName(), t.toString());
            }
        }
        return result;
    }
}
