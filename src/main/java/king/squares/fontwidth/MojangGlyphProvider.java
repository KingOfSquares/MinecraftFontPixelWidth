package king.squares.fontwidth;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.RawGlyph;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MojangGlyphProvider(GlyphProvider glyphProvider, IntSet supportedCodepoints,
                                  String providerName) implements GlyphProvider {

  @Override
  public void close() {
    this.glyphProvider.close();
  }

  @Override
  public @Nullable RawGlyph getGlyph(final int codepoint) {
    return this.glyphProvider.getGlyph(codepoint);
  }

  @Override
  public @NotNull IntSet getSupportedGlyphs() {
    return this.supportedCodepoints;
  }

  @Override
  public String toString() {
    return "MojangGlyphProvider{" +
        "glyphProvider=" + this.glyphProvider +
        ", supportedCodepoints=" + this.supportedCodepoints +
        ", providerName='" + this.providerName + '\'' +
        '}';
  }
}
