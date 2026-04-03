package org.igv.track;

import htsjdk.samtools.util.BlockCompressedInputStream;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.util.ResourceLocator;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data source for 2D heatmap count matrix files (.counts.tsv.gz).
 * Reads a bgzip-compressed TSV with columns: chrom, midpoint, length, count.
 * Parses scale factors from the file header and builds 2D matrices for given genomic ranges.
 * <p>
 * All records are loaded into memory on first access (the files are typically small,
 * ~100k records). This avoids issues with htsjdk's TabixReader.getIntv() which cannot
 * parse float-valued position columns like "23232514.0".
 */
public class Heatmap2DDataSource {

    private static final Logger log = LogManager.getLogger(Heatmap2DDataSource.class);
    private static final int MAX_RANGE_BP = 100_000;  // Maximum viewport width before refusing to load

    private final String path;
    private Map<Integer, Double> scaleFactors;

    // In-memory record store, keyed by chromosome, sorted by position
    private Map<String, List<Record>> recordsByChrom;

    static class Record implements Comparable<Record> {
        final int position;   // rounded midpoint
        final int y;
        final int count;

        Record(int position, int y, int count) {
            this.position = position;
            this.y = y;
            this.count = count;
        }

        @Override
        public int compareTo(Record o) {
            return Integer.compare(this.position, o.position);
        }
    }

    public Heatmap2DDataSource(ResourceLocator locator) throws IOException {
        this.path = locator.getPath();
        loadAll();
    }

    /**
     * Read the entire bgzip file: parse header for scale factors, then load all records.
     */
    private void loadAll() throws IOException {
        scaleFactors = new HashMap<>();
        recordsByChrom = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BlockCompressedInputStream(new FileInputStream(path))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    if (line.startsWith("# scale_factors:")) {
                        String dictStr = line.substring("# scale_factors:".length()).trim();
                        scaleFactors = parsePythonDict(dictStr);
                    }
                    continue;
                }
                String[] fields = line.split("\t");
                if (fields.length < 4) continue;
                String chr = fields[0];
                int pos = (int) Math.round(Double.parseDouble(fields[1]));
                int y = Integer.parseInt(fields[2]);
                int count = Integer.parseInt(fields[3]);
                recordsByChrom.computeIfAbsent(chr, k -> new ArrayList<>())
                        .add(new Record(pos, y, count));
            }
        }

        // Sort each chromosome's records by position
        for (List<Record> records : recordsByChrom.values()) {
            Collections.sort(records);
        }
    }

    /**
     * Parse a Python-style dictionary literal: {key: value, key: value, ...}
     * Keys are integers, values are doubles.
     */
    static Map<Integer, Double> parsePythonDict(String dictStr) {
        Map<Integer, Double> result = new HashMap<>();
        // Remove braces
        dictStr = dictStr.trim();
        if (dictStr.startsWith("{")) dictStr = dictStr.substring(1);
        if (dictStr.endsWith("}")) dictStr = dictStr.substring(0, dictStr.length() - 1);

        // Match key: value pairs
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
     * @param logTransform whether to apply log2(1+x) transform
     * @param applyScale   whether to apply scale factors
     * @return Heatmap2DMatrix with processed data
     */
    public Heatmap2DMatrix getMatrix(String chr, int start, int end,
                                     int yMin, int yMax,
                                     double sigma, boolean logTransform,
                                     boolean applyScale) throws IOException {

        int range = end - start + 1;
        if (range > MAX_RANGE_BP || range <= 0) {
            return null;  // Too wide — caller should show "zoom in" message
        }

        int numRows = yMax - yMin + 1;
        int numCols = range;
        float[][] matrix = new float[numRows][numCols];

        // Query in-memory records using binary search
        List<Record> chrRecords = recordsByChrom.get(chr);
        if (chrRecords != null) {
            // Find first record at or after 'start'
            int idx = Collections.binarySearch(chrRecords, new Record(start, 0, 0));
            if (idx < 0) idx = -(idx + 1);

            for (int i = idx; i < chrRecords.size(); i++) {
                Record rec = chrRecords.get(i);
                if (rec.position > end) break;

                int y = rec.y;
                if (y > yMax) y = yMax;
                if (y < yMin) continue;

                int row = y - yMin;
                int col = rec.position - start;
                if (col >= 0 && col < numCols && row >= 0 && row < numRows) {
                    matrix[row][col] += rec.count;
                }
            }
        }

        // Apply scale factors
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

        // Apply Gaussian smoothing
        if (sigma > 0) {
            matrix = gaussianSmooth(matrix, sigma);
        }

        // Apply log2(1+x) transform
        if (logTransform) {
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

        // Build 1D Gaussian kernel
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

        // Horizontal pass
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

        // Vertical pass
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
}
