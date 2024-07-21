package cpw.mods.forge.serverpacklocator;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public record ServerManifest(List<ModFileData> files) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Codec<ServerManifest> CODEC = RecordCodecBuilder.create(i -> i.group(
            ModFileData.CODEC.listOf().fieldOf("files").forGetter(ServerManifest::files)
    ).apply(i, ServerManifest::new));

    public static DataResult<ServerManifest> parse(final String string) {
        JsonElement json = JsonParser.parseString(string);
        return CODEC.parse(JsonOps.INSTANCE, json);
    }

    public String toJson() {
        JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, this).result().orElseThrow();
        return GSON.toJson(json);
    }

    public static DataResult<ServerManifest> load(final Path path) {
        try (BufferedReader json = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(final Path path) {
        try (final BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record ModFileData(String rootModId, HashCode checksum, String fileName) {
        private static final Codec<HashCode> HASH_CODE_CODEC = Codec.STRING.comapFlatMap(
                string -> {
                    try {
                        return DataResult.success(HashCode.fromString(string.toLowerCase(Locale.ROOT)));
                    } catch (Exception e) {
                        return DataResult.error(e::getMessage);
                    }
                },
                hash -> hash.toString().toUpperCase(Locale.ROOT)
        );

        public static final Codec<ModFileData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("rootModId").forGetter(ModFileData::rootModId),
                HASH_CODE_CODEC.fieldOf("checksum").forGetter(ModFileData::checksum),
                Codec.STRING.fieldOf("fileName").forGetter(ModFileData::fileName)
        ).apply(i, ModFileData::new));
    }

    public static class Builder {
        private final ImmutableList.Builder<ModFileData> mods = ImmutableList.builder();

        public Builder add(final String rootId, final HashCode checksum, final String fileName) {
            mods.add(new ModFileData(rootId, checksum, fileName));
            return this;
        }

        public ServerManifest build() {
            return new ServerManifest(mods.build());
        }
    }
}
