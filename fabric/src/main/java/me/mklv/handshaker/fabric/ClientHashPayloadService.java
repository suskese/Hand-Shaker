package me.mklv.handshaker.fabric;

import me.mklv.handshaker.common.loader.CommonClientHashPayloadService;
import me.mklv.handshaker.common.loader.CommonClientPayloadRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
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
            return HandShaker.MOD_ID;
        }

        @Override
        public String wireModId() {
            return "hand-shaker";
        }

        @Override
        public Class<?> integrityAnchorClass() {
            return HandShaker.class;
        }

        @Override
        public CommonClientHashPayloadService.LogSink logSink() {
            return new CommonClientHashPayloadService.LogSink() {
                @Override
                public void info(String message) {
                    HandShaker.LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    HandShaker.LOGGER.warn(message);
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
        List<CommonClientHashPayloadService.ModDescriptor> mods = FabricLoader.getInstance().getAllMods().stream()
            .map(this::toDescriptor)
            .toList();
        return mods;
    }

    private Optional<Path> safeFirstOriginPath(ModContainer modContainer) {
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

    private CommonClientHashPayloadService.ModDescriptor toDescriptor(ModContainer modContainer) {
        return new CommonClientHashPayloadService.ModDescriptor(
            modContainer.getMetadata().getId(),
            modContainer.getMetadata().getName(),
            modContainer.getMetadata().getVersion().getFriendlyString(),
            safeFirstOriginPath(modContainer).orElse(null)
        );
    }
}