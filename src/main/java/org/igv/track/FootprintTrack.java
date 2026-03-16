package org.igv.track;

import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.renderer.FootprintRenderer;
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

/**
 * Track for displaying 2D footprint heatmap data from .counts.tsv.gz files.
 * Shows fragment length (Y-axis) vs genomic position (X-axis) as a heatmap.
 */
public class FootprintTrack extends AbstractTrack implements IGVEventObserver {

    private static final Logger log = LogManager.getLogger(FootprintTrack.class);

    private final FootprintDataSource dataSource;
    private final FootprintRenderer renderer;

    // Cache per reference frame
    private Map<String, LoadedDataInterval<FootprintMatrix>> loadedIntervalCache =
            Collections.synchronizedMap(new HashMap<>());

    // User-configurable parameters
    private double sigma = 1.0;
    private boolean logTransform = true;
    private int fragLenMin = 25;
    private int fragLenMax = 150;
    private boolean applyScale = true;

    public FootprintTrack(ResourceLocator locator, FootprintDataSource dataSource) {
        super(locator);
        this.dataSource = dataSource;
        this.renderer = new FootprintRenderer();
        setHeight(250);
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof FrameManager.ChangeEvent) {
            Map<String, LoadedDataInterval<FootprintMatrix>> newCache =
                    Collections.synchronizedMap(new HashMap<>());
            List<ReferenceFrame> frames = FrameManager.getFrames();
            for (ReferenceFrame f : frames) {
                LoadedDataInterval<FootprintMatrix> interval = loadedIntervalCache.get(f.getName());
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
        LoadedDataInterval<FootprintMatrix> interval = loadedIntervalCache.get(frame.getName());
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
            FootprintMatrix matrix = dataSource.getMatrix(chr, expandedStart, expandedEnd,
                    fragLenMin, fragLenMax, sigma, logTransform, applyScale);
            LoadedDataInterval<FootprintMatrix> interval =
                    new LoadedDataInterval<>(chr, expandedStart, expandedEnd, matrix);
            loadedIntervalCache.put(referenceFrame.getName(), interval);
        } catch (IOException e) {
            log.error("Error loading footprint data", e);
        }
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        LoadedDataInterval<FootprintMatrix> interval =
                loadedIntervalCache.get(context.getReferenceFrame().getName());
        if (interval == null) return;

        FootprintMatrix matrix = interval.getFeatures();
        if (matrix == null) {
            // Data source returned null — viewport too wide
            Graphics2D g = context.getGraphic2DForColor(Color.gray);
            GraphicUtils.drawCenteredText("Zoom in to see footprint data", rect, g);
            return;
        }
        renderer.render(matrix, context, rect);
    }

    public void clearCaches() {
        loadedIntervalCache.clear();
    }

    @Override
    public void unload() {
        super.unload();
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

        // Fragment length range
        JMenuItem fragRangeItem = new JMenuItem("Set Fragment Length Range (" + fragLenMin + "-" + fragLenMax + ")...");
        fragRangeItem.addActionListener(e -> {
            String result = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(),
                    "Fragment length range (min-max):", fragLenMin + "-" + fragLenMax);
            if (result != null && result.contains("-")) {
                try {
                    String[] parts = result.split("-");
                    fragLenMin = Integer.parseInt(parts[0].trim());
                    fragLenMax = Integer.parseInt(parts[1].trim());
                    clearCaches();
                    IGV.getInstance().repaint();
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        });
        menu.add(fragRangeItem);

        menu.addSeparator();
        TrackMenuUtils.addSharedItems(menu, List.of(this));

        return menu;
    }

    public double getSigma() { return sigma; }
    public void setSigma(double sigma) { this.sigma = sigma; }
    public boolean isLogTransform() { return logTransform; }
    public void setLogTransform(boolean logTransform) { this.logTransform = logTransform; }
    public int getFragLenMin() { return fragLenMin; }
    public void setFragLenMin(int fragLenMin) { this.fragLenMin = fragLenMin; }
    public int getFragLenMax() { return fragLenMax; }
    public void setFragLenMax(int fragLenMax) { this.fragLenMax = fragLenMax; }
    public boolean isApplyScale() { return applyScale; }
    public void setApplyScale(boolean applyScale) { this.applyScale = applyScale; }
}

