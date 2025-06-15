#!/usr/bin/env python3
"""
IGV Multi-Track File Generator

This script generates IGV multi-track (.mtrack) files from a collection of genomic data files.
The generated files are compatible with IGV's multi-track loading feature.

Author: IGV Development Team
License: MIT
"""

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Tuple, Dict


class IGVMultiTrackGenerator:
    """Generator for IGV multi-track XML files."""
    
    # Supported file formats and their types
    SUPPORTED_FORMATS = {
        '.bw': 'bigwig',
        '.bigwig': 'bigwig',
        '.wig': 'wig',
        '.bam': 'bam',
        '.bed': 'bed',
        '.bedgraph': 'bedgraph',
        '.tdf': 'tdf',
        '.vcf': 'vcf',
        '.gff': 'gff',
        '.gtf': 'gtf',
        '.gff3': 'gff3'
    }
    
    # Valid renderer options
    VALID_RENDERERS = ['HEATMAP', 'BAR_CHART', 'POINTS', 'LINE_PLOT']
    
    def __init__(self):
        self.tracks = []
        self.resources = []
    
    def detect_file_type(self, filepath: str) -> str:
        """Detect file type from extension."""
        path = Path(filepath)
        ext = path.suffix.lower()
        
        # Handle compressed files
        if ext == '.gz':
            ext = path.with_suffix('').suffix.lower()
        
        return self.SUPPORTED_FORMATS.get(ext, 'unknown')
    
    def extract_track_name(self, filepath: str) -> str:
        """Extract track name from filename."""
        path = Path(filepath)
        name = path.stem
        
        # Remove .gz if present
        if name.endswith('.gz'):
            name = name[:-3]
        
        # Clean up common suffixes
        suffixes_to_remove = ['.sorted', '.filtered', '.processed']
        for suffix in suffixes_to_remove:
            if name.endswith(suffix):
                name = name[:-len(suffix)]
        
        return name
    
    def validate_files(self, filepaths: List[str]) -> List[str]:
        """Validate that files exist and are supported formats."""
        valid_files = []
        
        for filepath in filepaths:
            if not os.path.exists(filepath):
                print(f"Warning: File not found: {filepath}", file=sys.stderr)
                continue
            
            file_type = self.detect_file_type(filepath)
            if file_type == 'unknown':
                print(f"Warning: Unsupported file format: {filepath}", file=sys.stderr)
                continue
            
            valid_files.append(filepath)
        
        return valid_files
    
    def expand_wildcards(self, patterns: List[str]) -> List[str]:
        """Expand wildcard patterns to actual file paths."""
        all_files = []
        
        for pattern in patterns:
            if '*' in pattern or '?' in pattern:
                expanded = glob.glob(pattern)
                if not expanded:
                    print(f"Warning: No files found matching pattern: {pattern}", file=sys.stderr)
                else:
                    all_files.extend(expanded)
            else:
                all_files.append(pattern)
        
        return sorted(set(all_files))  # Remove duplicates and sort
    
    def calculate_track_height(self, total_height: int, num_tracks: int) -> int:
        """Calculate individual track height from total height."""
        if num_tracks == 0:
            return 50  # Default height
        
        individual_height = round(total_height / num_tracks)
        return max(1, individual_height)  # Minimum 1 pixel
    
    def generate_mtrack(self, 
                       output_path: str,
                       track_patterns: List[str],
                       renderer: str = 'HEATMAP',
                       total_height: int = 40,
                       use_relative_paths: bool = False,
                       color: str = '139,0,0',
                       scale_max: int = 100) -> None:
        """Generate the multi-track XML file."""
        
        # Expand wildcards and validate files
        filepaths = self.expand_wildcards(track_patterns)
        valid_files = self.validate_files(filepaths)
        
        if not valid_files:
            raise ValueError("No valid input files found")
        
        # Calculate track height
        track_height = self.calculate_track_height(total_height, len(valid_files))
        
        if track_height < 3:
            print(f"Warning: Individual track height ({track_height}px) is very small. "
                  f"Consider increasing --total-height or reducing number of tracks.", 
                  file=sys.stderr)
        
        # Create XML structure
        root = ET.Element('MultiTrack', version='1')
        
        # Add Resources section
        resources_elem = ET.SubElement(root, 'Resources')
        
        # Add Tracks section
        tracks_elem = ET.SubElement(root, 'Tracks')
        
        # Process each file
        for filepath in valid_files:
            # Determine path to use in XML
            if use_relative_paths:
                try:
                    xml_path = os.path.relpath(filepath, os.path.dirname(output_path))
                except ValueError:
                    # Can't create relative path (different drives on Windows)
                    xml_path = filepath
            else:
                xml_path = os.path.abspath(filepath)
            
            # Extract track information
            track_name = self.extract_track_name(filepath)
            file_type = self.detect_file_type(filepath)
            
            # Add Resource element
            resource_elem = ET.SubElement(resources_elem, 'Resource')
            resource_elem.set('name', track_name)
            resource_elem.set('path', xml_path)
            resource_elem.set('type', file_type)
            
            # Add index file for BAM files
            if file_type == 'bam':
                bai_path = filepath + '.bai'
                if os.path.exists(bai_path):
                    if use_relative_paths:
                        try:
                            bai_xml_path = os.path.relpath(bai_path, os.path.dirname(output_path))
                        except ValueError:
                            bai_xml_path = bai_path
                    else:
                        bai_xml_path = os.path.abspath(bai_path)
                    resource_elem.set('index', bai_xml_path)
            
            # Add Track element
            track_elem = ET.SubElement(tracks_elem, 'Track')
            track_elem.set('name', track_name)
            track_elem.set('resourceId', xml_path)
            track_elem.set('visible', 'true')
            track_elem.set('height', str(track_height))
            track_elem.set('color', color)
            track_elem.set('autoScale', 'true')
            track_elem.set('renderer', renderer)
            
            # Add DataRange for numeric tracks
            if file_type in ['bigwig', 'wig', 'bedgraph', 'tdf']:
                data_range_elem = ET.SubElement(track_elem, 'DataRange')
                data_range_elem.set('minimum', '0')
                data_range_elem.set('maximum', str(scale_max))
                data_range_elem.set('type', 'LINEAR')
        
        # Write XML to file
        self._write_xml(root, output_path)
        
        print(f"Generated multi-track file: {output_path}")
        print(f"  - {len(valid_files)} tracks")
        print(f"  - Individual track height: {track_height}px")
        print(f"  - Renderer: {renderer}")
        print(f"  - Color: {color}")
    
    def _write_xml(self, root: ET.Element, output_path: str) -> None:
        """Write XML to file with proper formatting."""
        # Add indentation for pretty printing
        self._indent_xml(root)

        # Create the tree
        tree = ET.ElementTree(root)

        # Add XML declaration and write
        with open(output_path, 'wb') as f:
            f.write(b'<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n')
            tree.write(f, encoding='utf-8', xml_declaration=False)

    def _indent_xml(self, elem: ET.Element, level: int = 0) -> None:
        """Add indentation to XML elements for pretty printing."""
        indent = "\n" + level * "    "
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = indent + "    "
            if not elem.tail or not elem.tail.strip():
                elem.tail = indent
            for child in elem:
                self._indent_xml(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = indent
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = indent


def main():
    """Main function with argument parsing."""
    parser = argparse.ArgumentParser(
        description='Generate IGV multi-track (.mtrack) files from genomic data files',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Generate from wildcard pattern
  python igv-mtrack.py -o footprint.mtrack -t "/data/fragments_*.bw" --total-height 60

  # Generate from multiple files with custom settings
  python igv-mtrack.py -o analysis.mtrack -t file1.bw file2.bw file3.bw \\
    --renderer BAR_CHART --color "255,0,0" --scale-max 50

  # Use relative paths for portable files
  python igv-mtrack.py -o tracks.mtrack -t "data/*.bigwig" --relative-paths

Supported file formats:
  .bw, .bigwig, .wig, .bam, .bed, .bedgraph, .tdf, .vcf, .gff, .gtf, .gff3
        """)
    
    # Required arguments
    parser.add_argument('-o', '--output', required=True,
                       help='Output .mtrack file path')
    parser.add_argument('-t', '--tracks', required=True, nargs='+',
                       help='Input track files (supports wildcards)')
    
    # Optional arguments
    parser.add_argument('-r', '--renderer', default='HEATMAP',
                       choices=IGVMultiTrackGenerator.VALID_RENDERERS,
                       help='Track visualization renderer (default: HEATMAP)')
    parser.add_argument('--total-height', type=int, default=40,
                       help='Total combined height for all tracks in pixels (default: 40)')
    parser.add_argument('--relative-paths', action='store_true',
                       help='Use relative paths instead of absolute paths')
    parser.add_argument('--color', default='139,0,0',
                       help='RGB color for tracks (default: 139,0,0 for dark red)')
    parser.add_argument('--scale-max', type=int, default=100,
                       help='Maximum scale value for numeric tracks (default: 100)')
    
    args = parser.parse_args()
    
    # Validate arguments
    if args.total_height < 1:
        parser.error("--total-height must be at least 1")
    
    # Validate color format
    try:
        color_parts = args.color.split(',')
        if len(color_parts) != 3:
            raise ValueError()
        for part in color_parts:
            val = int(part.strip())
            if not 0 <= val <= 255:
                raise ValueError()
    except ValueError:
        parser.error("--color must be in format 'R,G,B' with values 0-255")
    
    if args.scale_max < 1:
        parser.error("--scale-max must be at least 1")
    
    # Generate the multi-track file
    try:
        generator = IGVMultiTrackGenerator()
        generator.generate_mtrack(
            output_path=args.output,
            track_patterns=args.tracks,
            renderer=args.renderer,
            total_height=args.total_height,
            use_relative_paths=args.relative_paths,
            color=args.color,
            scale_max=args.scale_max
        )
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
