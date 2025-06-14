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
import org.broad.igv.track.Track;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.Utilities;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Writer for creating IGV Multi-Track files (.mtrack).
 * 
 * This class creates multi-track XML files from a collection of tracks,
 * preserving track configurations and resource information.
 * 
 * @author IGV Development Team
 */
public class MultiTrackWriter {

    private static final Logger log = LogManager.getLogger(MultiTrackWriter.class);
    
    private boolean useRelativePaths = true;
    
    /**
     * Create a new MultiTrackWriter
     */
    public MultiTrackWriter() {
    }
    
    /**
     * Set whether to use relative paths in the output file
     * @param useRelativePaths true to use relative paths when possible
     */
    public void setUseRelativePaths(boolean useRelativePaths) {
        this.useRelativePaths = useRelativePaths;
    }
    
    /**
     * Save tracks to a multi-track file
     * @param tracks The tracks to save
     * @param outputFile The output file
     * @throws MultiTrackException If there are errors writing the file
     */
    public void saveMultiTrack(Collection<Track> tracks, File outputFile) throws MultiTrackException {
        if (tracks == null || tracks.isEmpty()) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.INVALID_TRACK_CONFIG,
                "No tracks provided to save"
            );
        }
        
        if (outputFile == null) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.IO_ERROR,
                "Output file cannot be null"
            );
        }
        
        try {
            String xmlString = createXmlFromTracks(tracks, outputFile);
            
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
                writer.write(xmlString);
            }
            
            log.info("Successfully saved " + tracks.size() + " tracks to multi-track file: " + outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.IO_ERROR,
                "Failed to write multi-track file: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Create XML string from a collection of tracks
     * @param tracks The tracks to serialize
     * @param outputFile The output file (for relative path calculation)
     * @return XML string representation
     * @throws Exception If there are errors creating the XML
     */
    private String createXmlFromTracks(Collection<Track> tracks, File outputFile) throws Exception {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = documentBuilder.newDocument();
        document.setStrictErrorChecking(true);
        
        // Create root element
        Element rootElement = document.createElement(MultiTrackFormat.ROOT_ELEMENT);
        rootElement.setAttribute(MultiTrackFormat.VERSION_ATTR, MultiTrackFormat.CURRENT_VERSION);
        document.appendChild(rootElement);
        
        // Create resources section
        Element resourcesElement = document.createElement(MultiTrackFormat.RESOURCES_ELEMENT);
        rootElement.appendChild(resourcesElement);
        
        // Create tracks section
        Element tracksElement = document.createElement(MultiTrackFormat.TRACKS_ELEMENT);
        rootElement.appendChild(tracksElement);
        
        // Collect unique resources
        Set<ResourceLocator> uniqueResources = new HashSet<>();
        for (Track track : tracks) {
            Collection<ResourceLocator> locators = track.getResourceLocators();
            if (locators != null) {
                uniqueResources.addAll(locators);
            }
        }
        
        // Write resources
        for (ResourceLocator locator : uniqueResources) {
            Element resourceElement = createResourceElement(document, locator, outputFile);
            resourcesElement.appendChild(resourceElement);
        }
        
        // Write tracks
        for (Track track : tracks) {
            Element trackElement = createTrackElement(document, track, outputFile);
            tracksElement.appendChild(trackElement);
        }
        
        return Utilities.getString(document);
    }
    
    /**
     * Create a Resource XML element from a ResourceLocator
     * @param document The XML document
     * @param locator The resource locator
     * @param outputFile The output file (for relative path calculation)
     * @return Resource XML element
     */
    private Element createResourceElement(Document document, ResourceLocator locator, File outputFile) {
        Element resourceElement = document.createElement(MultiTrackFormat.RESOURCE_ELEMENT);
        
        String path = locator.getPath();
        if (useRelativePaths && !FileUtils.isRemote(path) && outputFile != null) {
            path = FileUtils.getRelativePath(outputFile.getAbsolutePath(), path);
        }
        resourceElement.setAttribute(MultiTrackFormat.PATH_ATTR, path);
        
        if (locator.getName() != null && !locator.getName().isEmpty()) {
            resourceElement.setAttribute(MultiTrackFormat.NAME_ATTR, locator.getName());
        }
        
        if (locator.getFormat() != null && !locator.getFormat().isEmpty()) {
            resourceElement.setAttribute(MultiTrackFormat.TYPE_ATTR, locator.getFormat());
        }
        
        if (locator.getIndexPath() != null && !locator.getIndexPath().isEmpty()) {
            String indexPath = locator.getIndexPath();
            if (useRelativePaths && !FileUtils.isRemote(indexPath) && outputFile != null) {
                indexPath = FileUtils.getRelativePath(outputFile.getAbsolutePath(), indexPath);
            }
            resourceElement.setAttribute(MultiTrackFormat.INDEX_ATTR, indexPath);
        }
        
        if (locator.getCoverage() != null && !locator.getCoverage().isEmpty()) {
            String coveragePath = locator.getCoverage();
            if (useRelativePaths && !FileUtils.isRemote(coveragePath) && outputFile != null) {
                coveragePath = FileUtils.getRelativePath(outputFile.getAbsolutePath(), coveragePath);
            }
            resourceElement.setAttribute(MultiTrackFormat.COVERAGE_ATTR, coveragePath);
        }
        
        if (locator.getMappingPath() != null && !locator.getMappingPath().isEmpty()) {
            String mappingPath = locator.getMappingPath();
            if (useRelativePaths && !FileUtils.isRemote(mappingPath) && outputFile != null) {
                mappingPath = FileUtils.getRelativePath(outputFile.getAbsolutePath(), mappingPath);
            }
            resourceElement.setAttribute(MultiTrackFormat.MAPPING_ATTR, mappingPath);
        }
        
        if (locator.getTrackLine() != null && !locator.getTrackLine().isEmpty()) {
            resourceElement.setAttribute(MultiTrackFormat.TRACK_LINE_ATTR, locator.getTrackLine());
        }
        
        if (locator.getDescription() != null && !locator.getDescription().isEmpty()) {
            resourceElement.setAttribute(MultiTrackFormat.DESCRIPTION_ATTR, locator.getDescription());
        }
        
        return resourceElement;
    }
    
    /**
     * Create a Track XML element from a Track object
     * @param document The XML document
     * @param track The track
     * @param outputFile The output file (for relative path calculation)
     * @return Track XML element
     */
    private Element createTrackElement(Document document, Track track, File outputFile) {
        Element trackElement = document.createElement(MultiTrackFormat.TRACK_ELEMENT);
        
        // Basic track attributes
        if (track.getName() != null && !track.getName().isEmpty()) {
            trackElement.setAttribute(MultiTrackFormat.NAME_ATTR, track.getName());
        }
        
        // Resource ID (path to the primary resource)
        ResourceLocator primaryLocator = track.getResourceLocator();
        if (primaryLocator != null) {
            String resourceId = primaryLocator.getPath();
            trackElement.setAttribute(MultiTrackFormat.RESOURCE_ID_ATTR, resourceId);
        }
        
        // Track configuration
        trackElement.setAttribute(MultiTrackFormat.VISIBLE_ATTR, String.valueOf(track.isVisible()));
        trackElement.setAttribute(MultiTrackFormat.HEIGHT_ATTR, String.valueOf(track.getHeight()));
        trackElement.setAttribute(MultiTrackFormat.FONT_SIZE_ATTR, String.valueOf(track.getFontSize()));
        
        if (track.getAutoScale()) {
            trackElement.setAttribute(MultiTrackFormat.AUTO_SCALE_ATTR, "true");
        }
        
        // Colors
        Color color = track.getColor();
        if (color != null) {
            trackElement.setAttribute(MultiTrackFormat.COLOR_ATTR, colorToString(color));
        }
        
        Color altColor = track.getAltColor();
        if (altColor != null) {
            trackElement.setAttribute(MultiTrackFormat.ALT_COLOR_ATTR, colorToString(altColor));
        }
        
        // Sample ID
        if (track.getSample() != null && !track.getSample().isEmpty()) {
            trackElement.setAttribute(MultiTrackFormat.SAMPLE_ID_ATTR, track.getSample());
        }
        
        // Data range for numeric tracks
        if (track.isNumeric() && track.getDataRange() != null) {
            Element dataRangeElement = document.createElement(MultiTrackFormat.DATA_RANGE_ELEMENT);
            dataRangeElement.setAttribute(MultiTrackFormat.MINIMUM_ATTR, 
                String.valueOf(track.getDataRange().getMinimum()));
            dataRangeElement.setAttribute(MultiTrackFormat.MAXIMUM_ATTR, 
                String.valueOf(track.getDataRange().getMaximum()));
            dataRangeElement.setAttribute(MultiTrackFormat.BASELINE_ATTR, 
                String.valueOf(track.getDataRange().getBaseline()));
            dataRangeElement.setAttribute(MultiTrackFormat.SCALE_TYPE_ATTR, 
                track.getDataRange().getType().toString());
            
            trackElement.appendChild(dataRangeElement);
        }
        
        return trackElement;
    }
    
    /**
     * Convert a Color to a string representation
     * @param color The color to convert
     * @return String representation (RGB format)
     */
    private String colorToString(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
    }
}
