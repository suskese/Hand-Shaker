package me.mklv.handshaker.neoforge;

import me.mklv.handshaker.common.loader.CommonClientHashPayloadService;
import me.mklv.handshaker.common.loader.CommonClientPayloadRuntime;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ClientHashPayloadService {
    public record ModListData(String transportPayload, String modListHash) {}
    public record IntegrityData(byte[] signature, String jarHash) {}

    private final CommonClientPayloadRuntime runtime = new CommonClientPayloadRuntime();
    private final CommonClientPayloadRuntime.Context context = new CommonClientPayloadRuntime.Context() {
        @Override
        public Collection<CommonClientHashPayloadService.ModDescriptor> collectMods() {
            return ClientHashPayloadService.this.collectMods();
        }

        @Override
        public String runtimeModId() {
            return HandShakerClientMod.MOD_ID;
        }

        @Override
        public String wireModId() {
            return "hand-shaker";
        }

        @Override
        public Class<?> integrityAnchorClass() {
            return HandShakerClientMod.class;
        }

        @Override
        public CommonClientHashPayloadService.LogSink logSink() {
            return new CommonClientHashPayloadService.LogSink() {
                @Override
                public void info(String message) {
                    HandShakerClientMod.LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    HandShakerClientMod.LOGGER.warn(message);
                }
            };
        }
    };

    public void precomputeAtBoot() {
        runtime.precomputeAtBoot(context);
    }

    public ModListData getOrBuildModListData() {
        CommonClientHashPayloadService.ModListData data = runtime.getOrBuildModListData(context);
        return new ModListData(data.transportPayload(), data.modListHash());
    }

    public IntegrityData getOrBuildIntegrityData() {
        CommonClientHashPayloadService.IntegrityData data = runtime.getOrBuildIntegrityData(context);
        return new IntegrityData(data.signature(), data.jarHash());
    }

    public ModListData buildModListDataManual() {
        CommonClientHashPayloadService.ModListData data = runtime.buildModListDataManual(context);
        return new ModListData(data.transportPayload(), data.modListHash());
    }

    public IntegrityData buildIntegrityDataManual() {
        CommonClientHashPayloadService.IntegrityData data = runtime.buildIntegrityDataManual(context);
        return new IntegrityData(data.signature(), data.jarHash());
    }

    private Collection<CommonClientHashPayloadService.ModDescriptor> collectMods() {
        List<CommonClientHashPayloadService.ModDescriptor> mods = ModList.get().getMods().stream()
            .map(this::toDescriptor)
            .toList();
        return mods;
    }

    private Optional<String> invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? Optional.empty() : Optional.of(value.toString());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<Path> resolveModFilePath(Object modInfo) {
        try {
            Object owningFile = modInfo.getClass().getMethod("getOwningFile").invoke(modInfo);
            if (owningFile == null) {
                return Optional.empty();
            }
            Object modFile = owningFile.getClass().getMethod("getFile").invoke(owningFile);
            if (modFile == null) {
                return Optional.empty();
            }
            Object filePath = modFile.getClass().getMethod("getFilePath").invoke(modFile);
            if (filePath instanceof Path path) {
                return Optional.of(path);
            }
            if (filePath != null) {
                return Optional.of(Paths.get(filePath.toString()));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private CommonClientHashPayloadService.ModDescriptor toDescriptor(Object modInfo) {
        return new CommonClientHashPayloadService.ModDescriptor(
            invokeString(modInfo, "getModId").orElse("unknown"),
            invokeString(modInfo, "getDisplayName").orElse("null"),
            invokeString(modInfo, "getVersion").orElse("unknown"),
            resolveModFilePath(modInfo).orElse(null)
        );
    }
}