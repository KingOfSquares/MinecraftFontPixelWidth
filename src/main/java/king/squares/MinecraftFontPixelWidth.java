package king.squares;

import com.github.javaparser.ast.Node;
import com.github.javaparser.printer.PrettyPrinterConfiguration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.RawGlyph;
import it.unimi.dsi.fastutil.ints.Int2BooleanArrayMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.providers.GlyphProviderBuilderType;
import net.minecraft.client.gui.font.providers.LegacyUnicodeBitmapsProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

public final class MinecraftFontPixelWidth {

  private MinecraftFontPixelWidth() {
  }

  private static final String FILE_NAME = "src/main/java/king/squares/FontWidthFunction.java";
  private static final Logger logger = Logger.getLogger("king.squares.FontWidthCalculator");
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static final SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(PackType.CLIENT_RESOURCES);
  private static final List<MojangGlyphProvider> glyphProviders = new ArrayList<>();

  public static void main(String[] args) throws IOException {
    args = new String[]{"-g", "ascii"};

    configureLogging();
    populateResourceManager();
    final InputStream defaultJson = findProviderJson();
    logger.info("Provider document chosen and validated");
    final InputStreamReader fileReader = new InputStreamReader(defaultJson);
    final JsonObject jsonArrayObject = GsonHelper.fromJson(gson, fileReader, JsonObject.class);
    if (jsonArrayObject == null) {
      logger.severe("Error reading default.json");
      System.exit(1);
    }
    final JsonArray providers = GsonHelper.getAsJsonArray(jsonArrayObject, "providers");
    logger.info("Found " + providers.size() + " glyph providers:");
    for (int i = providers.size() - 1; i >= 0; --i) {
      final JsonObject provider = GsonHelper.convertToJsonObject(providers.get(i), "providers[" + i + "]");
      final String type = GsonHelper.getAsString(provider, "type");
      final GlyphProvider glyphs;
      final String file;
      if (type.equals("legacy_unicode")) {
        final ResourceLocation sizesBinLocation = ResourceLocation.tryParse(GsonHelper.getAsString(provider, "sizes"));
        if (sizesBinLocation == null) {
          logger.severe("Unable to find sizes bin for legacy_unicode provider");
          continue;
        }
        final Resource sizesBin = manager.getResource(sizesBinLocation);
        glyphs = new LegacyUnicodeBitmapsProvider(manager, sizesBin.getInputStream().readAllBytes(), GsonHelper.getAsString(provider, "template"));
        file = GsonHelper.getAsString(provider, "sizes");
      } else {
        glyphs = GlyphProviderBuilderType.byName(type).create(provider).create(manager);
        file = GsonHelper.getAsString(provider, "file");
      }
      logger.info("Parsed glyph provider: " + file + "(" + type + ")");
      glyphProviders.add(new MojangGlyphProvider(glyphs, glyphs.getSupportedGlyphs(), file));
    }

    final Map<Float, List<@NotNull Integer>> sizes = makePreppedMap();
    //If TRUE then bitmap (e.g 1.0F), false is legacy_unicode eg (0.5f)
    final Int2BooleanArrayMap boldOffsets = new Int2BooleanArrayMap();

    populateSizes(sizes, boldOffsets, parseGlyphSets(args));

    sizes.forEach((f, l) -> l.sort(Comparator.comparingInt(Integer::intValue)));

    final File delete = new File(FILE_NAME);
    if (delete.isFile()) logger.info("Deleted old " + FILE_NAME + ": " + delete.delete());

    configureJavaparserPrinting();

    logger.info("Creating FontWidthFunction code generator...");
    final FontWidthFunctionGenerator generator = new FontWidthFunctionGenerator(sizes, boldOffsets, false); //TODO: option to use new switch features
    logger.info("Generating FontWidthFunction code...");
    final String clazzText = generator.makeClass();
    logger.info("Writing new code to the FontWidthFunction.java file");
    final FileWriter stream = new FileWriter(FILE_NAME, Charset.defaultCharset());
    stream.append(clazzText);
    stream.close();
  }

  private static void configureLogging() {
    final Handler customHandler = new ConsoleHandler();
    customHandler.setFormatter(new CustomLogFormatter());
    logger.setUseParentHandlers(false);
    logger.addHandler(customHandler);
  }

  private static void populateResourceManager() {
    final String assetsFolder = System.getProperty("king.squares.assetsFolder");
    logger.info("Choosing where to search for assets...");
    if (assetsFolder == null) {
      logger.info("Custom assets folder path not found, using vanilla assets");
      //Magic number "7" stolen from DetectedVersion.java
      final PackMetadataSection meta = new PackMetadataSection(new TextComponent("dummy"), 7);
      manager.add(new VanillaPackResources(meta, "minecraft", "realms"));
    } else {
      logger.info("Custom assets folder path found!(" + assetsFolder + ")");
      final File folderWithAssets = new File(assetsFolder);
      manager.add(new FolderPackResources(folderWithAssets));
    }
  }

  private static InputStream findProviderJson() throws FileNotFoundException {
    final String providerJson = System.getProperty("king.squares.providerJson");
    logger.info("Choosing which provider document to use...");
    if (providerJson == null) {
      logger.info("Custom provider document path not found, using default.json");
      final InputStream defaultJson = Minecraft.class.getResourceAsStream("/assets/minecraft/font/default.json");
      if (defaultJson == null) {
        logger.severe("Error finding default.json in decompiled client");
        System.exit(1);
      }
      return defaultJson;
    } else {
      logger.info("Custom provider document path found!(" + providerJson + ")");
      final File providerJsonFile = new File(providerJson);
      if (!providerJsonFile.exists() || !providerJsonFile.isFile() || !providerJsonFile.canRead() || !providerJsonFile.getName().endsWith(".json")) {
        logger.severe("Error reading " + providerJson);
        System.exit(1);
      }
      return new FileInputStream(providerJson);
    }
  }

  private static void populateSizes(final Map<Float, List<Integer>> sizes, final Int2BooleanArrayMap boldOffsets, final EnumSet<GlyphSet> flags) {
    logger.info("Fetching sizes for all requested glyphs...");
    for (final GlyphSet flag : flags) {
      if (flag.supportedCodepoints() != null) {
        final List<Integer> relevantCodepoints = flag.supportedCodepoints();
        for (final MojangGlyphProvider provider : glyphProviders) {
          for (final int relevantCodepoint : relevantCodepoints) {
            if (provider.getSupportedGlyphs().contains(relevantCodepoint)) {
              final RawGlyph glyph = provider.getGlyph(relevantCodepoint);
              sizes.get(glyph.getAdvance()).add(relevantCodepoint);
              boldOffsets.put(relevantCodepoint, glyph.getBoldOffset() == 1.0F);
            }
          }
        }
      }
      if (flag.glyphProviderName() != null) {
        for (final MojangGlyphProvider provider : glyphProviders) {
          if (flag.glyphProviderName().equals(provider.providerName())) {
            for (final int relevantCodepoint : provider.getSupportedGlyphs()) {
              final RawGlyph glyph = provider.getGlyph(relevantCodepoint);
              sizes.get(glyph.getAdvance()).add(relevantCodepoint);
              boldOffsets.put(relevantCodepoint, glyph.getBoldOffset() == 1.0F);
            }
          }
        }
      }
    }
  }

  private static Map<Float, List<@NotNull Integer>> makePreppedMap() {
    final Map<Float, List<@NotNull Integer>> prepped = new HashMap<>();
    for (float i = 0; i < 16; i++) {
      prepped.put(i, new LinkedList<>());
    }

    return prepped;
  }

  private static EnumSet<GlyphSet> parseGlyphSets(final String[] args) {
    final EnumSet<GlyphSet> set = EnumSet.noneOf(GlyphSet.class);
    boolean nextArgIsFlag = false;
    for (final String arg : args) {
      if (!arg.equals("-g") && !nextArgIsFlag) continue;
      else if (!nextArgIsFlag) {
        nextArgIsFlag = true;
        continue;
      }
      try {
        set.add(GlyphSet.valueOf(arg.toUpperCase()));
        logger.info("Parsed glyph set: " + arg.toUpperCase());
      } catch (final IllegalArgumentException e) {
        logger.warning("Skipped over wrong argument: " + arg);
      } finally {
        nextArgIsFlag = false;
      }
    }
    return set;
  }

  private static void configureJavaparserPrinting() {
    final PrettyPrinterConfiguration conf = Node.getToStringPrettyPrinterConfiguration();
    conf.setIndentSize(2);
    conf.setTabWidth(2);
  }
}
