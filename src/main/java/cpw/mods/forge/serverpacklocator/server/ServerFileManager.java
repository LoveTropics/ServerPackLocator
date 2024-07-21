package cpw.mods.forge.serverpacklocator.server;

import com.google.common.hash.HashCode;
import cpw.mods.forge.serverpacklocator.DirHandler;
import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.PackBuilder;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ServerFileManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path manifestPath;
    private final List<Path> modRoots;
    private final String forgeVersion;
    private Set<String> filesInManifest = Set.of();
    @Nullable
    private String manifestJson;

    ServerFileManager(final Path manifestPath, final List<Path> modRoots, final String forgeVersion) {
        this.manifestPath = manifestPath;
        this.modRoots = modRoots;
        this.forgeVersion = forgeVersion;
    }

    String getManifestJson() {
        return Objects.requireNonNull(manifestJson, "Manifest has not been initialized");
    }

    @Nullable
    byte[] findFile(final String fileName) {
        try {
            Path path = findFilePath(fileName);
            if (path == null) {
                LOGGER.warn("Requested mod file not in servermods directory: {}", fileName);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            LOGGER.warn("Failed to read file {}", fileName, e);
            return null;
        }
    }

    @Nullable
    private Path findFilePath(final String fileName) {
        if (!filesInManifest.contains(fileName)) {
            return null;
        }
        for (final Path root : modRoots) {
            final Path path = DirHandler.resolveDirectChild(root, fileName);
            if (path != null && Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    void buildManifest(final List<IModFile> files) {
        final ServerManifest manifest = generateManifest(files);
        manifestJson = manifest.toJson();
        filesInManifest = manifest.files().stream().map(ServerManifest.ModFileData::fileName).collect(Collectors.toSet());

        // We never use the serialised file, but some setups expose the manifest through an external HTTP server
        manifest.save(manifestPath);
    }

    private ServerManifest generateManifest(final List<IModFile> modList) {
        LOGGER.debug("Generating manifest");

        final ServerManifest.Builder manifest = new ServerManifest.Builder()
                .setForgeVersion(forgeVersion);

        for (final IModFile file : modList) {
            final HashCode checksum = FileChecksumValidator.computeChecksumFor(file.getFilePath());
            if (checksum == null) {
                throw new IllegalArgumentException("Invalid checksum for file " + file.getFileName());
            }
            manifest.add(PackBuilder.getRootModId(file), checksum, file.getFileName());
        }

        return manifest.build();
    }
}
