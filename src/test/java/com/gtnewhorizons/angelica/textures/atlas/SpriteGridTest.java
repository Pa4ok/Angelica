package com.gtnewhorizons.angelica.textures.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The sprite grid answers like the quad tree on a padded atlas, and maps a gutter texel to its sprite. */
class SpriteGridTest {

    private static final int ATLAS_WIDTH = 1024;
    private static final int[] SIZES = { 16, 32, 64 };

    private static final class Sprite {
        final Rect2i bounds;
        final int gutter;

        Sprite(Rect2i bounds, int gutter) {
            this.bounds = bounds;
            this.gutter = gutter;
        }

        Rect2i bounds() {
            return bounds;
        }

        int gutter() {
            return gutter;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 4, 16 })
    void matchesQuadTreeOnPaddedAtlas(int gutter) {
        final Random random = new Random(gutter);
        final List<Sprite> sprites = new ArrayList<>();

        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < 200; i++) {
            final int w = SIZES[random.nextInt(SIZES.length)];
            final int h = SIZES[random.nextInt(SIZES.length)];
            final int slotW = w + 2 * gutter, slotH = h + 2 * gutter;
            if (x + slotW > ATLAS_WIDTH) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }
            sprites.add(new Sprite(new Rect2i(x + gutter, y + gutter, w, h), gutter));
            x += slotW;
            rowHeight = Math.max(rowHeight, slotH);
        }
        final int atlasHeight = Integer.highestOneBit(y + rowHeight - 1) << 1;

        final SpriteGrid<Sprite> grid = SpriteGrid.build(ATLAS_WIDTH, atlasHeight, sprites, Sprite::bounds, Sprite::gutter);
        assertNotNull(grid, "no grid built for gutter " + gutter);

        final QuadTree<Sprite> tree = new QuadTree<>(new Rect2i(0, 0, ATLAS_WIDTH, atlasHeight), 16, sprites, Sprite::bounds);

        for (int ty = -1; ty <= atlasHeight; ty++) {
            for (int tx = -1; tx <= ATLAS_WIDTH; tx++) {
                final Sprite fromTree = tree.find(tx, ty);
                final Sprite fromGrid = grid.find(tx, ty);
                if (fromTree != null) {
                    assertSame(fromTree, fromGrid, "texel " + tx + "," + ty);
                } else {
                    assertEquals(gutterOwner(sprites, tx, ty), fromGrid, "texel " + tx + "," + ty);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1 })
    void oddSlotsFallBackToQuadTree(int gutter) {
        final List<Sprite> sprites = List.of(new Sprite(new Rect2i(gutter, gutter, 15, 16), gutter));
        assertNull(SpriteGrid.build(64, 64, sprites, Sprite::bounds, Sprite::gutter));
    }

    private static Sprite gutterOwner(List<Sprite> sprites, int x, int y) {
        for (Sprite sprite : sprites) {
            final Rect2i b = sprite.bounds();
            final int g = sprite.gutter();
            if (new Rect2i(b.x() - g, b.y() - g, b.width() + 2 * g, b.height() + 2 * g).contains(x, y)) return sprite;
        }
        return null;
    }
}
