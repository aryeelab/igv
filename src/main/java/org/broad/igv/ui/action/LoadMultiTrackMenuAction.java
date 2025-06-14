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

package org.broad.igv.ui.action;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.multitrack.MultiTrackException;
import org.broad.igv.multitrack.MultiTrackFormat;
import org.broad.igv.multitrack.MultiTrackLoader;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.NamedRunnable;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Menu action for loading IGV Multi-Track files.
 * 
 * This action provides a file dialog for selecting multi-track files
 * and loads them into the current IGV session.
 * 
 * @author IGV Development Team
 */
public class LoadMultiTrackMenuAction extends MenuAction {

    private static final Logger log = LogManager.getLogger(LoadMultiTrackMenuAction.class);
    
    private final IGV igv;
    
    public LoadMultiTrackMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
        setToolTipText("Load multiple tracks from a multi-track file (.mtrack)");
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        File multiTrackFile = chooseMultiTrackFile();
        if (multiTrackFile != null) {
            loadMultiTrackFile(multiTrackFile);
        }
    }
    
    /**
     * Show file dialog to choose a multi-track file
     * @return Selected file, or null if cancelled
     */
    private File chooseMultiTrackFile() {
        File lastDirectory = PreferencesManager.getPreferences().getLastTrackDirectory();

        File selectedFile = FileDialogUtils.chooseFile(
            "Load Multi-Track File",
            lastDirectory,
            FileDialogUtils.LOAD
        );

        if (selectedFile != null) {
            // Update last directory
            PreferencesManager.getPreferences().setLastTrackDirectory(selectedFile.getParentFile());
        }

        return selectedFile;
    }
    
    /**
     * Load a multi-track file in a background thread
     * @param file The multi-track file to load
     */
    private void loadMultiTrackFile(File file) {
        if (!file.exists()) {
            MessageUtils.showErrorMessage("File not found: " + file.getAbsolutePath(), null);
            return;
        }
        
        if (!MultiTrackFormat.isMultiTrackFile(file.getName())) {
            MessageUtils.showErrorMessage(
                "Invalid file type. Expected " + MultiTrackFormat.getFileFilterDescription(),
                null
            );
            return;
        }
        
        // Show loading message
        igv.getContentPane().getStatusBar().setMessage("Loading multi-track file...");
        
        NamedRunnable loadTask = new NamedRunnable() {
            @Override
            public void run() {
                try {
                    ResourceLocator locator = new ResourceLocator(file.getAbsolutePath());
                    MultiTrackLoader loader = new MultiTrackLoader();
                    
                    // Get file info first for user feedback
                    MultiTrackLoader.MultiTrackInfo info = loader.getMultiTrackInfo(locator);
                    log.info("Loading multi-track file: " + info.getTrackCount() + " tracks, " + info.getResourceCount() + " resources");
                    
                    // Load and add tracks
                    loader.loadAndAddTracks(locator);
                    
                    // Update UI on EDT
                    SwingUtilities.invokeLater(() -> {
                        igv.resetStatusMessage();
                        igv.revalidateTrackPanels();
                        
                        String message = String.format(
                            "Successfully loaded %d tracks from multi-track file",
                            info.getTrackCount()
                        );
                        MessageUtils.showMessage(message);
                    });
                    
                } catch (MultiTrackException ex) {
                    log.error("Error loading multi-track file", ex);
                    SwingUtilities.invokeLater(() -> {
                        igv.resetStatusMessage();
                        String userMessage = ex.getUserMessage();
                        MessageUtils.showErrorMessage("Error loading multi-track file: " + userMessage, ex);
                    });
                    
                } catch (Exception ex) {
                    log.error("Unexpected error loading multi-track file", ex);
                    SwingUtilities.invokeLater(() -> {
                        igv.resetStatusMessage();
                        MessageUtils.showErrorMessage(
                            "Unexpected error loading multi-track file: " + ex.getMessage(), 
                            ex
                        );
                    });
                }
            }
            
            @Override
            public String getName() {
                return "Load Multi-Track File";
            }
        };
        
        LongRunningTask.submit(loadTask);
    }
}
