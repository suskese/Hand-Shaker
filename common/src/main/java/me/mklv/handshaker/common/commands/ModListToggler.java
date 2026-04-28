package me.mklv.handshaker.common.commands;

import java.nio.file.Path;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.utils.ModListFiles;

public final class ModListToggler {
    private ModListToggler() {
    }

    public static boolean toggleList(
        Path configDir,
        String listName,
        boolean enable,
        ConfigFileBootstrap.Logger logger
    ) {
        return toggleListDetailed(configDir, listName, enable, logger).status() == ToggleStatus.UPDATED;
    }

    public static ToggleResult toggleListDetailed(
        Path configDir,
        String listName,
        boolean enable,
        ConfigFileBootstrap.Logger logger
    ) {
        Path listFile = ModListFiles.findListFile(configDir, listName);
        if (listFile == null) {
            return new ToggleResult(ToggleStatus.NOT_FOUND, null);
        }

        if (!ModListFiles.setListEnabled(listFile, enable, logger)) {
            return new ToggleResult(ToggleStatus.UPDATE_FAILED, listFile);
        }

        return new ToggleResult(ToggleStatus.UPDATED, listFile);
    }

    public enum ToggleStatus {
        UPDATED,
        NOT_FOUND,
        UPDATE_FAILED
    }

    public record ToggleResult(ToggleStatus status, Path listFile) {
    }
}
