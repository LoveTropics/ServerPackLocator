package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public ClientSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultclientconfig.toml")), path);
        return true;
    }

    @Override
    protected boolean validateConfig() {
        final Optional<String> remoteServer = getConfig().getOptional("client.remoteServer");
        if (remoteServer.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate remove server address. " +
                    "Repair or delete this file to continue", getConfig().getNioPath().toString());
            throw new IllegalStateException("Invalid configuation file found, please delete or correct");
        }
        return true;
    }

    @Override
    public void findCandidates(final ILaunchContext context, final IDiscoveryPipeline pipeline) {
        if (!isValid()) {
            LOGGER.info("There was a problem with the connection, there will not be any server mods");
            return;
        }

        final List<String> excludedModIds = getConfig().<List<String>>getOptional("client.excludedModIds").orElse(List.of());
        final SimpleHttpClient clientDownloader = new SimpleHttpClient(this, Set.copyOf(excludedModIds), serverModsDir);

        final ServerManifest manifest = clientDownloader.waitForResult();
        if (manifest == null) {
            pipeline.addIssue(ModLoadingIssue.warning("Failed to download server pack! Mods may not be loaded.\nPlease check your internet connection and restart your game, or contact the server administrator if the issue persists."));
            return;
        }

        final Set<String> manifestFileList = manifest.files().stream()
                .map(ServerManifest.ModFileData::fileName)
                .collect(Collectors.toSet());
        discoverModFiles(pipeline, manifestFileList::contains);
    }

    private void discoverModFiles(final IDiscoveryPipeline pipeline, final Predicate<String> fileNamePredicate) {
        final List<Path> directoryContent;
        try (final Stream<Path> files = Files.list(serverModsDir)) {
            directoryContent = files
                    .filter(path -> fileNamePredicate.test(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (final UncheckedIOException | IOException e) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failed_to_list_folder_content", serverModsDir)
                    .withAffectedPath(serverModsDir)
                    .withCause(e)
            );
        }

        for (final Path file : directoryContent) {
            if (!Files.isRegularFile(file)) {
                pipeline.addIssue(ModLoadingIssue.warning("fml.modloadingissue.brokenfile.unknown").withAffectedPath(file));
                continue;
            }
            pipeline.addPath(file, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS);
        }
    }
}
