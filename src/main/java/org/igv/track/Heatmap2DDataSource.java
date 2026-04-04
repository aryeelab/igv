package org.igv.track;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.tabix.TabixIndex;
import org.igv.util.FileUtils;
import org.igv.util.ParsingUtils;
import org.igv.util.ResourceLocator;
import org.igv.util.stream.IGVSeekableStreamFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data source for 2D heatmap count matrix files (.counts.tsv.gz).
 * Reads a bgzip-compressed TSV with columns: chrom, midpoint, length, count.
 * Parses scale factors from the file header and builds 2D matrices for genomic
 * windows using tabix queries against the visible region.
 */
public class Heatmap2DDataSource implements AutoCloseable {

    private final String path;
    private final String indexPath;
    private final TabixIndex tabixIndex;
    private final BlockCompressedInputStream dataInputStream;
    private Map<Integer, Double> scaleFactors;

    public Heatmap2DDataSource(ResourceLocator locator) throws IOException {
        this.path = locator.getPath();
        this.indexPath = resolveIndexPath(locator);
        validateIndexedInput();
        this.scaleFactors = loadScaleFactors(locator);

        try (SeekableStream indexStream = IGVSeekableStreamFactory.getInstance().getStreamFor(indexPath);
             BlockCompressedInputStream indexInput = new BlockCompressedInputStream(indexStream)) {
            this.tabixIndex = new TabixIndex(indexInput);
        }

        SeekableStream stream = IGVSeekableStreamFactory.getInstance().getStreamFor(path);
        this.dataInputStream = new BlockCompressedInputStream(stream);
    }

    private String resolveIndexPath(ResourceLocator locator) {
        String candidate = locator.getIndexPath();
        if (candidate == null || candidate.isBlank()) {
            candidate = ResourceLocator.indexFile(locator);
        }
        return candidate;
    }

    private void validateIndexedInput() throws IOException {
        if (!IOUtil.hasBlockCompressedExtension(path.split("\\?", 2)[0])) {
            throw new IOException("Heatmap2D requires a bgzip-compressed .counts.tsv.gz file: " + path);
        }
        if (indexPath == null || indexPath.isBlank() || !FileUtils.resourceExists(indexPath)) {
            throw new IOException("Heatmap2D requires a tabix index for " + path +
                    " (expected " + (indexPath == null ? "<unknown>" : indexPath) + ")");
        }
    }

    /**
     * Parse the file header to extract scale factors.
     */
    private Map<Integer, Double> loadScaleFactors(ResourceLocator locator) throws IOException {
        Map<Integer, Double> factors = new HashMap<>();
        try (BufferedReader reader = ParsingUtils.openBufferedReader(locator)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    break;
                }
                if (line.startsWith("# scale_factors:")) {
                    String dictStr = line.substring("# scale_factors:".length()).trim();
                    factors = parsePythonDict(dictStr);
                }
            }
        }
        return factors;
    }

    /**
     * Parse a Python-style dictionary literal: {key: value, key: value, ...}
     * Keys are integers, values are doubles.
     */
    static Map<Integer, Double> parsePythonDict(String dictStr) {
        Map<Integer, Double> result = new HashMap<>();
        dictStr = dictStr.trim();
        if (dictStr.startsWith("{")) dictStr = dictStr.substring(1);
        if (dictStr.endsWith("}")) dictStr = dictStr.substring(0, dictStr.length() - 1);

        Pattern pattern = Pattern.compile("(\\d+)\\s*:\\s*([\\d.eE+\\-]+)");
        Matcher matcher = pattern.matcher(dictStr);
        while (matcher.find()) {
            int key = Integer.parseInt(matcher.group(1));
            double value = Double.parseDouble(matcher.group(2));
            result.put(key, value);
        }
        return result;
    }

    /**
     * Build a Heatmap2DMatrix for the given genomic range.
     *
     * @param chr          chromosome name
     * @param start        genomic start (inclusive)
     * @param end          genomic end (inclusive)
     * @param yMin         minimum y value to include
     * @param yMax         maximum y value to include
     * @param sigma        Gaussian smoothing sigma (0 = no smoothing)
     * @param logTransform whether to apply a log2(x) display transform
     * @param applyScale   whether to apply scale factors
     * @return Heatmap2DMatrix with processed data
     */
    public Heatmap2DMatrix getMatrix(String chr, int start, int end,
                                     int yMin, int yMax,
                                     double sigma, boolean logTransform,
                                     boolean applyScale) throws IOException {

        int range = end - start + 1;
        if (range <= 0) {
            return null;
        }

        int numRows = yMax - yMin + 1;
        int numCols = range;
        float[][] matrix = new float[numRows][numCols];

        List<Block> blocks = new ArrayList<>(tabixIndex.getBlocks(chr, start + 1, end));
        if (blocks.isEmpty()) {
            return null;
        }

        blocks.sort(Comparator.comparingLong(Block::getStartPosition));
        dataInputStream.seek(blocks.get(0).getStartPosition());
        String line;
        while ((line = dataInputStream.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] fields = line.split("\t");
            if (fields.length < 4) {
                continue;
            }

            int pos = (int) Math.round(Double.parseDouble(fields[1]));
            if (pos > end) {
                break;
            }
            if (pos < start) {
                continue;
            }

            int y = Integer.parseInt(fields[2]);
            if (y < yMin || y > yMax) {
                continue;
            }

            int count = Integer.parseInt(fields[3]);
            int row = y - yMin;
            int col = pos - start;
            if (col >= 0 && col < numCols && row >= 0 && row < numRows) {
                matrix[row][col] += count;
            }
        }

        if (applyScale && !scaleFactors.isEmpty()) {
            for (int row = 0; row < numRows; row++) {
                int y = row + yMin;
                Double factor = scaleFactors.get(y);
                if (factor != null && factor > 0) {
                    for (int col = 0; col < numCols; col++) {
                        matrix[row][col] = (float) (matrix[row][col] / factor);
                    }
                }
            }
        }

        if (sigma > 0) {
            matrix = gaussianSmooth(matrix, sigma);
        }

        if (logTransform) {
            // Clamp values below 1 so the display transform is max(0, log2(x)).
            double log2 = Math.log(2);
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numCols; col++) {
                    float val = matrix[row][col];
                    if (val < 1) val = 1;
                    matrix[row][col] = (float) (Math.log(val) / log2);
                }
            }
        }

        return new Heatmap2DMatrix(matrix, chr, start, end, yMin, yMax);
    }

    /**
     * Apply separable 2D Gaussian smoothing to the matrix.
     * Uses two 1D passes (horizontal then vertical) for efficiency.
     */
    static float[][] gaussianSmooth(float[][] matrix, double sigma) {
        int rows = matrix.length;
        if (rows == 0) return matrix;
        int cols = matrix[0].length;

        int radius = (int) Math.ceil(3 * sigma);
        int kernelSize = 2 * radius + 1;
        float[] kernel = new float[kernelSize];
        float sum = 0;
        for (int i = 0; i < kernelSize; i++) {
            float x = i - radius;
            kernel[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < kernelSize; i++) {
            kernel[i] /= sum;
        }

        float[][] temp = new float[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float val = 0;
                for (int k = -radius; k <= radius; k++) {
                    int cc = c + k;
                    if (cc < 0) cc = 0;
                    else if (cc >= cols) cc = cols - 1;
                    val += matrix[r][cc] * kernel[k + radius];
                }
                temp[r][c] = val;
            }
        }

        float[][] result = new float[rows][cols];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                float val = 0;
                for (int k = -radius; k <= radius; k++) {
                    int rr = r + k;
                    if (rr < 0) rr = 0;
                    else if (rr >= rows) rr = rows - 1;
                    val += temp[rr][c] * kernel[k + radius];
                }
                result[r][c] = val;
            }
        }

        return result;
    }

    public Map<Integer, Double> getScaleFactors() {
        return scaleFactors;
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
