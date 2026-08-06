/*
 * Jenkinsfile
 *
 * Whole FPGA flow:
 *
 *   1. Read Jenkins parameters
 *   2. Create and validate a normalized configuration
 *   3. Build or download the left DCP
 *   4. Build or download the selected right DCP
 *   5. Run the top-level integration build
 *   6. Archive build outputs
 */


/*
 * These variables are declared outside pipeline {} so that multiple stages
 * can access them.
 *
 * Only store simple, serializable data in these maps:
 * strings, booleans, lists, and maps.
 */
def buildConfig = [:]
def resolvedDcps = [:]


pipeline {
    agent {
        label 'fpga-linux'
    }

    options {
        timestamps()
        disableConcurrentBuilds()

        /*
         * Keep this many Jenkins build records.
         * Adjust as appropriate for your Jenkins installation.
         */
        buildDiscarder(
            logRotator(
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
            )
        )
    }

    parameters {
        /*
         * General whole-flow parameters
         */

        string(
            name: 'PROJECT_NAME',
            defaultValue: 'wholeflow',
            description: 'Top-level FPGA project name'
        )

        choice(
            name: 'PLATFORM',
            choices: [
                '450mp',
                'mpower'
            ],
            description: 'Target FPGA platform'
        )

        string(
            name: 'IMAGE_CODE',
            defaultValue: '01',
            description: 'Image or project configuration code'
        )

        string(
            name: 'VIVADO_VERSION',
            defaultValue: '2023.2',
            description: 'Vivado version used by build scripts'
        )


        /*
         * Left DCP parameters
         */

        choice(
            name: 'LEFT_DCP_SOURCE',
            choices: [
                'BUILD',
                'URL'
            ],
            description: 'Build the left DCP or download it'
        )

        string(
            name: 'LEFT_DCP_URL',
            defaultValue: '',
            description: 'Required when LEFT_DCP_SOURCE is URL'
        )


        /*
         * Right DCP parameters
         */

        choice(
            name: 'RIGHT_DCP_VARIANT',
            choices: [
                'rightv1',
                'rightv2'
            ],
            description: 'Right-side implementation to use'
        )

        choice(
            name: 'RIGHT_DCP_SOURCE',
            choices: [
                'BUILD',
                'URL'
            ],
            description: 'Build the selected right DCP or download it'
        )

        string(
            name: 'RIGHT_DCP_URL',
            defaultValue: '',
            description: 'Required when RIGHT_DCP_SOURCE is URL'
        )


        /*
         * Whole-flow behavior
         */

        booleanParam(
            name: 'RUN_IMPLEMENTATION',
            defaultValue: true,
            description: 'Run the integrated top-level FPGA build'
        )
    }

    environment {
        /*
         * Keep actual shell-visible directories in env.
         *
         * These can be used directly inside sh blocks:
         *
         *   "$DCP_DIR"
         *   "$OUTPUT_DIR"
         */
        DCP_DIR = "${WORKSPACE}/dcps"
        OUTPUT_DIR = "${WORKSPACE}/output"
        LOG_DIR = "${WORKSPACE}/logs"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    buildConfig = createBuildConfig(
                        params,
                        env
                    )

                    validateBuildConfig(buildConfig)
                    printBuildConfig(buildConfig)

                    currentBuild.displayName =
                        "#${env.BUILD_NUMBER} " +
                        "${buildConfig.platform} " +
                        "${buildConfig.right.variant}"

                    currentBuild.description =
                        "Left: ${buildConfig.left.source}, " +
                        "Right: ${buildConfig.right.variant}/" +
                        "${buildConfig.right.source}"
                }
            }
        }

        stage('Prepare Workspace') {
            steps {
                sh(
                    label: 'Create build directories',
                    script: '''
                        set -euo pipefail

                        mkdir -p "$DCP_DIR"
                        mkdir -p "$OUTPUT_DIR"
                        mkdir -p "$LOG_DIR"

                        echo "Workspace prepared:"
                        echo "  DCP directory:    $DCP_DIR"
                        echo "  Output directory: $OUTPUT_DIR"
                        echo "  Log directory:    $LOG_DIR"
                    '''
                )
            }
        }

        stage('Resolve Left DCP') {
            steps {
                script {
                    resolvedDcps.left = resolveDcp(
                        buildConfig.left,
                        buildConfig
                    )
                }
            }
        }

        stage('Resolve Right DCP') {
            steps {
                script {
                    resolvedDcps.right = resolveDcp(
                        buildConfig.right,
                        buildConfig
                    )
                }
            }
        }

        stage('Verify DCP Inputs') {
            steps {
                script {
                    verifyResolvedDcps(resolvedDcps)

                    echo 'Resolved DCP inputs:'
                    echo "  Left:  ${resolvedDcps.left}"
                    echo "  Right: ${resolvedDcps.right}"
                }
            }
        }

        stage('Whole-Flow Build') {
            when {
                expression {
                    return params.RUN_IMPLEMENTATION
                }
            }

            steps {
                script {
                    runWholeFlow(
                        buildConfig,
                        resolvedDcps
                    )
                }
            }
        }

        stage('Verify Outputs') {
            when {
                expression {
                    return params.RUN_IMPLEMENTATION
                }
            }

            steps {
                script {
                    verifyWholeFlowOutputs(buildConfig)
                }
            }
        }

        stage('Archive Outputs') {
            steps {
                /*
                 * allowEmptyArchive is useful while the skeleton is being
                 * developed. Consider setting it to false once output names
                 * and paths are finalized.
                 */
                archiveArtifacts(
                    artifacts: '''
                        dcps/**/*.dcp,
                        output/**/*,
                        logs/**/*
                    ''',
                    allowEmptyArchive: true,
                    fingerprint: true
                )
            }
        }
    }

    post {
        success {
            echo 'Whole-flow pipeline completed successfully'
        }

        failure {
            echo 'Whole-flow pipeline failed'
        }

        aborted {
            echo 'Whole-flow pipeline was aborted'
        }

        always {
            echo "Build result: ${currentBuild.currentResult}"
            echo "Build URL: ${env.BUILD_URL}"
        }
    }
}


/*
 * ============================================================================
 * Configuration
 * ============================================================================
 */

Map createBuildConfig(def pipelineParams, def pipelineEnv) {
    Map rightVariants = getRightVariantDefinitions()

    String selectedRightVariant =
        pipelineParams.RIGHT_DCP_VARIANT?.trim()

    Map selectedRight =
        rightVariants[selectedRightVariant]

    if (!selectedRight) {
        error(
            "Unsupported RIGHT_DCP_VARIANT: " +
            "'${selectedRightVariant}'"
        )
    }

    return [
        projectName: pipelineParams.PROJECT_NAME?.trim(),
        platform   : pipelineParams.PLATFORM?.trim(),
        imageCode  : pipelineParams.IMAGE_CODE?.trim(),

        vivadoVersion   : pipelineParams.VIVADO_VERSION?.trim(),
        runImplementation:
            pipelineParams.RUN_IMPLEMENTATION as boolean,

        directories: [
            workspace: pipelineEnv.WORKSPACE,
            dcps     : pipelineEnv.DCP_DIR,
            output   : pipelineEnv.OUTPUT_DIR,
            logs     : pipelineEnv.LOG_DIR
        ],

        left: [
            role      : 'left',
            name      : 'left',
            variant   : 'left',
            source    : pipelineParams.LEFT_DCP_SOURCE?.trim(),
            url       : pipelineParams.LEFT_DCP_URL?.trim(),
            outputFile: "${pipelineEnv.DCP_DIR}/left.dcp",

            build: [
                topModule: 'left',
                sourceDir: 'fpga/left',

                /*
                 * Replace this with the actual script path.
                 */
                buildScript: 'fpga/tcl/build_dcp.tcl'
            ]
        ],

        right: [
            role      : 'right',
            name      : selectedRightVariant,
            variant   : selectedRightVariant,
            source    : pipelineParams.RIGHT_DCP_SOURCE?.trim(),
            url       : pipelineParams.RIGHT_DCP_URL?.trim(),
            outputFile:
                "${pipelineEnv.DCP_DIR}/${selectedRightVariant}.dcp",

            build: [
                topModule : selectedRight.topModule,
                sourceDir : selectedRight.sourceDir,
                buildScript:
                    selectedRight.buildScript
            ]
        ]
    ]
}


Map getRightVariantDefinitions() {
    /*
     * This is the registry of supported right-side variants.
     *
     * When rightv3 is added, add one entry here and one value to the
     * RIGHT_DCP_VARIANT Jenkins choice parameter.
     */
    return [
        rightv1: [
            topModule : 'right_v1',
            sourceDir : 'fpga/right/rightv1',
            buildScript: 'fpga/tcl/build_dcp.tcl'
        ],

        rightv2: [
            topModule : 'right_v2',
            sourceDir : 'fpga/right/rightv2',
            buildScript: 'fpga/tcl/build_dcp.tcl'
        ]
    ]
}


/*
 * ============================================================================
 * Validation
 * ============================================================================
 */

void validateBuildConfig(Map config) {
    List<String> errors = []

    requireValue(
        errors,
        config.projectName,
        'PROJECT_NAME is required'
    )

    requireValue(
        errors,
        config.platform,
        'PLATFORM is required'
    )

    requireValue(
        errors,
        config.imageCode,
        'IMAGE_CODE is required'
    )

    requireValue(
        errors,
        config.vivadoVersion,
        'VIVADO_VERSION is required'
    )

    validateDcpConfig(
        config.left,
        errors
    )

    validateDcpConfig(
        config.right,
        errors
    )

    if (!errors.isEmpty()) {
        error(
            'Invalid whole-flow configuration:\n' +
            errors.collect { String message ->
                "  - ${message}"
            }.join('\n')
        )
    }
}


void validateDcpConfig(
    Map dcpConfig,
    List<String> errors
) {
    List<String> supportedSources = [
        'BUILD',
        'URL'
    ]

    String role = dcpConfig.role ?: 'unknown'
    String source = dcpConfig.source?.toUpperCase()

    requireValue(
        errors,
        dcpConfig.name,
        "${role} DCP name is required"
    )

    requireValue(
        errors,
        dcpConfig.outputFile,
        "${role} DCP output file is required"
    )

    if (!supportedSources.contains(source)) {
        errors << (
            "${role} DCP source must be one of: " +
            supportedSources.join(', ')
        )

        return
    }

    if (source == 'URL' && !dcpConfig.url) {
        errors << (
            "${role} DCP URL is required when " +
            "${role.toUpperCase()}_DCP_SOURCE is URL"
        )
    }

    if (source == 'BUILD') {
        if (!dcpConfig.build) {
            errors << "${role} DCP build configuration is missing"
            return
        }

        requireValue(
            errors,
            dcpConfig.build.topModule,
            "${role} DCP top module is required"
        )

        requireValue(
            errors,
            dcpConfig.build.sourceDir,
            "${role} DCP source directory is required"
        )

        requireValue(
            errors,
            dcpConfig.build.buildScript,
            "${role} DCP build script is required"
        )
    }
}


void requireValue(
    List<String> errors,
    def value,
    String message
) {
    if (value == null || value.toString().trim().isEmpty()) {
        errors << message
    }
}


/*
 * ============================================================================
 * DCP acquisition
 * ============================================================================
 */

String resolveDcp(
    Map dcpConfig,
    Map wholeConfig
) {
    String source = dcpConfig.source.toUpperCase()

    switch (source) {
        case 'BUILD':
            buildDcp(
                dcpConfig,
                wholeConfig
            )
            break

        case 'URL':
            downloadDcp(dcpConfig)
            break

        default:
            error(
                "Unsupported DCP source '${source}' " +
                "for ${dcpConfig.name}"
            )
    }

    verifyDcpFile(
        dcpConfig.name,
        dcpConfig.outputFile
    )

    return dcpConfig.outputFile
}


void buildDcp(
    Map dcpConfig,
    Map wholeConfig
) {
    echo(
        "Building ${dcpConfig.name} DCP " +
        "using top ${dcpConfig.build.topModule}"
    )

    /*
     * This environment boundary is useful because your shell/Tcl scripts
     * receive ordinary environment variables rather than Groovy expressions.
     */
    withEnv([
        "DCP_NAME=${dcpConfig.name}",
        "DCP_TOP=${dcpConfig.build.topModule}",
        "DCP_SOURCE_DIR=${dcpConfig.build.sourceDir}",
        "DCP_OUTPUT_FILE=${dcpConfig.outputFile}",
        "DCP_BUILD_SCRIPT=${dcpConfig.build.buildScript}",
        "FPGA_PLATFORM=${wholeConfig.platform}",
        "FPGA_IMAGE_CODE=${wholeConfig.imageCode}",
        "VIVADO_VERSION=${wholeConfig.vivadoVersion}"
    ]) {
        sh(
            label: "Build ${dcpConfig.name} DCP",
            script: '''
                set -euo pipefail

                echo "DCP build requested"
                echo "  Name:          $DCP_NAME"
                echo "  Top:           $DCP_TOP"
                echo "  Source dir:    $DCP_SOURCE_DIR"
                echo "  Output:        $DCP_OUTPUT_FILE"
                echo "  Platform:      $FPGA_PLATFORM"
                echo "  Image code:    $FPGA_IMAGE_CODE"
                echo "  Vivado:        $VIVADO_VERSION"
                echo "  Build script:  $DCP_BUILD_SCRIPT"

                mkdir -p "$(dirname "$DCP_OUTPUT_FILE")"

                /*
                 * TODO:
                 * Replace this placeholder with your existing DCP command.
                 *
                 * Example shape:
                 *
                 * vivado \
                 *     -mode batch \
                 *     -source "$DCP_BUILD_SCRIPT" \
                 *     -tclargs \
                 *         -dcp_top "$DCP_TOP" \
                 *         -dcp_out "$DCP_OUTPUT_FILE"
                 */

                echo "PLACEHOLDER: build $DCP_NAME"

                /*
                 * Remove this touch command when the real build command is
                 * enabled. It exists only so the skeleton can advance through
                 * the file-verification stages.
                 */
                touch "$DCP_OUTPUT_FILE"
            '''
        )
    }
}


void downloadDcp(Map dcpConfig) {
    echo(
        "Downloading ${dcpConfig.name} DCP " +
        "from ${dcpConfig.url}"
    )

    withEnv([
        "DCP_DOWNLOAD_URL=${dcpConfig.url}",
        "DCP_OUTPUT_FILE=${dcpConfig.outputFile}"
    ]) {
        sh(
            label: "Download ${dcpConfig.name} DCP",
            script: '''
                set -euo pipefail

                mkdir -p "$(dirname "$DCP_OUTPUT_FILE")"

                /*
                 * Download to a temporary file first. This prevents a failed
                 * or interrupted download from leaving a partial file at the
                 * final DCP path.
                 */
                TEMP_FILE="${DCP_OUTPUT_FILE}.part"

                rm -f "$TEMP_FILE"

                curl \
                    --fail \
                    --location \
                    --retry 3 \
                    --retry-delay 5 \
                    --output "$TEMP_FILE" \
                    "$DCP_DOWNLOAD_URL"

                mv "$TEMP_FILE" "$DCP_OUTPUT_FILE"

                echo "Downloaded DCP:"
                ls -lh "$DCP_OUTPUT_FILE"
            '''
        )
    }
}


void verifyDcpFile(
    String dcpName,
    String dcpPath
) {
    if (!fileExists(dcpPath)) {
        error(
            "DCP '${dcpName}' was not created or downloaded: " +
            dcpPath
        )
    }

    sh(
        label: "Inspect ${dcpName} DCP",
        script: """
            set -euo pipefail

            test -s '${dcpPath}' || {
                echo "DCP file is empty: ${dcpPath}"
                exit 1
            }

            ls -lh '${dcpPath}'
        """
    )
}


void verifyResolvedDcps(Map dcps) {
    if (!dcps.left) {
        error('The resolved left DCP path is missing')
    }

    if (!dcps.right) {
        error('The resolved right DCP path is missing')
    }

    verifyDcpFile(
        'left',
        dcps.left
    )

    verifyDcpFile(
        'right',
        dcps.right
    )
}


/*
 * ============================================================================
 * Whole-flow build
 * ============================================================================
 */

void runWholeFlow(
    Map config,
    Map dcps
) {
    echo 'Starting integrated whole-flow build'

    withEnv([
        "PROJECT_NAME=${config.projectName}",
        "FPGA_PLATFORM=${config.platform}",
        "FPGA_IMAGE_CODE=${config.imageCode}",
        "VIVADO_VERSION=${config.vivadoVersion}",
        "LEFT_DCP=${dcps.left}",
        "RIGHT_DCP=${dcps.right}",
        "WHOLEFLOW_OUTPUT_DIR=${config.directories.output}",
        "WHOLEFLOW_LOG_DIR=${config.directories.logs}"
    ]) {
        sh(
            label: 'Run integrated FPGA build',
            script: '''
                set -euo pipefail

                echo "Whole-flow build configuration"
                echo "  Project:       $PROJECT_NAME"
                echo "  Platform:      $FPGA_PLATFORM"
                echo "  Image code:    $FPGA_IMAGE_CODE"
                echo "  Vivado:        $VIVADO_VERSION"
                echo "  Left DCP:      $LEFT_DCP"
                echo "  Right DCP:     $RIGHT_DCP"
                echo "  Output dir:    $WHOLEFLOW_OUTPUT_DIR"

                mkdir -p "$WHOLEFLOW_OUTPUT_DIR"
                mkdir -p "$WHOLEFLOW_LOG_DIR"

                /*
                 * TODO:
                 * Replace this placeholder with the existing whole-flow
                 * shell, Make, or Vivado Tcl entry point.
                 *
                 * Example:
                 *
                 * ./run_wholeflow.sh \
                 *     --project "$PROJECT_NAME" \
                 *     --platform "$FPGA_PLATFORM" \
                 *     --image-code "$FPGA_IMAGE_CODE" \
                 *     --left-dcp "$LEFT_DCP" \
                 *     --right-dcp "$RIGHT_DCP" \
                 *     --output "$WHOLEFLOW_OUTPUT_DIR"
                 */

                echo "PLACEHOLDER: run whole-flow build"

                /*
                 * Temporary skeleton output.
                 * Remove when the real build is connected.
                 */
                printf '%s\n' \
                    "Project: $PROJECT_NAME" \
                    "Platform: $FPGA_PLATFORM" \
                    "Left DCP: $LEFT_DCP" \
                    "Right DCP: $RIGHT_DCP" \
                    > "$WHOLEFLOW_OUTPUT_DIR/build_summary.txt"
            '''
        )
    }
}


void verifyWholeFlowOutputs(Map config) {
    String summaryFile =
        "${config.directories.output}/build_summary.txt"

    /*
     * This currently verifies the temporary skeleton output.
     *
     * Later, change this to the actual required output:
     *
     *   .bit
     *   .bin
     *   .mcs
     *   top-level .dcp
     *   reports
     */
    if (!fileExists(summaryFile)) {
        error(
            "Expected whole-flow output was not found: " +
            summaryFile
        )
    }

    echo "Verified whole-flow output: ${summaryFile}"
}


/*
 * ============================================================================
 * Logging
 * ============================================================================
 */

void printBuildConfig(Map config) {
    echo """
===============================================================================
Whole-Flow Configuration
===============================================================================

Project
  Name:             ${config.projectName}
  Platform:         ${config.platform}
  Image code:       ${config.imageCode}
  Vivado version:   ${config.vivadoVersion}
  Run integration:  ${config.runImplementation}

Directories
  Workspace:        ${config.directories.workspace}
  DCP directory:    ${config.directories.dcps}
  Output directory: ${config.directories.output}
  Log directory:    ${config.directories.logs}

Left DCP
  Name:             ${config.left.name}
  Source:           ${config.left.source}
  URL:              ${config.left.url ?: '(not used)'}
  Output file:      ${config.left.outputFile}
  Top module:       ${config.left.build.topModule}
  Source directory: ${config.left.build.sourceDir}

Right DCP
  Name:             ${config.right.name}
  Variant:          ${config.right.variant}
  Source:           ${config.right.source}
  URL:              ${config.right.url ?: '(not used)'}
  Output file:      ${config.right.outputFile}
  Top module:       ${config.right.build.topModule}
  Source directory: ${config.right.build.sourceDir}

===============================================================================
"""
}