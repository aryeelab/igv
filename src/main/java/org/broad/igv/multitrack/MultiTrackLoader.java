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
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.panel.IGVPanel;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.util.ResourceLocator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Loader for IGV Multi-Track files.
 * 
 * This class handles the loading of multi-track files and integration
 * with IGV's track management system. It provides methods to load tracks
 * from multi-track files and add them to the current IGV session.
 * 
 * @author IGV Development Team
 */
public class MultiTrackLoader {

    private static final Logger log = LogManager.getLogger(MultiTrackLoader.class);
    
    private final MultiTrackReader reader;
    
    public MultiTrackLoader() {
        this.reader = new MultiTrackReader();
    }
    
    /**
     * Load tracks from a multi-track file
     * @param locator ResourceLocator pointing to the multi-track file
     * @return List of loaded tracks
     * @throws MultiTrackException If there are errors loading the file
     */
    public List<Track> loadMultiTrackFile(ResourceLocator locator) throws MultiTrackException {
        return loadMultiTrackFile(locator, GenomeManager.getInstance().getCurrentGenome());
    }
    
    /**
     * Load tracks from a multi-track file with a specific genome
     * @param locator ResourceLocator pointing to the multi-track file
     * @param genome The genome to use for loading tracks
     * @return List of loaded tracks
     * @throws MultiTrackException If there are errors loading the file
     */
    public List<Track> loadMultiTrackFile(ResourceLocator locator, Genome genome) throws MultiTrackException {
        if (genome == null) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.GENOME_MISMATCH,
                "No genome is currently loaded. Please load a genome before loading tracks."
            );
        }
        
        String filePath = locator.getPath();
        log.info("Loading multi-track file: " + filePath);
        
        try (InputStream inputStream = openInputStream(locator)) {
            List<Track> tracks = reader.loadMultiTrack(inputStream, filePath, genome);
            
            // Validate and process tracks
            validateTracks(tracks, genome);
            handleDuplicateTrackNames(tracks);
            
            log.info("Successfully loaded " + tracks.size() + " tracks from multi-track file");
            return tracks;
            
        } catch (IOException e) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.IO_ERROR,
                "Failed to read multi-track file: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Load tracks from a multi-track file and add them to the current IGV session
     * @param locator ResourceLocator pointing to the multi-track file
     * @throws MultiTrackException If there are errors loading the file
     */
    public void loadAndAddTracks(ResourceLocator locator) throws MultiTrackException {
        List<Track> tracks = loadMultiTrackFile(locator);
        addTracksToSession(tracks);
    }
    
    /**
     * Add a list of tracks to the current IGV session
     * @param tracks The tracks to add
     */
    public void addTracksToSession(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            log.warn("No tracks to add to session");
            return;
        }

        IGV igv = IGV.getInstance();
        if (igv == null) {
            log.error("IGV instance not available");
            return;
        }

        // Add tracks one by one to preserve order
        // IGV.addTracks() groups by resource path which can scramble order
        for (Track track : tracks) {
            igv.addTrack(track);
        }

        log.info("Added " + tracks.size() + " tracks to IGV session");
    }
    
    /**
     * Open an input stream for the given resource locator
     * @param locator The resource locator
     * @return An input stream for reading the resource
     * @throws IOException If there are errors opening the stream
     */
    private InputStream openInputStream(ResourceLocator locator) throws IOException {
        String path = locator.getPath();
        
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("ftp://")) {
            // For remote files, use IGV's existing URL handling
            return org.broad.igv.util.ParsingUtils.openInputStream(path);
        } else {
            // For local files
            return new FileInputStream(path);
        }
    }
    
    /**
     * Validate that tracks are compatible with the current genome
     * @param tracks The tracks to validate
     * @param genome The current genome
     * @throws MultiTrackException If tracks are incompatible
     */
    private void validateTracks(List<Track> tracks, Genome genome) throws MultiTrackException {
        for (Track track : tracks) {
            try {
                // Basic validation - check if track can be loaded with current genome
                // More specific validation could be added here based on track type
                if (track.getResourceLocator() != null) {
                    String path = track.getResourceLocator().getPath();
                    if (path != null && !path.isEmpty()) {
                        // Check if file exists for local files
                        if (!path.startsWith("http") && !path.startsWith("ftp")) {
                            java.io.File file = new java.io.File(path);
                            if (!file.exists()) {
                                log.warn("Track file does not exist: " + path + " (track: " + track.getName() + ")");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Validation warning for track " + track.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle duplicate track names by appending numbers
     * @param tracks The tracks to process
     */
    private void handleDuplicateTrackNames(List<Track> tracks) {
        Map<String, Integer> nameCount = new HashMap<>();
        
        for (Track track : tracks) {
            String originalName = track.getName();
            if (originalName == null || originalName.isEmpty()) {
                originalName = "Unnamed Track";
                track.setName(originalName);
            }
            
            String baseName = originalName;
            Integer count = nameCount.get(baseName);
            
            if (count == null) {
                // First occurrence of this name
                nameCount.put(baseName, 1);
            } else {
                // Duplicate name - append number
                count++;
                nameCount.put(baseName, count);
                String newName = baseName + " (" + count + ")";
                track.setName(newName);
                log.info("Renamed duplicate track '" + originalName + "' to '" + newName + "'");
            }
        }
    }
    
    /**
     * Get information about a multi-track file without fully loading it
     * @param locator ResourceLocator pointing to the multi-track file
     * @return MultiTrackInfo object containing file information
     * @throws MultiTrackException If there are errors reading the file
     */
    public MultiTrackInfo getMultiTrackInfo(ResourceLocator locator) throws MultiTrackException {
        try (InputStream inputStream = openInputStream(locator)) {
            return MultiTrackInfo.fromInputStream(inputStream);
        } catch (IOException e) {
            throw new MultiTrackException(
                MultiTrackException.ErrorType.IO_ERROR,
                "Failed to read multi-track file info: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Information about a multi-track file
     */
    public static class MultiTrackInfo {
        private final String version;
        private final int resourceCount;
        private final int trackCount;
        private final List<String> trackNames;
        private final List<String> resourceTypes;
        
        public MultiTrackInfo(String version, int resourceCount, int trackCount, 
                             List<String> trackNames, List<String> resourceTypes) {
            this.version = version;
            this.resourceCount = resourceCount;
            this.trackCount = trackCount;
            this.trackNames = new ArrayList<>(trackNames);
            this.resourceTypes = new ArrayList<>(resourceTypes);
        }
        
        public String getVersion() { return version; }
        public int getResourceCount() { return resourceCount; }
        public int getTrackCount() { return trackCount; }
        public List<String> getTrackNames() { return new ArrayList<>(trackNames); }
        public List<String> getResourceTypes() { return new ArrayList<>(resourceTypes); }
        
        /**
         * Create MultiTrackInfo from an input stream without fully parsing
         * @param inputStream The input stream to read from
         * @return MultiTrackInfo object
         * @throws MultiTrackException If there are errors reading the stream
         */
        public static MultiTrackInfo fromInputStream(InputStream inputStream) throws MultiTrackException {
            try {
                org.w3c.dom.Document document = org.broad.igv.util.Utilities.createDOMDocumentFromXmlStream(inputStream);
                org.w3c.dom.Element rootElement = document.getDocumentElement();
                
                String version = rootElement.getAttribute(MultiTrackFormat.VERSION_ATTR);
                
                org.w3c.dom.NodeList resourceNodes = document.getElementsByTagName(MultiTrackFormat.RESOURCE_ELEMENT);
                org.w3c.dom.NodeList trackNodes = document.getElementsByTagName(MultiTrackFormat.TRACK_ELEMENT);
                
                List<String> trackNames = new ArrayList<>();
                List<String> resourceTypes = new ArrayList<>();
                
                for (int i = 0; i < trackNodes.getLength(); i++) {
                    org.w3c.dom.Element trackElement = (org.w3c.dom.Element) trackNodes.item(i);
                    String name = trackElement.getAttribute(MultiTrackFormat.NAME_ATTR);
                    trackNames.add(name != null && !name.isEmpty() ? name : "Track " + (i + 1));
                }
                
                for (int i = 0; i < resourceNodes.getLength(); i++) {
                    org.w3c.dom.Element resourceElement = (org.w3c.dom.Element) resourceNodes.item(i);
                    String type = resourceElement.getAttribute(MultiTrackFormat.TYPE_ATTR);
                    resourceTypes.add(type != null && !type.isEmpty() ? type : "unknown");
                }
                
                return new MultiTrackInfo(version, resourceNodes.getLength(), trackNodes.getLength(), trackNames, resourceTypes);
                
            } catch (Exception e) {
                throw new MultiTrackException(
                    MultiTrackException.ErrorType.PARSING_ERROR,
                    "Failed to parse multi-track file info: " + e.getMessage(),
                    e
                );
            }
        }
        
        @Override
        public String toString() {
            return String.format("MultiTrackInfo{version='%s', resources=%d, tracks=%d}", 
                               version, resourceCount, trackCount);
        }
    }
}
