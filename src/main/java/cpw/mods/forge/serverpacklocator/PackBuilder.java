package cpw.mods.forge.serverpacklocator;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<String> excludedModIds;

    public PackBuilder(final Set<String> excludedModIds) {
        this.excludedModIds = excludedModIds;
    }

    public static String getRootModId(final IModFile modFile) {
        final List<IModInfo> modInfos = modFile.getModInfos();
        return modFile.getType() == IModFile.Type.MOD && !modInfos.isEmpty() ? modInfos.getFirst().getModId() : modFile.getFileName();
    }

    public List<IModFile> buildModList(final List<IModFile> files) {
        final Map<String, List<IModFile>> filesByRootId = files.stream().collect(Collectors.groupingBy(PackBuilder::getRootModId));
        excludedModIds.forEach(filesByRootId::remove);

        return filesByRootId.entrySet().stream()
                .flatMap(this::selectNewest)
                .toList();
    }

    private Stream<IModFile> selectNewest(final Map.Entry<String, List<IModFile>> entry) {
        List<IModFile> files = entry.getValue();
        if (files.isEmpty()) {
            return Stream.empty();
        } else if (files.size() == 1) {
            return Stream.of(files.getFirst());
        }
        if (!files.stream().allMatch(file -> file.getType() == IModFile.Type.MOD)) {
            return files.stream();
        }

        LOGGER.debug("Selecting newest by artifact version for modid {}", entry.getKey());
        IModFile newestFile = files.stream()
                .max(Comparator.comparing(PackBuilder::getRootVersion))
                .orElseThrow();
        LOGGER.debug("Newest file by artifact version for modid {} is {} ({})", entry.getKey(), newestFile.getFileName(), getRootVersion(newestFile));
        return Stream.of(newestFile);
    }

    private static ArtifactVersion getRootVersion(IModFile file) {
        return file.getModInfos().getFirst().getVersion();
    }

}
