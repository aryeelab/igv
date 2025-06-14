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

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.track.Track;
import org.broad.igv.util.ResourceLocator;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test class for multi-track functionality
 */
public class MultiTrackTest extends AbstractHeadlessTest {

    @Test
    public void testMultiTrackFormat() {
        // Test format detection
        assertTrue(MultiTrackFormat.isMultiTrackFile("test.mtrack"));
        assertTrue(MultiTrackFormat.isMultiTrackFile("test.igv-tracks"));
        assertFalse(MultiTrackFormat.isMultiTrackFile("test.bam"));
        assertFalse(MultiTrackFormat.isMultiTrackFile(null));
        
        // Test version validation
        assertTrue(MultiTrackFormat.isSupportedVersion("1"));
        assertFalse(MultiTrackFormat.isSupportedVersion("2"));
        assertFalse(MultiTrackFormat.isSupportedVersion(null));
    }

    @Test
    public void testMultiTrackReader() throws Exception {
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<MultiTrack version=\"1\">\n" +
                "    <Resources>\n" +
                "        <Resource name=\"Test Track\" path=\"test.bed\" type=\"bed\"/>\n" +
                "    </Resources>\n" +
                "    <Tracks>\n" +
                "        <Track name=\"Test Track\" resourceId=\"test.bed\" visible=\"true\" height=\"50\"/>\n" +
                "    </Tracks>\n" +
                "</MultiTrack>";
        
        InputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes());
        MultiTrackReader reader = new MultiTrackReader();
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        
        // The reader should successfully parse the XML but return 0 tracks since the file doesn't exist
        List<Track> tracks = reader.loadMultiTrack(inputStream, "test.mtrack", genome);
        assertEquals("Should return 0 tracks since the file doesn't exist", 0, tracks.size());
    }

    @Test
    public void testMultiTrackException() {
        MultiTrackException ex = new MultiTrackException(
            MultiTrackException.ErrorType.INVALID_XML,
            "Test message",
            "test.mtrack",
            "Test Track"
        );
        
        assertEquals(MultiTrackException.ErrorType.INVALID_XML, ex.getErrorType());
        assertEquals("test.mtrack", ex.getResourcePath());
        assertEquals("Test Track", ex.getTrackName());
        assertTrue(ex.getUserMessage().contains("Invalid XML format"));
        assertTrue(ex.getUserMessage().contains("Test Track"));
        assertTrue(ex.getUserMessage().contains("test.mtrack"));
    }

    @Test
    public void testInvalidXmlFormat() throws Exception {
        String invalidXml = "<?xml version=\"1.0\"?>\n<InvalidRoot></InvalidRoot>";
        
        InputStream inputStream = new ByteArrayInputStream(invalidXml.getBytes());
        MultiTrackReader reader = new MultiTrackReader();
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        
        try {
            reader.loadMultiTrack(inputStream, "test.mtrack", genome);
            fail("Expected MultiTrackException for invalid XML");
        } catch (MultiTrackException e) {
            assertEquals(MultiTrackException.ErrorType.INVALID_XML, e.getErrorType());
        }
    }

    @Test
    public void testUnsupportedVersion() throws Exception {
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<MultiTrack version=\"999\">\n" +
                "    <Resources></Resources>\n" +
                "    <Tracks></Tracks>\n" +
                "</MultiTrack>";
        
        InputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes());
        MultiTrackReader reader = new MultiTrackReader();
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        
        try {
            reader.loadMultiTrack(inputStream, "test.mtrack", genome);
            fail("Expected MultiTrackException for unsupported version");
        } catch (MultiTrackException e) {
            assertEquals(MultiTrackException.ErrorType.UNSUPPORTED_VERSION, e.getErrorType());
        }
    }

    @Test
    public void testMultiTrackLoader() throws Exception {
        MultiTrackLoader loader = new MultiTrackLoader();
        
        // Test with non-existent file
        ResourceLocator locator = new ResourceLocator("nonexistent.mtrack");
        try {
            loader.loadMultiTrackFile(locator);
            fail("Expected MultiTrackException for non-existent file");
        } catch (MultiTrackException e) {
            assertEquals(MultiTrackException.ErrorType.IO_ERROR, e.getErrorType());
        }
    }
}
