package org.igv.track;

import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.renderer.Heatmap2DRenderer;
import org.igv.renderer.GraphicUtils;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.IGVPopupMenu;
import org.igv.ui.panel.ReferenceFrame;
import org.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Track for displaying 2D heatmap data from .counts.tsv.gz files.
 * Shows y-axis values vs genomic x positions as a heatmap.
 */
public class Heatmap2DTrack extends AbstractTrack implements IGVEventObserver {

    private static final Logger log = LogManager.getLogger(Heatmap2DTrack.class);
    private static final Pattern SIGNED_RANGE_PATTERN = Pattern.compile("^\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*$");
    private static final Pattern SIGNED_FLOAT_RANGE_PATTERN = Pattern.compile(
            "^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*-\\s*" +
                    "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*$");

    private final Heatmap2DDataSource dataSource;
    private final Heatmap2DRenderer renderer;

    // Cache per reference frame
    private Map<String, LoadedDataInterval<Heatmap2DMatrix>> loadedIntervalCache =
            Collections.synchronizedMap(new HashMap<>());

    // User-configurable parameters
    private double sigma = 1.0;
    private boolean logTransform = true;
    private int yMin = 25;
    private int yMax = 150;
    private boolean applyScale = true;

    // Color scale: NaN means auto (0 for min, 98th percentile for max)
    private float colorScaleMin = Float.NaN;
    private float colorScaleMax = Float.NaN;
    private String paletteName = "Magma";

    public Heatmap2DTrack(ResourceLocator locator, Heatmap2DDataSource dataSource) {
        super(locator);
        this.dataSource = dataSource;
        this.renderer = new Heatmap2DRenderer();
        setHeight(250);
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof FrameManager.ChangeEvent) {
            Map<String, LoadedDataInterval<Heatmap2DMatrix>> newCache =
                    Collections.synchronizedMap(new HashMap<>());
            List<ReferenceFrame> frames = FrameManager.getFrames();
            for (ReferenceFrame f : frames) {
                LoadedDataInterval<Heatmap2DMatrix> interval = loadedIntervalCache.get(f.getName());
                if (interval != null) {
                    newCache.put(f.getName(), interval);
                }
            }
            loadedIntervalCache = newCache;
        }
    }

    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {
        String chr = frame.getChrName();
        int start = (int) frame.getOrigin();
        int end = (int) frame.getEnd();
        LoadedDataInterval<Heatmap2DMatrix> interval = loadedIntervalCache.get(frame.getName());
        if (interval == null || !interval.contains(chr, start, end)) return false;
        // If the cached matrix is null (viewport was too wide), only consider ready
        // if the viewport is still too wide. Otherwise, reload to get actual data.
        if (interval.getFeatures() == null) {
            int range = end - start;
            return range > 100_000;  // still too wide, no point reloading
        }
        return true;
    }

    @Override
    public synchronized void load(ReferenceFrame referenceFrame) {
        if (isReadyToPaint(referenceFrame)) return;

        String chr = referenceFrame.getChrName();
        int start = (int) referenceFrame.getOrigin();
        int end = (int) referenceFrame.getEnd() + 1;

        // Expand range by 50% for panning
        int w = end - start;
        int expandedStart = Math.max(0, start - w / 2);
        int expandedEnd = end + w / 2;

        try {
            Heatmap2DMatrix matrix = dataSource.getMatrix(chr, expandedStart, expandedEnd,
                    yMin, yMax, sigma, logTransform, applyScale);
            LoadedDataInterval<Heatmap2DMatrix> interval =
                    new LoadedDataInterval<>(chr, expandedStart, expandedEnd, matrix);
            loadedIntervalCache.put(referenceFrame.getName(), interval);
        } catch (IOException e) {
            log.error("Error loading 2D heatmap data", e);
        }
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        LoadedDataInterval<Heatmap2DMatrix> interval =
                loadedIntervalCache.get(context.getReferenceFrame().getName());
        if (interval == null) return;

        Heatmap2DMatrix matrix = interval.getFeatures();
        if (matrix == null) {
            // Data source returned null - viewport too wide
            Graphics2D g = context.getGraphic2DForColor(Color.gray);
            GraphicUtils.drawCenteredText("Zoom in to see 2D heatmap data", rect, g);
            return;
        }
        renderer.render(matrix, context, rect, colorScaleMin, colorScaleMax, paletteName);
    }

    public void clearCaches() {
        loadedIntervalCache.clear();
    }

    @Override
    public void unload() {
        super.unload();
        try {
            dataSource.close();
        } catch (IOException e) {
            log.warn("Error closing Heatmap2D data source", e);
        }
        IGVEventBus.getInstance().unsubscribe(this);
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public IGVPopupMenu getPopupMenu(final TrackClickEvent te) {
        IGVPopupMenu menu = new IGVPopupMenu();

        // Sigma setting
        JMenuItem sigmaItem = new JMenuItem("Set Smoothing Sigma (" + sigma + ")...");
        sigmaItem.addActionListener(e -> {
            String result = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(),
                    "Gaussian smoothing sigma (0 = no smoothing):", String.valueOf(sigma));
            if (result != null) {
                try {
                    sigma = Double.parseDouble(result);
                    clearCaches();
                    IGV.getInstance().repaint();
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        });
        menu.add(sigmaItem);

        // Log transform toggle
        JCheckBoxMenuItem logItem = new JCheckBoxMenuItem("Log2(1+x) Transform", logTransform);
        logItem.addActionListener(e -> {
            logTransform = logItem.isSelected();
            clearCaches();
            IGV.getInstance().repaint();
        });
        menu.add(logItem);

        // Scale toggle
        JCheckBoxMenuItem scaleItem = new JCheckBoxMenuItem("Apply Scale Factors", applyScale);
        scaleItem.addActionListener(e -> {
            applyScale = scaleItem.isSelected();
            clearCaches();
            IGV.getInstance().repaint();
        });
        menu.add(scaleItem);

        menu.addSeparator();

        // Y-axis range
        JMenuItem yRangeItem = new JMenuItem("Set Y Range (" + yMin + "-" + yMax + ")...");
        yRangeItem.addActionListener(e -> {
            String result = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(),
                    "Y-axis range (min-max):", yMin + "-" + yMax);
            if (result != null) {
                try {
                    int[] range = parseRange(result);
                    if (range != null) {
                        yMin = range[0];
                        yMax = range[1];
                        clearCaches();
                        IGV.getInstance().repaint();
                    }
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        });
        menu.add(yRangeItem);

        menu.addSeparator();

        // Color scale range
        // Compute effective auto range from current matrix for display
        String autoRangeStr = null;
        ReferenceFrame clickFrame = te != null ? te.getFrame() : null;
        if (clickFrame == null) {
            clickFrame = FrameManager.getDefaultFrame();
        }
        if (clickFrame != null) {
            LoadedDataInterval<Heatmap2DMatrix> clickInterval = loadedIntervalCache.get(clickFrame.getName());
            if (clickInterval != null && clickInterval.getFeatures() != null) {
                Heatmap2DMatrix clickMatrix = clickInterval.getFeatures();
                float autoMin = 0;
                float autoMax = clickMatrix.getPercentile(98);
                autoRangeStr = String.format("%.1f - %.1f", autoMin, autoMax);
            }
        }
        String rangeLabel = Float.isNaN(colorScaleMin) ? "Auto" :
                String.format("%.1f - %.1f", colorScaleMin, colorScaleMax);
        JMenuItem colorRangeItem = new JMenuItem("Set Color Scale Range (" + rangeLabel + ")...");
        final String effectiveAutoRange = autoRangeStr;
        colorRangeItem.addActionListener(e -> {
            String defaultVal;
            if (!Float.isNaN(colorScaleMin)) {
                defaultVal = String.format("%.1f - %.1f", colorScaleMin, colorScaleMax);
            } else if (effectiveAutoRange != null) {
                defaultVal = effectiveAutoRange;
            } else {
                defaultVal = "auto";
            }
            String result = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(),
                    "Color scale range (min - max), or 'auto':", defaultVal);
            if (result != null) {
                result = result.trim();
                if (result.equalsIgnoreCase("auto")) {
                    colorScaleMin = Float.NaN;
                    colorScaleMax = Float.NaN;
                    IGV.getInstance().repaint();
                } else {
                    try {
                        float[] range = parseFloatRange(result);
                        if (range != null) {
                            colorScaleMin = range[0];
                            colorScaleMax = range[1];
                            IGV.getInstance().repaint();
                        }
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                        // ignore
                    }
                }
            }
        });
        menu.add(colorRangeItem);

        // Color palette submenu
        JMenu paletteMenu = new JMenu("Color Palette");
        ButtonGroup paletteGroup = new ButtonGroup();
        for (String name : Heatmap2DRenderer.getAvailablePalettes()) {
            JRadioButtonMenuItem paletteItem = new JRadioButtonMenuItem(name, name.equals(paletteName));
            paletteItem.addActionListener(e -> {
                paletteName = name;
                IGV.getInstance().repaint();
            });
            paletteGroup.add(paletteItem);
            paletteMenu.add(paletteItem);
        }
        menu.add(paletteMenu);

        menu.addSeparator();
        TrackMenuUtils.addSharedItems(menu, List.of(this));

        return menu;
    }

    public double getSigma() { return sigma; }
    public void setSigma(double sigma) { this.sigma = sigma; }
    public boolean isLogTransform() { return logTransform; }
    public void setLogTransform(boolean logTransform) { this.logTransform = logTransform; }
    public int getYMin() { return yMin; }
    public void setYMin(int yMin) { this.yMin = yMin; }
    public int getYMax() { return yMax; }
    public void setYMax(int yMax) { this.yMax = yMax; }
    public boolean isApplyScale() { return applyScale; }
    public void setApplyScale(boolean applyScale) { this.applyScale = applyScale; }
    public float getColorScaleMin() { return colorScaleMin; }
    public void setColorScaleMin(float colorScaleMin) { this.colorScaleMin = colorScaleMin; }
    public float getColorScaleMax() { return colorScaleMax; }
    public void setColorScaleMax(float colorScaleMax) { this.colorScaleMax = colorScaleMax; }
    public String getPaletteName() { return paletteName; }
    public void setPaletteName(String paletteName) { this.paletteName = paletteName; }

    static int[] parseRange(String text) {
        Matcher matcher = SIGNED_RANGE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        int min = Integer.parseInt(matcher.group(1));
        int max = Integer.parseInt(matcher.group(2));
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return new int[]{
                min,
                max
        };
    }

    static float[] parseFloatRange(String text) {
        Matcher matcher = SIGNED_FLOAT_RANGE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        float min = Float.parseFloat(matcher.group(1));
        float max = Float.parseFloat(matcher.group(2));
        if (min > max) {
            float tmp = min;
            min = max;
            max = tmp;
        }
        return new float[]{min, max};
    }
}
