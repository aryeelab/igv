# IGV Conda Environment Setup

This document describes the conda environment setup for building and running IGV (Integrative Genomics Viewer).

## Environment Details

The `igv` conda environment includes all the necessary dependencies for building and running IGV:

- **OpenJDK 21** - Required Java version for IGV
- **Gradle 8.11.1** - Build system
- **Git 2.49.0** - Version control
- **Maven 3.9.10** - Additional build tool support

## Quick Start

### Activate the Environment
```bash
conda activate igv
```

### Build IGV
```bash
# Clean build
./gradlew clean build

# Create distribution
./gradlew createDist

# Run tests
./gradlew test
```

### Run IGV
After building, you can run IGV using the launcher scripts in `build/IGV-dist/`:
```bash
# On macOS/Linux
./build/IGV-dist/igv.sh

# On macOS (HiDPI)
./build/IGV-dist/igv_hidpi.sh

# On Windows
./build/IGV-dist/igv.bat
```

## Environment Recreation

If you need to recreate this environment from scratch:

### Option 1: Using the environment.yml file
```bash
conda env create -f environment.yml
```

### Option 2: Manual creation
```bash
conda create -n igv openjdk=21 gradle git maven -c conda-forge -y
```

## Verification

To verify the environment is working correctly:

1. **Check Java version:**
   ```bash
   conda activate igv
   java -version
   # Should show: openjdk version "21.0.6"
   ```

2. **Check Gradle version:**
   ```bash
   gradle --version
   # Should show: Gradle 8.11.1
   ```

3. **Build IGV:**
   ```bash
   ./gradlew clean build
   # Should complete successfully
   ```

## Environment Management

### Deactivate the environment
```bash
conda deactivate
```

### Remove the environment (if needed)
```bash
conda env remove -n igv
```

### List all environments
```bash
conda env list
```

## Troubleshooting

### If build fails with Java version issues:
- Ensure you're in the `igv` environment: `conda activate igv`
- Check Java version: `java -version`
- Verify JAVA_HOME is set correctly

### If Gradle daemon issues occur:
```bash
./gradlew --stop
./gradlew clean build
```

### If dependencies are missing:
```bash
conda install -c conda-forge <missing-package>
```

## Additional Notes

- The environment uses OpenJDK 21 as required by IGV
- All tools are installed from conda-forge for consistency
- The environment is isolated and won't affect your system Java installation
- You can have multiple conda environments for different projects

## IGV-Specific Build Targets

Common Gradle tasks for IGV development:

```bash
# Basic build
./gradlew build

# Create distribution
./gradlew createDist

# Create platform-specific distributions
./gradlew createLinuxDistZip
./gradlew createMacDistZip
./gradlew createWinDist

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

For more detailed build instructions, see the main IGV README.md file.
