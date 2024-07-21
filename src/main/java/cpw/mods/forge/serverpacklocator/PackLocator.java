package cpw.mods.forge.serverpacklocator;

import com.mojang.logging.LogUtils;
import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import cpw.mods.modlauncher.api.IEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.slf4j.Logger;

import java.net.URL;
import java.nio.file.Path;

public class PackLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void findCandidates(final ILaunchContext context, final IDiscoveryPipeline pipeline) {
        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        LOGGER.info("Loading server pack locator. Version {} from {}", getClass().getPackage().getImplementationVersion(), url);

        final Path gameDir = context.environment().getProperty(IEnvironment.Keys.GAMEDIR.get()).orElseGet(() -> Path.of("."));
        final Dist dist = context.environment().getProperty(Environment.Keys.DIST.get()).orElse(Dist.CLIENT);

        final Path serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        final SidedPackHandler sidedLocator = switch (dist) {
            case CLIENT -> new ClientSidedPackHandler(serverModsPath);
            case DEDICATED_SERVER -> {
                final Path clientMods = DirHandler.createOrGetDirectory(gameDir, "clientmods");
                yield new ServerSidedPackHandler(serverModsPath, clientMods);
            }
        };
        if (!sidedLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }

        sidedLocator.findCandidates(context, pipeline);
    }

    @Override
    public String toString() {
        return "serverpacklocator";
    }
}
