package cpw.mods.forge.serverpacklocator.client;

import com.google.common.collect.Iterators;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.serialization.DataResult;
import cpw.mods.forge.serverpacklocator.DirHandler;
import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SimpleHttpClient {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactoryBuilder()
            .setNameFormat("ServerPackLocator HTTP Client - %d")
            .setDaemon(true)
            .build());

    private static final String USER_AGENT = "ServerPackLocator (https://github.com/LoveTropics/serverpacklocator)";

    private final HttpClient client = HttpClient.newBuilder()
            .executor(EXECUTOR)
            .build();

    private final Path outputDir;
    private final CompletableFuture<ServerManifest> downloadJob;
    private final Set<String> excludedModIds;

    public SimpleHttpClient(final ClientSidedPackHandler packHandler, final Set<String> excludedModIds, final Path outputDir) {
        this.outputDir = outputDir;
        this.excludedModIds = excludedModIds;

        final Optional<String> remoteServer = packHandler.getConfig().<String>getOptional("client.remoteServer")
                .map(server -> server.endsWith("/") ? server.substring(0, server.length() - 1) : server);
        downloadJob = remoteServer.map(this::connectAndDownload)
                .orElse(CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<ServerManifest> connectAndDownload(final String host) {
        return downloadManifest(host).thenCompose(manifest -> {
            List<ServerManifest.ModFileData> filesToDownload = manifest.files().stream()
                    .filter(file -> !excludedModIds.contains(file.rootModId()))
                    .toList();
            LOGGER.debug("Downloading {} of {} files from manifest", filesToDownload.size(), manifest.files().size());

            return sequential(Iterators.transform(filesToDownload.iterator(), file -> downloadFile(host, file))).thenApply(unused -> {
                LOGGER.debug("Finished downloading files");
                return manifest;
            });
        });
    }

    private static CompletableFuture<?> sequential(final Iterator<CompletableFuture<?>> iterator) {
        if (iterator.hasNext()) {
            return iterator.next().thenCompose(unused -> sequential(iterator));
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<ServerManifest> downloadManifest(final String host) {
        LOGGER.info("Requesting server manifest from: {}", host);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting server manifest from: " + host);

        HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/servermanifest.json"))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(SimpleHttpClient::parseManifest);
    }

    private static ServerManifest parseManifest(HttpResponse<String> response) {
        DataResult<ServerManifest> result = ServerManifest.parse(response.body());
        return result.result().orElseThrow(() -> new IllegalStateException("Manifest was malformed: " + result.error().orElseThrow()));
    }

    private CompletableFuture<?> downloadFile(final String host, final ServerManifest.ModFileData modFile) {
        final Path targetPath = resolvePath(modFile);

        final HashCode existingChecksum = FileChecksumValidator.computeChecksumFor(targetPath);
        if (Objects.equals(modFile.checksum(), existingChecksum)) {
            LOGGER.debug("Found existing file {} - skipping", modFile.fileName());
            return CompletableFuture.completedFuture(null);
        }

        final String fileName = modFile.fileName();
        LOGGER.info("Requesting file: {}", fileName);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting file: " + fileName);

        final URI uri = URI.create(host + "/files/" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20"));
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(targetPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
                .thenAccept(response -> LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Finished downloading file: " + fileName));
    }

    private Path resolvePath(final ServerManifest.ModFileData modFile) {
        final Path path = DirHandler.resolveDirectChild(outputDir, modFile.fileName());
        if (path == null) {
            throw new IllegalStateException("Requested file '" + modFile.fileName() + "' resolved to path outside of servermods directory");
        }
        return path;
    }

    @Nullable
    ServerManifest waitForResult() {
        try {
            return downloadJob.join();
        } catch (Throwable t) {
            LOGGER.error("Encountered an exception while downloading server mods", t);
            return null;
        }
    }
}
