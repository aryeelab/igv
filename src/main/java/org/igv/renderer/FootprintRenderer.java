package org.igv.renderer;

import org.igv.track.FootprintMatrix;
import org.igv.track.RenderContext;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Renderer for 2D footprint heatmap data.
 * Maps a FootprintMatrix to a BufferedImage using a magma-like colormap.
 */
public class FootprintRenderer {

    // Magma colormap: 256 entries from dark (low) to bright (high)
    private static final int[] MAGMA_R, MAGMA_G, MAGMA_B;

    static {
        // Simplified magma colormap - 9 control points, interpolated to 256 entries
        int[][] controlPoints = {
                {0, 0, 4},       // 0
                {28, 16, 68},    // 32
                {79, 18, 123},   // 64
                {129, 37, 129},  // 96
                {181, 54, 122},  // 128
                {229, 89, 100},  // 160
                {251, 135, 97},  // 192
                {254, 194, 140}, // 224
                {252, 253, 191}  // 255
        };
        MAGMA_R = new int[256];
        MAGMA_G = new int[256];
        MAGMA_B = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255.0f;
            float segment = t * (controlPoints.length - 1);
            int idx = Math.min((int) segment, controlPoints.length - 2);
            float frac = segment - idx;
            MAGMA_R[i] = clamp((int) (controlPoints[idx][0] + frac * (controlPoints[idx + 1][0] - controlPoints[idx][0])));
            MAGMA_G[i] = clamp((int) (controlPoints[idx][1] + frac * (controlPoints[idx + 1][1] - controlPoints[idx][1])));
            MAGMA_B[i] = clamp((int) (controlPoints[idx][2] + frac * (controlPoints[idx + 1][2] - controlPoints[idx][2])));
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Render the 2D heatmap matrix into the given rectangle.
     *
     * @param matrix  the footprint data matrix
     * @param context render context with graphics and frame info
     * @param rect    the rectangle to draw into
     */
    public void render(FootprintMatrix matrix, RenderContext context, Rectangle rect) {
        if (matrix == null || matrix.getNumRows() == 0 || matrix.getNumCols() == 0) {
            return;
        }

        Graphics2D g2d = context.getGraphics();
        double origin = context.getOrigin();
        double scale = context.getScale();  // bp per pixel

        int matStart = matrix.getStartPosition();
        int numRows = matrix.getNumRows();
        float[][] data = matrix.getData();

        // Compute color scale: vmin=0, vmax=98th percentile
        float vmin = 0;
        float vmax = matrix.getPercentile(98);
        if (vmax <= vmin) vmax = vmin + 1;

        // Create a BufferedImage the size of the output rectangle
        int imgWidth = rect.width;
        int imgHeight = rect.height;
        if (imgWidth <= 0 || imgHeight <= 0) return;

        BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);

        for (int px = 0; px < imgWidth; px++) {
            // Map pixel to genomic position
            double genomicPos = origin + (rect.x + px) * scale;
            int col = (int) Math.round(genomicPos) - matStart;

            for (int py = 0; py < imgHeight; py++) {
                // Map pixel row to fragment length row (top = fragLenMax, bottom = fragLenMin)
                int row = (int) ((double) (imgHeight - 1 - py) / imgHeight * numRows);
                if (row >= numRows) row = numRows - 1;
                if (row < 0) row = 0;

                if (col >= 0 && col < matrix.getNumCols() && row >= 0) {
                    float value = data[row][col];
                    int colorIdx = (int) ((value - vmin) / (vmax - vmin) * 255);
                    colorIdx = Math.max(0, Math.min(255, colorIdx));
                    int rgb = (0xFF << 24) | (MAGMA_R[colorIdx] << 16) | (MAGMA_G[colorIdx] << 8) | MAGMA_B[colorIdx];
                    image.setRGB(px, py, rgb);
                } else {
                    image.setRGB(px, py, 0x00000000);  // transparent
                }
            }
        }

        g2d.drawImage(image, rect.x, rect.y, null);

        // Draw Y-axis labels (fragment length ticks every 20)
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g2d.getFontMetrics();
        int fragMin = matrix.getFragLenMin();
        int fragMax = matrix.getFragLenMax();
        for (int fragLen = ((fragMin / 20) + 1) * 20; fragLen <= fragMax; fragLen += 20) {
            int row = fragLen - fragMin;
            int y = rect.y + imgHeight - (int) ((double) row / numRows * imgHeight);
            String label = String.valueOf(fragLen);
            g2d.drawString(label, rect.x + 3, y + fm.getAscent() / 2);
        }
    }
}

