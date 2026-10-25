package com.gtnewhorizons.angelica.textures.atlas;

import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Flat atlas lookup: one array index per UV instead of a descent through the quad tree.
 * The cell is the largest power of two dividing every sprite slot, so no cell can straddle two sprites.
 */
public final class SpriteGrid<T> {

    private static final int MAX_CELLS = 1 << 22;

    private final Object[] cells;
    private final int shift;
    private final int width;
    private final int height;

    private SpriteGrid(Object[] cells, int shift, int width, int height) {
        this.cells = cells;
        this.shift = shift;
        this.width = width;
        this.height = height;
    }

    /** Returns null when the slots share no grid coarser than a texel; the caller keeps its quad tree then. */
    public static <T> SpriteGrid<T> build(int atlasWidth, int atlasHeight, Collection<T> sprites, Function<T, Rect2i> boundsOf, ToIntFunction<T> gutterOf) {
        if (sprites.isEmpty() || atlasWidth <= 0 || atlasHeight <= 0) return null;

        // Each slot includes the sprite's gutter. Bare origins sit one gutter off the slot grid, which drops the divisor to 1 at mip 0.
        final Function<T, Rect2i> slotOf = sprite -> {
            final Rect2i bounds = boundsOf.apply(sprite);
            final int gutter = gutterOf.applyAsInt(sprite);
            return new Rect2i(bounds.x() - gutter, bounds.y() - gutter, bounds.width() + 2 * gutter, bounds.height() + 2 * gutter);
        };

        int divisor = gcd(atlasWidth, atlasHeight);
        for (T sprite : sprites) {
            final Rect2i slot = slotOf.apply(sprite);
            divisor = gcd(divisor, slot.x());
            divisor = gcd(divisor, slot.y());
            divisor = gcd(divisor, slot.width());
            divisor = gcd(divisor, slot.height());
            if (divisor <= 1) return null;
        }

        final int shift = Integer.numberOfTrailingZeros(divisor);
        final int width = atlasWidth >> shift;
        final int height = atlasHeight >> shift;
        if ((long) width * height > MAX_CELLS) return null;

        final Object[] cells = new Object[width * height];
        for (T sprite : sprites) {
            final Rect2i slot = slotOf.apply(sprite);
            final int fromX = slot.x() >> shift;
            final int fromY = slot.y() >> shift;
            final int toX = Math.min(width, fromX + (slot.width() >> shift));
            final int toY = Math.min(height, fromY + (slot.height() >> shift));
            for (int gy = fromY; gy < toY; gy++) {
                for (int gx = fromX; gx < toX; gx++) {
                    cells[gy * width + gx] = sprite;
                }
            }
        }
        return new SpriteGrid<>(cells, shift, width, height);
    }

    @SuppressWarnings("unchecked")
    public T find(int x, int y) {
        final int gx = x >> shift;
        final int gy = y >> shift;
        if (x < 0 || y < 0 || gx >= width || gy >= height) return null;
        return (T) cells[gy * width + gx];
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            final int rem = a % b;
            a = b;
            b = rem;
        }
        return a;
    }
}
