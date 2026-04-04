package org.igv.track;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.PositionalBufferedStream;
import org.igv.util.ResourceLocator;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Heatmap2DDataSourceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void loadsSignalInFixtureWindow() throws Exception {
        File countsFile = createCountsFixture();
        ResourceLocator locator = new ResourceLocator(countsFile.getAbsolutePath());
        try (Heatmap2DDataSource source = new Heatmap2DDataSource(locator)) {
            Heatmap2DMatrix matrix = source.getMatrix("chr8", 23237000, 23238000, 25, 150, 0.0, false, false);
            assertNotNull(matrix);
            assertTrue(matrix.getDataMax() > 0);
        }
    }

    private File createCountsFixture() throws IOException {
        File countsFile = tmp.newFile("heatmap2d.counts.tsv.gz");
        File indexFile = new File(countsFile.getAbsolutePath() + ".tbi");

        try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(countsFile)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write("chr8\t23237000.5\t138\t1\n");
            writer.write("chr8\t23237010.0\t80\t2\n");
            writer.write("chr8\t23238005.0\t120\t3\n");
            writer.flush();
        }

        IndexFactory.writeIndex(
                IndexFactory.createTabixIndex(
                        countsFile,
                        new CountsCodec(),
                        new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr8", 25_000_000)))),
                indexFile);

        return countsFile;
    }

    private static class CountsCodec implements FeatureCodec<CountsFeature, PositionalBufferedStream> {
        @Override
        public CountsFeature decodeLoc(PositionalBufferedStream source) throws IOException {
            return decode(source);
        }

        @Override
        public CountsFeature decode(PositionalBufferedStream source) throws IOException {
            while (!source.isDone()) {
                String line = readLine(source);
                if (line == null || line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t");
                double midpoint = Double.parseDouble(fields[1]);
                int pos = (int) Math.round(midpoint);
                int y = Integer.parseInt(fields[2]);
                int count = Integer.parseInt(fields[3]);
                return new CountsFeature(fields[0], pos, y, count);
            }
            return null;
        }

        @Override
        public FeatureCodecHeader readHeader(PositionalBufferedStream source) {
            return FeatureCodecHeader.EMPTY_HEADER;
        }

        @Override
        public Class<CountsFeature> getFeatureType() {
            return CountsFeature.class;
        }

        @Override
        public PositionalBufferedStream makeSourceFromStream(java.io.InputStream inputStream) {
            return new PositionalBufferedStream(inputStream);
        }

        @Override
        public PositionalBufferedStream makeIndexableSourceFromStream(java.io.InputStream inputStream) {
            return new PositionalBufferedStream(inputStream);
        }

        @Override
        public boolean isDone(PositionalBufferedStream source) {
            try {
                return source.isDone();
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public void close(PositionalBufferedStream source) {
            source.close();
        }

        @Override
        public boolean canDecode(String path) {
            return path.endsWith(".counts.tsv.gz");
        }

        @Override
        public TabixFormat getTabixFormat() {
            return new TabixFormat(TabixFormat.GENERIC_FLAGS, 1, 2, 2, '#', 0);
        }

        private String readLine(PositionalBufferedStream source) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = source.read()) != -1 && c != '\n') {
                if (c != '\r') {
                    sb.append((char) c);
                }
            }
            if (c == -1 && sb.length() == 0) {
                return null;
            }
            return sb.toString();
        }
    }

    private static class CountsFeature implements Feature {
        private final String contig;
        private final int position;
        private final int y;
        private final int count;

        private CountsFeature(String contig, int position, int y, int count) {
            this.contig = contig;
            this.position = position;
            this.y = y;
            this.count = count;
        }

        @Override
        public String getContig() {
            return contig;
        }

        @Override
        public int getStart() {
            return position;
        }

        @Override
        public int getEnd() {
            return position;
        }

        @SuppressWarnings("unused")
        public int getY() {
            return y;
        }

        @SuppressWarnings("unused")
        public int getCount() {
            return count;
        }
    }
}
