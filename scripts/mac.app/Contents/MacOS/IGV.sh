#!/bin/bash

# Get the directory of this script (Contents/MacOS)
DIR="$(cd "$(dirname "$0")" && pwd)"
CONTENTS="$(dirname "$DIR")"
BUNDLED_GENOMES_DIR="${CONTENTS}/Resources/genomes"
USER_GENOMES_DIR="${HOME}/igv/genomes"

install_bundled_genomes() {
    if [ ! -d "${BUNDLED_GENOMES_DIR}" ]; then
        return
    fi

    mkdir -p "${USER_GENOMES_DIR}"

    for genome_file in "${BUNDLED_GENOMES_DIR}"/*.json; do
        [ -e "${genome_file}" ] || continue

        target="${USER_GENOMES_DIR}/$(basename "${genome_file}")"
        if [ ! -e "${target}" ]; then
            cp "${genome_file}" "${target}"
        fi
    done
}

install_bundled_genomes

# Use bundled JDK if available
if [ -d "${CONTENTS}/jdk-21" ]; then
    export JAVA_HOME="${CONTENTS}/jdk-21"
    export PATH="${JAVA_HOME}/bin:${PATH}"
else
    echo "Using system JDK. IGV requires Java 21." >&2
fi

JAVA_DIR="${CONTENTS}/Java"
CP="${JAVA_DIR}/lib/*"

# Check for user-specified Java arguments
JAVA_ARGS=""
if [ -e "$HOME/.igv/java_arguments" ]; then
    JAVA_ARGS="@$HOME/.igv/java_arguments"
fi

exec java -Xmx8g \
    @"${JAVA_DIR}/igv.args" \
    -Xdock:name="IGV" \
    -Xdock:icon="${CONTENTS}/Resources/IGV_64.png" \
    -Dapple.laf.useScreenMenuBar=true \
    -Djava.net.preferIPv4Stack=true \
    -Djava.net.useSystemProxies=true \
    ${JAVA_ARGS} \
    -cp "$CP" \
    org.igv.ui.Main "$@"
