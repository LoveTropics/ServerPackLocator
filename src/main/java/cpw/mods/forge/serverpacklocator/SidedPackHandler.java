package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.io.IOException;
import java.nio.file.Path;

public abstract class SidedPackHandler implements IModLocator {
    protected final Path serverModsDir;
    private final FileConfig packConfig;
    private final boolean isValid;

    protected SidedPackHandler(final Path serverModsDir) {
        this.serverModsDir = serverModsDir;
        this.packConfig = FileConfig
                .builder(serverModsDir.resolve("serverpacklocator.toml"))
                .onFileNotFound(this::handleMissing)
                .build();
        packConfig.load();
        packConfig.close();
        isValid = validateConfig();
        ModAccessor.needsCertificate = !this.isValid;
    }

    protected abstract boolean validateConfig();

    protected abstract boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException;

    public FileConfig getConfig() {
        return packConfig;
    }

    protected boolean isValid() {
        return isValid;
    }
}
