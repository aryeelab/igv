package org.igv.track;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class Heatmap2DTrackTest {

    @Test
    public void parsesSignedYRange() {
        assertArrayEquals(new int[]{25, 150}, Heatmap2DTrack.parseRange("25-150"));
        assertArrayEquals(new int[]{-20, 150}, Heatmap2DTrack.parseRange("-20-150"));
        assertArrayEquals(new int[]{-20, -5}, Heatmap2DTrack.parseRange("-20 - -5"));
        assertArrayEquals(new int[]{25, 150}, Heatmap2DTrack.parseRange("150-25"));
    }

    @Test
    public void rejectsMalformedYRange() {
        assertNull(Heatmap2DTrack.parseRange("abc"));
        assertNull(Heatmap2DTrack.parseRange("10-"));
        assertNull(Heatmap2DTrack.parseRange("10-20-30"));
    }
}
