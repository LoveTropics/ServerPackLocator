package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class SidedPackHandler {
    private final Path serverModsDir;
    private final FileConfig packConfig;
    private final boolean isValid;
    private String forgeVersion = "UNKNOWN";
    private String mcVersion = "UNKNOWN";

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

    public static SidedPackHandler buildFor(Dist side, final Path serverModsPath) {
        return switch (side) {
            case CLIENT -> new ClientSidedPackHandler(serverModsPath);
            case DEDICATED_SERVER -> new ServerSidedPackHandler(serverModsPath);
        };
    }

    protected abstract boolean validateConfig();

    protected abstract boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException;

    public FileConfig getConfig() {
        return packConfig;
    }

    public Path getServerModsDir() {
        return serverModsDir;
    }

    protected boolean isValid() {
        return isValid;
    }

    protected abstract List<IModFile> processModList(final List<IModFile> scannedMods);

    public abstract void initialize(final IModLocator dirLocator);

    protected abstract boolean waitForDownload();

    public String getForgeVersion()
    {
        return forgeVersion;
    }

    public void setForgeVersion(final String forgeVersion)
    {
        this.forgeVersion = forgeVersion;
    }

    public String getMcVersion()
    {
        return mcVersion;
    }

    public void setMcVersion(final String mcVersion)
    {
        this.mcVersion = mcVersion;
    }
}
