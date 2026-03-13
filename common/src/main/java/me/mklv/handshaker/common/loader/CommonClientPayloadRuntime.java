package me.mklv.handshaker.common.loader;

import java.util.Collection;

public final class CommonClientPayloadRuntime {
    public interface Context {
        Collection<CommonClientHashPayloadService.ModDescriptor> collectMods();

        String runtimeModId();

        String wireModId();

        Class<?> integrityAnchorClass();

        CommonClientHashPayloadService.LogSink logSink();
    }

    private final CommonClientHashPayloadService service = new CommonClientHashPayloadService();
    private volatile CommonClientHashPayloadService.ModListData cachedModList;
    private volatile CommonClientHashPayloadService.IntegrityData cachedIntegrity;

    public void precomputeAtBoot(Context context) {
        cachedModList = buildModListDataManual(context);
        cachedIntegrity = buildIntegrityDataManual(context);
    }

    public CommonClientHashPayloadService.ModListData getOrBuildModListData(Context context) {
        CommonClientHashPayloadService.ModListData value = cachedModList;
        if (value != null) {
            return value;
        }

        CommonClientHashPayloadService.ModListData computed = buildModListDataManual(context);
        cachedModList = computed;
        return computed;
    }

    public CommonClientHashPayloadService.IntegrityData getOrBuildIntegrityData(Context context) {
        CommonClientHashPayloadService.IntegrityData value = cachedIntegrity;
        if (value != null) {
            return new CommonClientHashPayloadService.IntegrityData(value.signature().clone(), value.jarHash());
        }

        CommonClientHashPayloadService.IntegrityData computed = buildIntegrityDataManual(context);
        cachedIntegrity = new CommonClientHashPayloadService.IntegrityData(computed.signature().clone(), computed.jarHash());
        return computed;
    }

    public CommonClientHashPayloadService.ModListData buildModListDataManual(Context context) {
        return service.buildModListData(
            context.collectMods(),
            context.runtimeModId(),
            context.wireModId(),
            true
        );
    }

    public CommonClientHashPayloadService.IntegrityData buildIntegrityDataManual(Context context) {
        return service.buildIntegrityData(context.integrityAnchorClass(), context.logSink());
    }
}