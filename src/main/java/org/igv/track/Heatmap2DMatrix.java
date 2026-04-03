package org.igv.track;

/**
 * Data container for a 2D heatmap matrix.
 * Rows represent y-axis values, columns represent x-axis positions.
 */
public class Heatmap2DMatrix {

    private final float[][] data;       // [yRows][xCols]
    private final String chr;
    private final int startPosition;    // genomic x start (inclusive)
    private final int endPosition;      // genomic x end (inclusive)
    private final int yMin;
    private final int yMax;
    private float dataMin;
    private float dataMax;

    public Heatmap2DMatrix(float[][] data, String chr, int startPosition, int endPosition,
                           int yMin, int yMax) {
        this.data = data;
        this.chr = chr;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.yMin = yMin;
        this.yMax = yMax;
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

    public int getYMin() {
        return yMin;
    }

    public int getYMax() {
        return yMax;
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
     * Get the value at a specific y and x position.
     * Returns 0 if out of bounds.
     */
    public float getValue(int y, int x) {
        int row = y - yMin;
        int col = x - startPosition;
        if (row < 0 || row >= data.length || col < 0 || col >= data[0].length) {
            return 0;
        }
        return data[row][col];
    }
}
