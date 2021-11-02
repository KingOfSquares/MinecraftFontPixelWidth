package king.squares;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

public enum GlyphSet {
  BMP('\u0000', '\uFFFF'),
  SMP(65536, 131071),
  SIP(131071, 196607),
  ASCII("minecraft:font/ascii.png"),
  NONLATIN_EUROPEAN("minecraft:font/nonlatin_european.png"),
  ACCENTED("minecraft:font/accented.png"),
  LEGACY_UNICODE("minecraft:font/glyph_sizes.bin"),
  ALL_PRESENT(Integer.MIN_VALUE, Integer.MAX_VALUE);

  @Nullable
  private final IntList codepoints;
  @Nullable
  private final String glyphProviderName;

  GlyphSet(final Bounds... bounds) {
    this.codepoints = new IntArrayList();
    for (final Bounds bound : bounds) {
      for (int i = bound.lowerBound(); i <= bound.higherBound(); i++) {
        this.codepoints.add(i);
      }
    }
    this.glyphProviderName = null;
  }

  GlyphSet(final char lower, final char higher) {
    this(new Bounds(lower, higher));
  }

  GlyphSet(final int minValue, final int maxValue) {
    this((char) minValue, (char) maxValue);
  }

  GlyphSet(final @Nullable String glyphProviderLocation) {
    this.codepoints = null;
    this.glyphProviderName = glyphProviderLocation;
  }

  public @Nullable IntList supportedCodepoints() {
    return this.codepoints;
  }

  public @Nullable String glyphProviderName() {
    return this.glyphProviderName;
  }

  public static record Bounds(int lowerBound, int higherBound) {}
}
