package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import cpw.mods.forge.serverpacklocator.PackBuilder;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.jarhandling.JarContents;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ServerSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path clientModsDir;

    public ServerSidedPackHandler(final Path serverModsDir, final Path clientModsDir) {
        super(serverModsDir);
        this.clientModsDir = clientModsDir;
    }

    @Override
    protected boolean validateConfig() {
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (port.isPresent()) {
            return true;
        } else {
            LOGGER.error("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuration found");
        }
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultserverconfig.toml")), path);
        return true;
    }

    @Override
    public void findCandidates(final ILaunchContext context, final IDiscoveryPipeline pipeline) {
        if (!isValid()) {
            return;
        }

        final FileConfig config = getConfig();
        final int port = config.getOptionalInt("server.port").orElse(8443);
        final Set<String> excludedModIds = Set.copyOf(config.<List<String>>getOptional("server.excludedModIds").orElse(List.of()));

        final SslContext sslContext = buildSslContext(config.get("server.ssl.certificateChainFile"), config.get("server.ssl.keyFile"));

        final Path manifestPath = serverModsDir.resolve("servermanifest.json");
        final List<Path> modRoots = List.of(serverModsDir, clientModsDir);
        final ServerFileManager serverFileManager = new ServerFileManager(manifestPath, modRoots);

        SimpleHttpServer.run(serverFileManager, port, sslContext);

        final PackBuilder packBuilder = new PackBuilder(excludedModIds);

        final List<IModFile> modsToShare = new ArrayList<>();
        final List<IModFile> modsToLoad = new ArrayList<>();

        discoverMods(serverModsDir, pipeline, file -> {
            modsToShare.add(file);
            modsToLoad.add(file);
        });
        discoverMods(clientModsDir, pipeline, modsToShare::add);

        serverFileManager.buildManifest(packBuilder.buildModList(modsToShare));

        for (final IModFile file : packBuilder.buildModList(modsToLoad)) {
            pipeline.addModFile(file);
        }
    }

    private void discoverMods(final Path directory, final IDiscoveryPipeline pipeline, final Consumer<IModFile> consumer) {
        final List<Path> fileList;
        try (final Stream<Path> files = Files.list(directory)) {
            fileList = files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (final UncheckedIOException | IOException e) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failed_to_list_folder_content", directory)
                    .withAffectedPath(directory)
                    .withCause(e));
        }

        for (final Path file : fileList) {
            if (!Files.isRegularFile(file)) {
                pipeline.addIssue(ModLoadingIssue.warning("fml.modloadingissue.brokenfile.unknown").withAffectedPath(file));
                continue;
            }
            try {
                final IModFile modFile = pipeline.readModFile(JarContents.of(file), ModFileDiscoveryAttributes.DEFAULT);
                if (modFile != null) {
                    consumer.accept(modFile);
                }
            } catch (final ModLoadingException e) {
                for (final ModLoadingIssue issue : e.getIssues()) {
                    pipeline.addIssue(issue);
                }
            } catch (final Exception e) {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.brokenfile").withAffectedPath(file).withCause(e));
            }
        }
    }

    @Nullable
    private static SslContext buildSslContext(@Nullable final String certificateChainFile, @Nullable final String keyFile) {
        if (certificateChainFile == null || keyFile == null) {
            return null;
        }
        try (
                final InputStream certificateChain = Files.newInputStream(Path.of(certificateChainFile));
                final InputStream key = Files.newInputStream(Path.of(keyFile))
        ) {
            return SslContextBuilder.forServer(certificateChain, key).build();
        } catch (final Exception e) {
            LOGGER.error("Failed to initialize SSL context for server", e);
        }
        return null;
    }
}
