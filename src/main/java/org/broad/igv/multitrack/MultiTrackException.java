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
 * Exception thrown when there are errors processing multi-track files.
 * 
 * This exception provides specific error handling for multi-track file
 * operations including parsing, validation, and track loading errors.
 * 
 * @author IGV Development Team
 */
public class MultiTrackException extends Exception {

    /**
     * Error types for categorizing multi-track exceptions
     */
    public enum ErrorType {
        INVALID_XML("Invalid XML format"),
        UNSUPPORTED_VERSION("Unsupported file version"),
        MISSING_RESOURCE("Resource file not found"),
        INVALID_TRACK_CONFIG("Invalid track configuration"),
        DUPLICATE_TRACK_NAME("Duplicate track name"),
        GENOME_MISMATCH("Track incompatible with current genome"),
        IO_ERROR("File input/output error"),
        PARSING_ERROR("Error parsing file content");
        
        private final String description;
        
        ErrorType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final ErrorType errorType;
    private final String resourcePath;
    private final String trackName;
    
    /**
     * Create a new MultiTrackException with an error type and message
     * @param errorType The type of error that occurred
     * @param message Detailed error message
     */
    public MultiTrackException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.resourcePath = null;
        this.trackName = null;
    }
    
    /**
     * Create a new MultiTrackException with an error type, message, and cause
     * @param errorType The type of error that occurred
     * @param message Detailed error message
     * @param cause The underlying cause of the error
     */
    public MultiTrackException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.resourcePath = null;
        this.trackName = null;
    }
    
    /**
     * Create a new MultiTrackException with context information
     * @param errorType The type of error that occurred
     * @param message Detailed error message
     * @param resourcePath The path to the resource that caused the error (optional)
     * @param trackName The name of the track that caused the error (optional)
     */
    public MultiTrackException(ErrorType errorType, String message, String resourcePath, String trackName) {
        super(message);
        this.errorType = errorType;
        this.resourcePath = resourcePath;
        this.trackName = trackName;
    }
    
    /**
     * Create a new MultiTrackException with context information and cause
     * @param errorType The type of error that occurred
     * @param message Detailed error message
     * @param cause The underlying cause of the error
     * @param resourcePath The path to the resource that caused the error (optional)
     * @param trackName The name of the track that caused the error (optional)
     */
    public MultiTrackException(ErrorType errorType, String message, Throwable cause, String resourcePath, String trackName) {
        super(message, cause);
        this.errorType = errorType;
        this.resourcePath = resourcePath;
        this.trackName = trackName;
    }
    
    /**
     * Get the error type
     * @return The ErrorType that categorizes this exception
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Get the resource path associated with this error
     * @return The resource path, or null if not applicable
     */
    public String getResourcePath() {
        return resourcePath;
    }
    
    /**
     * Get the track name associated with this error
     * @return The track name, or null if not applicable
     */
    public String getTrackName() {
        return trackName;
    }
    
    /**
     * Get a user-friendly error message that includes context information
     * @return A formatted error message suitable for display to users
     */
    public String getUserMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(errorType.getDescription());
        
        if (trackName != null) {
            sb.append(" for track '").append(trackName).append("'");
        }
        
        if (resourcePath != null) {
            sb.append(" (resource: ").append(resourcePath).append(")");
        }
        
        String message = getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "MultiTrackException{" +
                "errorType=" + errorType +
                ", resourcePath='" + resourcePath + '\'' +
                ", trackName='" + trackName + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
