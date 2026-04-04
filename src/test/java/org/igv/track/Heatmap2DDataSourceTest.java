package org.igv.track;

import org.igv.util.ResourceLocator;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Heatmap2DDataSourceTest {

    @Test
    public void loadsSignalInFixtureWindow() throws Exception {
        ResourceLocator locator = new ResourceLocator(
                "/Users/martin/projects/igv/footprint-tools/test_data/mesc_microc_test.counts.tsv.gz");
        try (Heatmap2DDataSource source = new Heatmap2DDataSource(locator)) {
            Heatmap2DMatrix matrix = source.getMatrix("chr8", 23237000, 23238000, 25, 150, 1.0, true, true);
            assertNotNull(matrix);
            assertTrue(matrix.getDataMax() > 0);
        }
    }
}
