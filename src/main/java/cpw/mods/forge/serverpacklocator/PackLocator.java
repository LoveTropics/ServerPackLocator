package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final SidedPackHandler sidedLocator;

    public PackLocator() {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
        final Path gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        final Dist dist = LaunchEnvironmentHandler.INSTANCE.getDist();
        final Path serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        sidedLocator = switch (dist) {
            case CLIENT -> new ClientSidedPackHandler(serverModsPath);
            case DEDICATED_SERVER -> {
                final Path clientMods = DirHandler.createOrGetDirectory(gameDir, "clientmods");
                yield new ServerSidedPackHandler(serverModsPath, clientMods);
            }
        };
        if (!sidedLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
    }

    @Override
    public List<ModFileOrException> scanMods() {
        return sidedLocator.scanMods();
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer) {
        sidedLocator.scanFile(modFile, pathConsumer);
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        sidedLocator.initArguments(arguments);

        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        LOGGER.info("Loading server pack locator from: {}", url.toString());
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return sidedLocator.isValid(modFile);
    }
}
