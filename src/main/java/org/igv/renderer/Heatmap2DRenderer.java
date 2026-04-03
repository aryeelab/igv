package org.igv.renderer;

import org.igv.track.Heatmap2DMatrix;
import org.igv.track.RenderContext;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renderer for 2D heatmap data.
 * Maps a Heatmap2DMatrix to a BufferedImage using a configurable colormap and data range.
 */
public class Heatmap2DRenderer {

    /**
     * Available color palettes.
     */
    private static final Map<String, int[][]> PALETTE_CONTROL_POINTS = new LinkedHashMap<>();

    static {
        PALETTE_CONTROL_POINTS.put("Magma", new int[][]{
                {0, 0, 4}, {28, 16, 68}, {79, 18, 123}, {129, 37, 129},
                {181, 54, 122}, {229, 89, 100}, {251, 135, 97}, {254, 194, 140}, {252, 253, 191}
        });
        PALETTE_CONTROL_POINTS.put("Viridis", new int[][]{
                {68, 1, 84}, {72, 33, 115}, {64, 67, 135}, {52, 94, 141},
                {33, 145, 140}, {94, 201, 98}, {253, 231, 37}
        });
        PALETTE_CONTROL_POINTS.put("White-Red", new int[][]{
                {255, 255, 255}, {255, 200, 200}, {255, 100, 100}, {200, 0, 0}, {128, 0, 0}
        });
        PALETTE_CONTROL_POINTS.put("Blue-White-Red", new int[][]{
                {0, 0, 178}, {0, 0, 255}, {128, 128, 255}, {255, 255, 255},
                {255, 128, 128}, {255, 0, 0}, {178, 0, 0}
        });
        PALETTE_CONTROL_POINTS.put("Inferno", new int[][]{
                {0, 0, 4}, {40, 11, 84}, {101, 21, 110}, {159, 42, 99},
                {212, 72, 66}, {245, 125, 21}, {250, 193, 39}, {252, 255, 164}
        });
        PALETTE_CONTROL_POINTS.put("Hot", new int[][]{
                {0, 0, 0}, {128, 0, 0}, {255, 0, 0}, {255, 128, 0}, {255, 255, 0}, {255, 255, 255}
        });
    }

    // Precomputed LUTs: paletteName -> {R[256], G[256], B[256]}
    private static final Map<String, int[][]> PALETTE_LUTS = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, int[][]> entry : PALETTE_CONTROL_POINTS.entrySet()) {
            PALETTE_LUTS.put(entry.getKey(), buildLUT(entry.getValue()));
        }
    }

    private static int[][] buildLUT(int[][] controlPoints) {
        int[] r = new int[256], g = new int[256], b = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255.0f;
            float segment = t * (controlPoints.length - 1);
            int idx = Math.min((int) segment, controlPoints.length - 2);
            float frac = segment - idx;
            r[i] = clamp((int) (controlPoints[idx][0] + frac * (controlPoints[idx + 1][0] - controlPoints[idx][0])));
            g[i] = clamp((int) (controlPoints[idx][1] + frac * (controlPoints[idx + 1][1] - controlPoints[idx][1])));
            b[i] = clamp((int) (controlPoints[idx][2] + frac * (controlPoints[idx + 1][2] - controlPoints[idx][2])));
        }
        return new int[][]{r, g, b};
    }

    public static Set<String> getAvailablePalettes() {
        return PALETTE_LUTS.keySet();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Render the 2D heatmap matrix into the given rectangle.
     *
     * @param matrix      the heatmap data matrix
     * @param context     render context with graphics and frame info
     * @param rect        the rectangle to draw into
     * @param colorMin    minimum value for color mapping (NaN = auto)
     * @param colorMax    maximum value for color mapping (NaN = auto)
     * @param paletteName name of the color palette to use
     */
    public void render(Heatmap2DMatrix matrix, RenderContext context, Rectangle rect,
                       float colorMin, float colorMax, String paletteName) {
        if (matrix == null || matrix.getNumRows() == 0 || matrix.getNumCols() == 0) {
            return;
        }

        int[][] lut = PALETTE_LUTS.getOrDefault(paletteName, PALETTE_LUTS.get("Magma"));
        int[] lutR = lut[0], lutG = lut[1], lutB = lut[2];

        Graphics2D g2d = context.getGraphics();
        double origin = context.getOrigin();
        double scale = context.getScale();  // bp per pixel

        int matStart = matrix.getStartPosition();
        int numRows = matrix.getNumRows();
        float[][] data = matrix.getData();

        // Determine color scale bounds
        float vmin = Float.isNaN(colorMin) ? 0 : colorMin;
        float vmax = Float.isNaN(colorMax) ? matrix.getPercentile(98) : colorMax;
        if (vmax <= vmin) vmax = vmin + 1;

        // Create a BufferedImage the size of the output rectangle
        int imgWidth = rect.width;
        int imgHeight = rect.height;
        if (imgWidth <= 0 || imgHeight <= 0) return;

        BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);

        for (int px = 0; px < imgWidth; px++) {
            double genomicPos = origin + (rect.x + px) * scale;
            int col = (int) Math.round(genomicPos) - matStart;

            for (int py = 0; py < imgHeight; py++) {
                int row = (int) ((double) (imgHeight - 1 - py) / imgHeight * numRows);
                if (row >= numRows) row = numRows - 1;
                if (row < 0) row = 0;

                if (col >= 0 && col < matrix.getNumCols()) {
                    float value = data[row][col];
                    int colorIdx = (int) ((value - vmin) / (vmax - vmin) * 255);
                    colorIdx = Math.max(0, Math.min(255, colorIdx));
                    int rgb = (0xFF << 24) | (lutR[colorIdx] << 16) | (lutG[colorIdx] << 8) | lutB[colorIdx];
                    image.setRGB(px, py, rgb);
                } else {
                    image.setRGB(px, py, 0x00000000);  // transparent
                }
            }
        }

        g2d.drawImage(image, rect.x, rect.y, null);

        // Draw Y-axis labels every 20 units
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g2d.getFontMetrics();
        int yMin = matrix.getYMin();
        int yMax = matrix.getYMax();
        for (int yValue = ((yMin / 20) + 1) * 20; yValue <= yMax; yValue += 20) {
            int row = yValue - yMin;
            int yPixel = rect.y + imgHeight - (int) ((double) row / numRows * imgHeight);
            String label = String.valueOf(yValue);
            g2d.drawString(label, rect.x + 3, yPixel + fm.getAscent() / 2);
        }
    }
}
