/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.multitrack;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.Track;
import org.broad.igv.track.TrackLoader;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.Utilities;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader for IGV Multi-Track files (.mtrack).
 * 
 * This class parses multi-track XML files and creates the corresponding
 * ResourceLocator objects and Track configurations for loading into IGV.
 * 
 * @author IGV Development Team
 */
public class MultiTrackReader {

    private static final Logger log = LogManager.getLogger(MultiTrackReader.class);
    
    private final TrackLoader trackLoader;
    private Map<String, ResourceLocator> resourceMap;
    private String basePath;
    
    public MultiTrackReader() {
        this.trackLoader = new TrackLoader();
        this.resourceMap = new HashMap<>();
    }
    
    /**
     * Load tracks from a multi-track file
     * @param inputStream The input stream containing the multi-track XML
     * @param filePath The path to the multi-track file (for relative path resolution)
     * @param genome The current genome
     * @return List of loaded tracks
     * @throws MultiTrackException If there are errors parsing or loading the file
     */
    public List<Track> loadMultiTrack(InputStream inputStream, String filePath, Genome genome) 
            throws MultiTrackException {
        
        try {
            // Set base path for relative path resolution
            if (filePath != null) {
                File file = new File(filePath);
                this.basePath = file.getParent();
            }
            
            // Parse XML document
            Document document = Utilities.createDOMDocumentFromXmlStream(inputStream);
            
            // Validate root element
            Element rootElement = document.getDocumentElement();
            if (!MultiTrackFormat.ROOT_ELEMENT.equals(rootElement.getNodeName())) {
                throw new MultiTrackException(
                    MultiTrackException.ErrorType.INVALID_XML,
                    "Root element must be '" + MultiTrackFormat.ROOT_ELEMENT + "', found: " + rootElement.getNodeName()
                );
            }
            
            // Validate version
            String version = rootElement.getAttribute(MultiTrackFormat.VERSION_ATTR);
            if (!MultiTrackFormat.isSupportedVersion(version)) {
                throw new MultiTrackException(
                    MultiTrackException.ErrorType.UNSUPPORTED_VERSION,
                    "Unsupported version: " + version + ". Supported versions: " + MultiTrackFormat.CURRENT_VERSION
                );
            }
            
            // Process resources first
            processResources(rootElement, filePath);
            
            // Process tracks and load them
            return processTracks(rootElement, genome);
            
        } catch (MultiTrackException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error loading multi-track file", e);
            throw new MultiTrackException(
                MultiTrackException.ErrorType.PARSING_ERROR,
                "Failed to parse multi-track file: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Process the Resources section of the multi-track file
     * @param rootElement The root XML element
     * @param filePath The path to the multi-track file
     * @throws MultiTrackException If there are errors processing resources
     */
    private void processResources(Element rootElement, String filePath) throws MultiTrackException {
        NodeList resourcesNodes = rootElement.getElementsByTagName(MultiTrackFormat.RESOURCES_ELEMENT);
        if (resourcesNodes.getLength() == 0) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.INVALID_XML,
                "Multi-track file must contain a '" + MultiTrackFormat.RESOURCES_ELEMENT + "' section"
            );
        }
        
        Element resourcesElement = (Element) resourcesNodes.item(0);
        NodeList resourceNodes = resourcesElement.getElementsByTagName(MultiTrackFormat.RESOURCE_ELEMENT);
        
        for (int i = 0; i < resourceNodes.getLength(); i++) {
            Element resourceElement = (Element) resourceNodes.item(i);
            try {
                ResourceLocator locator = createResourceLocator(resourceElement, filePath);
                resourceMap.put(locator.getPath(), locator);
            } catch (Exception e) {
                String resourcePath = resourceElement.getAttribute(MultiTrackFormat.PATH_ATTR);
                throw new MultiTrackException(
                    MultiTrackException.ErrorType.INVALID_TRACK_CONFIG,
                    "Failed to create resource locator: " + e.getMessage(),
                    e,
                    resourcePath,
                    null
                );
            }
        }
        
        log.info("Processed " + resourceMap.size() + " resources from multi-track file");
    }
    
    /**
     * Create a ResourceLocator from a Resource XML element
     * @param resourceElement The Resource XML element
     * @param filePath The path to the multi-track file
     * @return A configured ResourceLocator
     */
    private ResourceLocator createResourceLocator(Element resourceElement, String filePath) {
        String path = resourceElement.getAttribute(MultiTrackFormat.PATH_ATTR);
        String name = resourceElement.getAttribute(MultiTrackFormat.NAME_ATTR);
        String type = resourceElement.getAttribute(MultiTrackFormat.TYPE_ATTR);
        String index = resourceElement.getAttribute(MultiTrackFormat.INDEX_ATTR);
        String coverage = resourceElement.getAttribute(MultiTrackFormat.COVERAGE_ATTR);
        String mapping = resourceElement.getAttribute(MultiTrackFormat.MAPPING_ATTR);
        String trackLine = resourceElement.getAttribute(MultiTrackFormat.TRACK_LINE_ATTR);
        String description = resourceElement.getAttribute(MultiTrackFormat.DESCRIPTION_ATTR);
        
        // Resolve relative paths
        if (!FileUtils.isRemote(path) && basePath != null && !new File(path).isAbsolute()) {
            path = new File(basePath, path).getAbsolutePath();
        }
        
        ResourceLocator locator = new ResourceLocator(path);
        
        if (name != null && !name.isEmpty()) {
            locator.setName(name);
        }
        if (type != null && !type.isEmpty()) {
            locator.setFormat(type);
        }
        if (index != null && !index.isEmpty()) {
            if (!FileUtils.isRemote(index) && basePath != null && !new File(index).isAbsolute()) {
                index = new File(basePath, index).getAbsolutePath();
            }
            locator.setIndexPath(index);
        }
        if (coverage != null && !coverage.isEmpty()) {
            if (!FileUtils.isRemote(coverage) && basePath != null && !new File(coverage).isAbsolute()) {
                coverage = new File(basePath, coverage).getAbsolutePath();
            }
            locator.setCoverage(coverage);
        }
        if (mapping != null && !mapping.isEmpty()) {
            if (!FileUtils.isRemote(mapping) && basePath != null && !new File(mapping).isAbsolute()) {
                mapping = new File(basePath, mapping).getAbsolutePath();
            }
            locator.setMappingPath(mapping);
        }
        if (trackLine != null && !trackLine.isEmpty()) {
            locator.setTrackLine(trackLine);
        }
        if (description != null && !description.isEmpty()) {
            locator.setDescription(description);
        }
        
        return locator;
    }

    /**
     * Process the Tracks section and load the tracks
     * @param rootElement The root XML element
     * @param genome The current genome
     * @return List of loaded tracks
     * @throws MultiTrackException If there are errors processing tracks
     */
    private List<Track> processTracks(Element rootElement, Genome genome) throws MultiTrackException {
        List<Track> loadedTracks = new ArrayList<>();

        NodeList tracksNodes = rootElement.getElementsByTagName(MultiTrackFormat.TRACKS_ELEMENT);
        if (tracksNodes.getLength() == 0) {
            log.warn("No tracks section found in multi-track file");
            return loadedTracks;
        }

        Element tracksElement = (Element) tracksNodes.item(0);
        NodeList trackNodes = tracksElement.getElementsByTagName(MultiTrackFormat.TRACK_ELEMENT);

        for (int i = 0; i < trackNodes.getLength(); i++) {
            Element trackElement = (Element) trackNodes.item(i);
            try {
                List<Track> tracks = loadTrackFromElement(trackElement, genome);
                loadedTracks.addAll(tracks);
            } catch (Exception e) {
                String trackName = trackElement.getAttribute(MultiTrackFormat.NAME_ATTR);
                String resourceId = trackElement.getAttribute(MultiTrackFormat.RESOURCE_ID_ATTR);

                log.warn("Failed to load track '" + trackName + "' from resource '" + resourceId + "': " + e.getMessage());

                // Continue loading other tracks even if one fails
                // This allows partial loading of multi-track files
            }
        }

        log.info("Successfully loaded " + loadedTracks.size() + " tracks from multi-track file");
        return loadedTracks;
    }

    /**
     * Load a track from a Track XML element
     * @param trackElement The Track XML element
     * @param genome The current genome
     * @return List of loaded tracks (some file types create multiple tracks)
     * @throws MultiTrackException If there are errors loading the track
     */
    private List<Track> loadTrackFromElement(Element trackElement, Genome genome) throws MultiTrackException {
        String resourceId = trackElement.getAttribute(MultiTrackFormat.RESOURCE_ID_ATTR);
        String trackName = trackElement.getAttribute(MultiTrackFormat.NAME_ATTR);

        if (resourceId == null || resourceId.isEmpty()) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.INVALID_TRACK_CONFIG,
                "Track element must have a resourceId attribute",
                null,
                trackName
            );
        }

        ResourceLocator locator = resourceMap.get(resourceId);
        if (locator == null) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.MISSING_RESOURCE,
                "Resource not found: " + resourceId,
                resourceId,
                trackName
            );
        }

        try {
            // Load the track using the existing TrackLoader
            List<Track> tracks = trackLoader.load(locator, genome);

            // Apply track-specific configurations using IGV's standard XML unmarshaling
            for (Track track : tracks) {
                // Use IGV's standard XML unmarshaling mechanism
                track.unmarshalXML(trackElement, 8); // Use version 8 (current IGV session version)

                // Apply any additional multi-track specific configurations
                configureMultiTrackSpecific(track, trackElement);
            }

            return tracks;

        } catch (Exception e) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.IO_ERROR,
                "Failed to load track from resource: " + e.getMessage(),
                e,
                resourceId,
                trackName
            );
        }
    }

    /**
     * Apply multi-track specific configuration that's not handled by standard IGV XML unmarshaling
     * @param track The loaded track
     * @param trackElement The Track XML element containing configuration
     */
    private void configureMultiTrackSpecific(Track track, Element trackElement) {
        // Set renderer (this is multi-track specific)
        String rendererStr = trackElement.getAttribute(MultiTrackFormat.RENDERER_ATTR);
        if (rendererStr != null && !rendererStr.isEmpty()) {
            configureRenderer(track, rendererStr);
        }

        // Set sample ID (this might not be handled by standard unmarshaling)
        String sampleId = trackElement.getAttribute(MultiTrackFormat.SAMPLE_ID_ATTR);
        if (sampleId != null && !sampleId.isEmpty()) {
            track.setSampleId(sampleId);
        }

        // Note: Most other properties (name, visible, height, color, autoScale, etc.)
        // are now handled by track.unmarshalXML() which uses IGV's standard attribute names
    }

    /**
     * Configure renderer for tracks
     * @param track The track to configure
     * @param rendererType The renderer type string
     */
    private void configureRenderer(Track track, String rendererType) {
        try {
            Class rendererClass = org.broad.igv.session.RendererFactory.getRendererClass(rendererType);
            if (rendererClass != null && track instanceof org.broad.igv.track.DataTrack) {
                ((org.broad.igv.track.DataTrack) track).setRendererClass(rendererClass);
            }
        } catch (Exception e) {
            log.warn("Invalid renderer type for track " + track.getName() + ": " + rendererType);
        }
    }

    /**
     * Configure data range for numeric tracks
     * @param track The track to configure
     * @param dataRangeElement The DataRange XML element
     */
    private void configureDataRange(Track track, Element dataRangeElement) {
        try {
            String minStr = dataRangeElement.getAttribute(MultiTrackFormat.MINIMUM_ATTR);
            String maxStr = dataRangeElement.getAttribute(MultiTrackFormat.MAXIMUM_ATTR);
            String baselineStr = dataRangeElement.getAttribute(MultiTrackFormat.BASELINE_ATTR);

            if (minStr != null && !minStr.isEmpty() && maxStr != null && !maxStr.isEmpty()) {
                float min = Float.parseFloat(minStr);
                float max = Float.parseFloat(maxStr);
                float baseline = min; // Default baseline to minimum

                if (baselineStr != null && !baselineStr.isEmpty()) {
                    baseline = Float.parseFloat(baselineStr);
                }

                track.setDataRange(new org.broad.igv.renderer.DataRange(min, baseline, max));
            }

        } catch (NumberFormatException e) {
            log.warn("Invalid data range values for track " + track.getName());
        }
    }

    /**
     * Parse a color string in various formats (RGB, hex, etc.)
     * @param colorStr The color string to parse
     * @return The parsed Color object
     */
    private Color parseColor(String colorStr) {
        if (colorStr.startsWith("#")) {
            // Hex color
            return Color.decode(colorStr);
        } else if (colorStr.contains(",")) {
            // RGB format: "255,0,0"
            String[] parts = colorStr.split(",");
            if (parts.length >= 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            }
        }

        // Try to parse as a named color or throw exception
        throw new IllegalArgumentException("Unsupported color format: " + colorStr);
    }
}
