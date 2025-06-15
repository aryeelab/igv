# IGV Multi-Track File Generator

A Python script for generating IGV multi-track (.mtrack) files from collections of genomic data files. This tool automates the creation of multi-track configuration files compatible with IGV's multi-track loading feature.

## Features

- **Wildcard Support**: Use glob patterns to select multiple files
- **Automatic Format Detection**: Supports all major genomic file formats
- **Flexible Track Configuration**: Customizable heights, colors, and renderers
- **Path Management**: Support for both absolute and relative paths
- **Error Handling**: Comprehensive validation and user-friendly error messages
- **XML Formatting**: Generates properly formatted, human-readable XML

## Installation

No installation required! Just ensure you have Python 3.6+ available:

```bash
# Make the script executable
chmod +x igv-mtrack.py

# Test the installation
python3 igv-mtrack.py --help
```

## Quick Start

### Basic Usage

```bash
# Generate from wildcard pattern
python3 igv-mtrack.py -o my_tracks.mtrack -t "/path/to/data/*.bw"

# Generate from specific files
python3 igv-mtrack.py -o analysis.mtrack -t file1.bw file2.bigwig file3.wig
```

### Common Use Cases

#### Footprint Analysis
```bash
python3 igv-mtrack.py \
  -o footprint_analysis.mtrack \
  -t "/data/fragments_*.bw" \
  --total-height 60 \
  --renderer HEATMAP \
  --relative-paths
```

#### ChIP-seq Comparison
```bash
python3 igv-mtrack.py \
  -o chipseq_comparison.mtrack \
  -t "/data/H3K4me3_*.bigwig" "/data/H3K27ac_*.bigwig" \
  --renderer BAR_CHART \
  --color "0,150,0" \
  --scale-max 200
```

#### RNA-seq Time Series
```bash
python3 igv-mtrack.py \
  -o timeseries.mtrack \
  -t "timepoint_*.bw" \
  --total-height 300 \
  --renderer LINE_PLOT \
  --color "255,0,0"
```

## Command Line Options

### Required Arguments

| Option | Description | Example |
|--------|-------------|---------|
| `-o, --output` | Output .mtrack file path | `-o my_tracks.mtrack` |
| `-t, --tracks` | Input track files (supports wildcards) | `-t "*.bw" file.wig` |

### Optional Arguments

| Option | Default | Description | Example |
|--------|---------|-------------|---------|
| `-r, --renderer` | `HEATMAP` | Track visualization mode | `--renderer BAR_CHART` |
| `--total-height` | `40` | Total height for all tracks (pixels) | `--total-height 120` |
| `--relative-paths` | `False` | Use relative instead of absolute paths | `--relative-paths` |
| `--color` | `139,0,0` | RGB color for all tracks | `--color "255,0,0"` |
| `--scale-max` | `100` | Maximum scale value for numeric tracks | `--scale-max 50` |

### Renderer Options

- **`HEATMAP`**: Compact heatmap visualization (ideal for many tracks)
- **`BAR_CHART`**: Traditional bar chart display
- **`POINTS`**: Point-based visualization
- **`LINE_PLOT`**: Line graph display

## Supported File Formats

| Extension | Type | Description |
|-----------|------|-------------|
| `.bw`, `.bigwig` | BigWig | Signal tracks (recommended for large datasets) |
| `.wig` | Wiggle | Signal tracks |
| `.bam` | BAM | Alignment files (auto-detects .bai index) |
| `.bed` | BED | Feature annotations |
| `.bedgraph` | BedGraph | Signal tracks |
| `.tdf` | TDF | IGV tile format |
| `.vcf` | VCF | Variant calls |
| `.gff`, `.gtf`, `.gff3` | GFF/GTF | Gene annotations |

**Note**: Compressed files (`.gz`) are automatically detected.

## Advanced Features

### Automatic Track Height Calculation

The script automatically calculates individual track heights based on the total height:

```bash
# 10 tracks with total height 100 = 10px per track
python3 igv-mtrack.py -o tracks.mtrack -t "*.bw" --total-height 100

# Minimum height is 1px (with warning for very small heights)
```

### Smart Track Naming

Track names are automatically extracted from filenames:

- **Input**: `/path/to/sample_H3K4me3_rep1.sorted.bw`
- **Output**: `sample_H3K4me3_rep1`

Common suffixes like `.sorted`, `.filtered`, `.processed` are automatically removed.

### BAM Index Detection

For BAM files, the script automatically includes index files if present:

```bash
# If sample.bam.bai exists, it will be included in the XML
python3 igv-mtrack.py -o bam_tracks.mtrack -t "*.bam"
```

### Relative Path Support

Use `--relative-paths` for portable multi-track files:

```bash
# Creates paths relative to the output file location
python3 igv-mtrack.py -o data/tracks.mtrack -t "data/*.bw" --relative-paths
```

## Output Format

The script generates XML files compatible with IGV's MultiTrack format:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<MultiTrack version="1">
    <Resources>
        <Resource name="sample1" path="data/sample1.bw" type="bigwig" />
        <Resource name="sample2" path="data/sample2.bw" type="bigwig" />
    </Resources>
    <Tracks>
        <Track name="sample1" resourceId="data/sample1.bw" 
               visible="true" height="20" color="139,0,0" 
               autoScale="true" renderer="HEATMAP">
            <DataRange minimum="0" maximum="100" type="LINEAR" />
        </Track>
        <Track name="sample2" resourceId="data/sample2.bw" 
               visible="true" height="20" color="139,0,0" 
               autoScale="true" renderer="HEATMAP">
            <DataRange minimum="0" maximum="100" type="LINEAR" />
        </Track>
    </Tracks>
</MultiTrack>
```

## Error Handling

The script provides comprehensive error handling:

### File Validation
- **Missing files**: Warns and skips non-existent files
- **Unsupported formats**: Warns and skips unknown file types
- **Empty results**: Errors if no valid files are found

### Parameter Validation
- **Color format**: Must be "R,G,B" with values 0-255
- **Height limits**: Must be positive integers
- **Scale values**: Must be positive

### Example Error Messages
```bash
Warning: File not found: missing_file.bw
Warning: Unsupported file format: data.txt
Warning: No files found matching pattern: *.xyz
Error: --color must be in format 'R,G,B' with values 0-255
```

## Integration with IGV

### Loading Generated Files

1. **Start IGV** and load your genome of interest
2. **Open multi-track file**: `Tracks → Load Multi-Track File...`
3. **Select your .mtrack file** generated by this script
4. **Tracks load automatically** with all specified configurations

### Best Practices

- **Use BigWig format** for large signal datasets (faster loading)
- **Include index files** for BAM files (place .bai files alongside .bam)
- **Test file accessibility** before sharing (especially for relative paths)
- **Use meaningful filenames** for automatic track naming

## Troubleshooting

### Common Issues

1. **"No valid input files found"**
   - Check file paths and wildcard patterns
   - Verify files exist and have supported extensions

2. **"Individual track height is very small"**
   - Increase `--total-height` or reduce number of tracks
   - Minimum recommended height is 3px per track

3. **Relative paths not working**
   - Ensure output directory exists
   - Check that input files are accessible from output location

### Performance Tips

- **Use indexed formats** (BigWig, BAM) for large datasets
- **Limit total tracks** for better IGV performance
- **Consider data ranges** when setting `--scale-max`

## Examples Repository

See the IGV repository for additional examples:
- `test/sessions/chipseq_example.mtrack` - ChIP-seq analysis example
- `MULTI_TRACK_FEATURE.md` - Complete feature documentation

## License

This script is part of the IGV project and follows the same licensing terms.

---

*For more information about IGV multi-track files, see the [Multi-Track Feature Documentation](MULTI_TRACK_FEATURE.md)*
