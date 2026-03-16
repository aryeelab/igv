package org.igv.track;

/**
 * Data container for a 2D footprint heatmap matrix.
 * Rows represent fragment lengths, columns represent genomic positions.
 */
public class FootprintMatrix {

    private final float[][] data;       // [fragLenRows][positionCols]
    private final String chr;
    private final int startPosition;    // genomic start (inclusive)
    private final int endPosition;      // genomic end (inclusive)
    private final int fragLenMin;
    private final int fragLenMax;
    private float dataMin;
    private float dataMax;

    public FootprintMatrix(float[][] data, String chr, int startPosition, int endPosition,
                           int fragLenMin, int fragLenMax) {
        this.data = data;
        this.chr = chr;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.fragLenMin = fragLenMin;
        this.fragLenMax = fragLenMax;
        computeMinMax();
    }

    private void computeMinMax() {
        dataMin = Float.MAX_VALUE;
        dataMax = -Float.MAX_VALUE;
        for (float[] row : data) {
            for (float val : row) {
                if (!Float.isNaN(val)) {
                    dataMin = Math.min(dataMin, val);
                    dataMax = Math.max(dataMax, val);
                }
            }
        }
        if (dataMin == Float.MAX_VALUE) {
            dataMin = 0;
            dataMax = 0;
        }
    }

    /**
     * Compute the value at a given percentile across all matrix values.
     */
    public float getPercentile(float percentile) {
        int totalCells = data.length * (data.length > 0 ? data[0].length : 0);
        if (totalCells == 0) return 0;

        float[] values = new float[totalCells];
        int idx = 0;
        for (float[] row : data) {
            for (float val : row) {
                values[idx++] = val;
            }
        }
        java.util.Arrays.sort(values);
        int index = Math.min((int) (percentile / 100.0f * totalCells), totalCells - 1);
        return values[index];
    }

    public float[][] getData() {
        return data;
    }

    public String getChr() {
        return chr;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public int getFragLenMin() {
        return fragLenMin;
    }

    public int getFragLenMax() {
        return fragLenMax;
    }

    public float getDataMin() {
        return dataMin;
    }

    public float getDataMax() {
        return dataMax;
    }

    public int getNumRows() {
        return data.length;
    }

    public int getNumCols() {
        return data.length > 0 ? data[0].length : 0;
    }

    /**
     * Get the value at a specific fragment length and genomic position.
     * Returns 0 if out of bounds.
     */
    public float getValue(int fragLen, int position) {
        int row = fragLen - fragLenMin;
        int col = position - startPosition;
        if (row < 0 || row >= data.length || col < 0 || col >= data[0].length) {
            return 0;
        }
        return data[row][col];
    }
}

