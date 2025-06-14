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

/**
 * Constants and utilities for the IGV Multi-Track file format.
 * 
 * The multi-track format is a subset of IGV's session XML format,
 * containing only track definitions and configurations without
 * session-level settings like genome, locus, or viewport information.
 * 
 * @author IGV Development Team
 */
public class MultiTrackFormat {

    // Format version
    public static final String CURRENT_VERSION = "1";
    
    // File extensions
    public static final String PRIMARY_EXTENSION = ".mtrack";
    public static final String ALTERNATIVE_EXTENSION = ".igv-tracks";
    
    // MIME type
    public static final String MIME_TYPE = "application/x-igv-multitrack+xml";
    
    // XML Elements
    public static final String ROOT_ELEMENT = "MultiTrack";
    public static final String RESOURCES_ELEMENT = "Resources";
    public static final String TRACKS_ELEMENT = "Tracks";
    public static final String RESOURCE_ELEMENT = "Resource";
    public static final String TRACK_ELEMENT = "Track";
    public static final String DATA_RANGE_ELEMENT = "DataRange";
    
    // XML Attributes
    public static final String VERSION_ATTR = "version";
    public static final String NAME_ATTR = "name";
    public static final String PATH_ATTR = "path";
    public static final String TYPE_ATTR = "type";
    public static final String INDEX_ATTR = "index";
    public static final String COVERAGE_ATTR = "coverage";
    public static final String MAPPING_ATTR = "mapping";
    public static final String TRACK_LINE_ATTR = "trackLine";
    public static final String COLOR_ATTR = "color";
    public static final String ALT_COLOR_ATTR = "altColor";
    public static final String VISIBLE_ATTR = "visible";
    public static final String HEIGHT_ATTR = "height";
    public static final String RESOURCE_ID_ATTR = "resourceId";
    public static final String DISPLAY_MODE_ATTR = "displayMode";
    public static final String FONT_SIZE_ATTR = "fontSize";
    public static final String AUTO_SCALE_ATTR = "autoScale";
    public static final String RENDERER_ATTR = "renderer";
    public static final String SAMPLE_ID_ATTR = "sampleId";
    public static final String DESCRIPTION_ATTR = "description";
    public static final String LABEL_FIELD_ATTR = "labelField";
    public static final String FEATURE_URL_ATTR = "featureURL";
    
    // Data Range Attributes
    public static final String MINIMUM_ATTR = "minimum";
    public static final String MAXIMUM_ATTR = "maximum";
    public static final String BASELINE_ATTR = "baseline";
    public static final String DRAW_BASELINE_ATTR = "drawBaseline";
    public static final String FLIP_AXIS_ATTR = "flipAxis";
    public static final String SCALE_TYPE_ATTR = "type";
    
    // Default values
    public static final boolean DEFAULT_VISIBLE = true;
    public static final int DEFAULT_HEIGHT = 50;
    public static final int DEFAULT_FONT_SIZE = 10;
    public static final boolean DEFAULT_AUTO_SCALE = false;
    
    /**
     * Check if a file path has a multi-track file extension
     * @param path The file path to check
     * @return true if the path ends with a multi-track extension
     */
    public static boolean isMultiTrackFile(String path) {
        if (path == null) {
            return false;
        }
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(PRIMARY_EXTENSION) || 
               lowerPath.endsWith(ALTERNATIVE_EXTENSION);
    }
    
    /**
     * Get the appropriate file extension for multi-track files
     * @return The primary file extension
     */
    public static String getFileExtension() {
        return PRIMARY_EXTENSION;
    }
    
    /**
     * Get a file filter description for file dialogs
     * @return A description string for file filters
     */
    public static String getFileFilterDescription() {
        return "IGV Multi-Track Files (*" + PRIMARY_EXTENSION + ", *" + ALTERNATIVE_EXTENSION + ")";
    }
    
    /**
     * Get all supported file extensions as an array
     * @return Array of supported extensions
     */
    public static String[] getSupportedExtensions() {
        return new String[]{PRIMARY_EXTENSION, ALTERNATIVE_EXTENSION};
    }
    
    /**
     * Validate that a version string is supported
     * @param version The version string to validate
     * @return true if the version is supported
     */
    public static boolean isSupportedVersion(String version) {
        if (version == null) {
            return false;
        }
        // Currently only version 1 is supported
        return CURRENT_VERSION.equals(version);
    }
    
    /**
     * Get a user-friendly format name
     * @return The format name for display purposes
     */
    public static String getFormatName() {
        return "IGV Multi-Track";
    }
}
