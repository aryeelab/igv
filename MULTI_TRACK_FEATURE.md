# IGV Multi-Track Loading Feature

## Overview

The IGV Multi-Track Loading feature allows users to load multiple tracks simultaneously from a single XML configuration file. This feature is particularly useful for:

- **Comparative analysis** across multiple related datasets
- **Batch loading** of tracks with consistent configurations
- **Reproducible workflows** with standardized track settings
- **Footprint analysis** with multiple fragment length ranges
- **Time series experiments** with multiple time points

## Quick Start

### Loading Multi-Track Files

1. **Open IGV** and load your genome of interest
2. **Navigate to Tracks Menu**: `Tracks → Load Multi-Track File...`
3. **Select your .mtrack file** from the file dialog
4. **Tracks load automatically** with all specified configurations

### Supported File Extensions

- **Primary**: `.mtrack`
- **Alternative**: `.igv-tracks`

## File Format Specification

### Basic Structure

Multi-track files use XML format with the following structure:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<MultiTrack version="1">
    <Resources>
        <!-- Define data files -->
    </Resources>
    <Tracks>
        <!-- Configure track display -->
    </Tracks>
</MultiTrack>
```

### Complete Example

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<MultiTrack version="1">
    <Resources>
        <Resource name="Sample 1" path="/path/to/sample1.bam" type="bam" index="/path/to/sample1.bam.bai"/>
        <Resource name="Sample 2" path="/path/to/sample2.wig" type="wig"/>
        <Resource name="Remote Data" path="https://example.com/data.bigwig" type="bigwig"/>
    </Resources>
    <Tracks>
        <Track name="Alignments" resourceId="/path/to/sample1.bam" visible="true" height="100" color="0,0,255">
            <DataRange minimum="0" maximum="500" type="LINEAR"/>
        </Track>
        <Track name="Signal" resourceId="/path/to/sample2.wig" visible="true" height="50" color="255,0,0" renderer="HEATMAP"/>
        <Track name="Remote Signal" resourceId="https://example.com/data.bigwig" visible="true" height="30" autoScale="true"/>
    </Tracks>
</MultiTrack>
```

## XML Elements Reference

### Root Element

- **`<MultiTrack>`**: Root element with required `version` attribute

### Resources Section

- **`<Resources>`**: Container for all data file definitions
- **`<Resource>`**: Individual data file specification

#### Resource Attributes

| Attribute | Required | Description | Example |
|-----------|----------|-------------|---------|
| `name` | Yes | Display name for the resource | `"Sample 1"` |
| `path` | Yes | File path or URL | `"/path/to/file.bam"` |
| `type` | Yes | File format type | `"bam"`, `"wig"`, `"bigwig"`, `"bed"` |
| `index` | No | Index file path (for BAM/CRAM) | `"/path/to/file.bam.bai"` |
| `coverage` | No | Coverage file path | `"/path/to/coverage.tdf"` |
| `mapping` | No | Mapping file path | `"/path/to/mapping.txt"` |
| `trackLine` | No | UCSC track line parameters | `"color=255,0,0"` |
| `description` | No | Resource description | `"ChIP-seq data"` |

### Tracks Section

- **`<Tracks>`**: Container for all track configurations
- **`<Track>`**: Individual track display settings

#### Track Attributes

| Attribute | Required | Description | Example |
|-----------|----------|-------------|---------|
| `name` | Yes | Track display name | `"My Track"` |
| `resourceId` | Yes | Path matching a Resource | `"/path/to/file.bam"` |
| `visible` | No | Track visibility | `"true"` (default) |
| `height` | No | Track height in pixels | `"50"` |
| `color` | No | Track color (RGB) | `"255,0,0"` |
| `altColor` | No | Alternative color | `"0,255,0"` |
| `autoScale` | No | Auto-scale data range | `"true"` |
| `fontSize` | No | Font size for labels | `"12"` |
| `renderer` | No | Rendering mode | `"HEATMAP"` |
| `sampleId` | No | Sample identifier | `"Sample_001"` |

#### Data Range Configuration

```xml
<Track name="My Track" resourceId="/path/to/file.wig">
    <DataRange minimum="0" maximum="100" baseline="0" type="LINEAR"/>
</Track>
```

**DataRange Attributes:**
- `minimum`: Minimum value for display range
- `maximum`: Maximum value for display range  
- `baseline`: Baseline value (default: minimum)
- `type`: Scale type (`"LINEAR"` or `"LOG"`)

## Supported File Types

### Genomic Data Formats

- **BAM/SAM**: Alignment files (`.bam`, `.sam`)
- **BigWig**: Signal tracks (`.bw`, `.bigwig`)
- **Wig**: Wiggle format (`.wig`)
- **BED**: Feature annotations (`.bed`)
- **GFF/GTF**: Gene annotations (`.gff`, `.gtf`)
- **VCF**: Variant calls (`.vcf`)
- **TDF**: IGV tile format (`.tdf`)

### Remote Resources

- **HTTP/HTTPS URLs**: Direct file access
- **FTP URLs**: File transfer protocol
- **Cloud Storage**: S3, Google Cloud, etc.

## Advanced Features

### Relative Paths

Use relative paths for portable multi-track files:

```xml
<Resource name="Local Data" path="./data/sample.bam" type="bam"/>
```

### Heatmap Visualization

Configure tracks for heatmap display:

```xml
<Track name="Heatmap Track" resourceId="/path/to/data.bw" 
       height="3" renderer="HEATMAP" color="139,0,0"/>
```

### Auto-scaling

Enable automatic data range scaling:

```xml
<Track name="Auto-scaled" resourceId="/path/to/data.wig" autoScale="true"/>
```

## Use Cases

### ChIP-seq Analysis

Example comparing histone modifications in A549 cells (see `test/sessions/chipseq_example.mtrack`):

```xml
<MultiTrack version="1">
    <Resources>
        <Resource name="A549 H3K4me3" path="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k04me3Etoh02Sig.bigWig" type="bigwig"/>
        <Resource name="A549 H3K27ac" path="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k27acEtoh02Sig.bigWig" type="bigwig"/>
        <Resource name="A549 H3K27me3" path="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k27me3Etoh02Sig.bigWig" type="bigwig"/>
    </Resources>
    <Tracks>
        <Track name="H3K4me3 (Promoters)" resourceId="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k04me3Etoh02Sig.bigWig" height="50" color="0,150,0"/>
        <Track name="H3K27ac (Active Enhancers)" resourceId="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k27acEtoh02Sig.bigWig" height="50" color="255,165,0"/>
        <Track name="H3K27me3 (Repressed)" resourceId="https://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeBroadHistone/wgEncodeBroadHistoneA549H3k27me3Etoh02Sig.bigWig" height="50" color="255,0,0"/>
    </Tracks>
</MultiTrack>
```

### Footprint Analysis

Perfect for MicroC or ATAC-seq footprint analysis with multiple fragment lengths:

```xml
<MultiTrack version="1">
    <Resources>
        <Resource name="25-28bp" path="./fragments_25-28bp.bw" type="bigwig"/>
        <Resource name="40-43bp" path="./fragments_40-43bp.bw" type="bigwig"/>
        <Resource name="70-73bp" path="./fragments_70-73bp.bw" type="bigwig"/>
    </Resources>
    <Tracks>
        <Track name="Short Fragments" resourceId="./fragments_25-28bp.bw"
               height="3" renderer="HEATMAP" color="139,0,0"/>
        <Track name="Medium Fragments" resourceId="./fragments_40-43bp.bw"
               height="3" renderer="HEATMAP" color="139,0,0"/>
        <Track name="Long Fragments" resourceId="./fragments_70-73bp.bw"
               height="3" renderer="HEATMAP" color="139,0,0"/>
    </Tracks>
</MultiTrack>
```

### Time Series Analysis

Load multiple time points with consistent settings:

```xml
<MultiTrack version="1">
    <Resources>
        <Resource name="0hr" path="./timepoint_0hr.bw" type="bigwig"/>
        <Resource name="2hr" path="./timepoint_2hr.bw" type="bigwig"/>
        <Resource name="6hr" path="./timepoint_6hr.bw" type="bigwig"/>
        <Resource name="24hr" path="./timepoint_24hr.bw" type="bigwig"/>
    </Resources>
    <Tracks>
        <Track name="0 hours" resourceId="./timepoint_0hr.bw" height="50" color="0,0,255"/>
        <Track name="2 hours" resourceId="./timepoint_2hr.bw" height="50" color="0,128,255"/>
        <Track name="6 hours" resourceId="./timepoint_6hr.bw" height="50" color="255,128,0"/>
        <Track name="24 hours" resourceId="./timepoint_24hr.bw" height="50" color="255,0,0"/>
    </Tracks>
</MultiTrack>
```

## Error Handling

### Graceful Degradation

- **Missing files**: Tracks are skipped with warning messages
- **Invalid formats**: Unsupported files are ignored
- **Network issues**: Remote files timeout gracefully
- **Partial loading**: Valid tracks load even if some fail

### Common Issues

1. **File not found**: Check file paths and permissions
2. **Unsupported format**: Verify file type matches extension
3. **Network timeout**: Check internet connection for remote files
4. **Invalid XML**: Validate XML syntax and structure

## Best Practices

### File Organization

- **Use relative paths** for portable configurations
- **Group related files** in the same directory
- **Include index files** for BAM/CRAM files
- **Test file accessibility** before sharing

### Track Configuration

- **Use consistent heights** for comparable tracks
- **Choose distinct colors** for easy identification
- **Enable auto-scaling** for unknown data ranges
- **Set appropriate baselines** for meaningful comparisons

### Performance Optimization

- **Use indexed formats** (BigWig, BAM) for large datasets
- **Limit track heights** for many simultaneous tracks
- **Consider data ranges** to avoid excessive memory usage
- **Use remote files** judiciously to avoid network bottlenecks

## Technical Implementation

### Architecture

- **MultiTrackFormat**: Constants and format utilities
- **MultiTrackReader**: XML parsing and track loading
- **MultiTrackLoader**: IGV integration and session management
- **MultiTrackWriter**: Export functionality (future feature)

### Integration Points

- **Tracks Menu**: "Load Multi-Track File..." menu item
- **File Dialog**: Supports .mtrack and .igv-tracks extensions
- **Track Management**: Uses standard IGV track loading infrastructure
- **Session Compatibility**: Integrates with existing IGV sessions

## Example Files

### ChIP-seq Example

IGV includes a complete example multi-track file demonstrating ChIP-seq analysis:

**Location**: `test/sessions/chipseq_example.mtrack`

**Contents**:
- A549 cell line histone modifications
- 6 different ChIP-seq tracks (H3K4me3, H3K4me1, H3K27ac, H3K27me3, H3K36me3, CTCF)
- Biologically meaningful color coding
- Appropriate data ranges for each mark
- Uses publicly available ENCODE data

**To test**:
1. Load hg19 genome in IGV
2. Load the example file: `Tracks → Load Multi-Track File...`
3. Navigate to chr11:62,671,495-62,700,195 for good signal
4. Observe distinct patterns for each histone modification

## Version History

- **Version 1.0**: Initial implementation with core functionality
  - XML-based configuration format
  - Support for all major genomic file types
  - Heatmap rendering support
  - Error handling and graceful degradation

## Future Enhancements

- **Track grouping**: Organize related tracks into collapsible groups
- **Conditional loading**: Load tracks based on genome or other criteria
- **Template system**: Predefined templates for common use cases
- **Export functionality**: Save current tracks as multi-track files
- **Workspace integration**: Integration with IGV workspace features

---

*For technical support or feature requests, please visit the [IGV GitHub repository](https://github.com/igvteam/igv).*
