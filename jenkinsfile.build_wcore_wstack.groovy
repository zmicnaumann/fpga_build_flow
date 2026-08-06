/*
 * Jenkinsfile
 *
 * Whole-flow structure:
 *
 *   1. Read Jenkins parameters
 *   2. Create one normalized configuration map
 *   3. Build or download the left DCP
 *   4. Build or download the selected right DCP
 *   5. Run the integrated whole-flow build
 */


/*
 * Declared outside pipeline {} because these values are shared
 * across multiple stages.
 */
def config = [:]
def resolvedDcps = [:]


pipeline {
    agent {
        label 'fpga-linux'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        /*
         * General build parameters
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
            description: 'Image configuration code'
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
            description: 'Build or download the left DCP'
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
            description: 'Right-side design to use'
        )

        choice(
            name: 'RIGHT_DCP_SOURCE',
            choices: [
                'BUILD',
                'URL'
            ],
            description: 'Build or download the selected right DCP'
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
            description: 'Run the integrated top-level build'
        )
    }

    environment {
        DCP_DIR = "${WORKSPACE}/dcps"
        OUTPUT_DIR = "${WORKSPACE}/output"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    /*
                     * Variant-specific information lives here.
                     *
                     * When rightv3 is added:
                     *   1. Add it to RIGHT_DCP_VARIANT above.
                     *   2. Add its definition here.
                     */
                    Map rightVariants = [
                        rightv1: [
                            topModule: 'right_v1',
                            sourceDir: 'fpga/right/rightv1'
                        ],

                        rightv2: [
                            topModule: 'right_v2',
                            sourceDir: 'fpga/right/rightv2'
                        ]
                    ]

                    Map selectedRight =
                        rightVariants[params.RIGHT_DCP_VARIANT]

                    if (!selectedRight) {
                        error(
                            "Unsupported right DCP variant: " +
                            params.RIGHT_DCP_VARIANT
                        )
                    }

                    /*
                     * Convert Jenkins params into one normalized map.
                     *
                     * Lower stages and methods use config instead of
                     * repeatedly reading params.
                     */
                    config = [
                        projectName: params.PROJECT_NAME.trim(),
                        platform   : params.PLATFORM,
                        imageCode  : params.IMAGE_CODE.trim(),

                        runImplementation:
                            params.RUN_IMPLEMENTATION,

                        directories: [
                            workspace: env.WORKSPACE,
                            dcps     : env.DCP_DIR,
                            output   : env.OUTPUT_DIR
                        ],

                        left: [
                            name      : 'left',
                            variant   : 'left',
                            source    : params.LEFT_DCP_SOURCE,
                            url       : params.LEFT_DCP_URL.trim(),

                            topModule : 'left',
                            sourceDir : 'fpga/left',

                            outputFile:
                                "${env.DCP_DIR}/left.dcp"
                        ],

                        right: [
                            name      : params.RIGHT_DCP_VARIANT,
                            variant   : params.RIGHT_DCP_VARIANT,
                            source    : params.RIGHT_DCP_SOURCE,
                            url       : params.RIGHT_DCP_URL.trim(),

                            topModule : selectedRight.topModule,
                            sourceDir : selectedRight.sourceDir,

                            outputFile:
                                "${env.DCP_DIR}/" +
                                "${params.RIGHT_DCP_VARIANT}.dcp"
                        ]
                    ]

                    /*
                     * Basic validation
                     */

                    if (!config.projectName) {
                        error('PROJECT_NAME is required')
                    }

                    if (!config.imageCode) {
                        error('IMAGE_CODE is required')
                    }

                    if (
                        config.left.source == 'URL' &&
                        !config.left.url
                    ) {
                        error(
                            'LEFT_DCP_URL is required when ' +
                            'LEFT_DCP_SOURCE is URL'
                        )
                    }

                    if (
                        config.right.source == 'URL' &&
                        !config.right.url
                    ) {
                        error(
                            'RIGHT_DCP_URL is required when ' +
                            'RIGHT_DCP_SOURCE is URL'
                        )
                    }

                    /*
                     * Make the Jenkins build easy to identify.
                     */
                    currentBuild.displayName =
                        "#${env.BUILD_NUMBER} " +
                        "${config.platform} " +
                        "${config.right.variant}"

                    currentBuild.description =
                        "Left=${config.left.source}, " +
                        "Right=${config.right.variant}/" +
                        "${config.right.source}"

                    echo """
============================================================
Whole-Flow Configuration
============================================================

Project
  Name:             ${config.projectName}
  Platform:         ${config.platform}
  Image code:       ${config.imageCode}
  Run integration:  ${config.runImplementation}

Left DCP
  Source:           ${config.left.source}
  URL:              ${config.left.url ?: '(not used)'}
  Top module:       ${config.left.topModule}
  Source directory: ${config.left.sourceDir}
  Output file:      ${config.left.outputFile}

Right DCP
  Variant:          ${config.right.variant}
  Source:           ${config.right.source}
  URL:              ${config.right.url ?: '(not used)'}
  Top module:       ${config.right.topModule}
  Source directory: ${config.right.sourceDir}
  Output file:      ${config.right.outputFile}

============================================================
"""
                }
            }
        }

        stage('Prepare Workspace') {
            steps {
                sh(
                    label: 'Prepare build directories',
                    script: '''
                        set -euo pipefail

                        rm -rf "$DCP_DIR"
                        mkdir -p "$DCP_DIR"
                        mkdir -p "$OUTPUT_DIR"

                        echo "DCP directory:    $DCP_DIR"
                        echo "Output directory: $OUTPUT_DIR"
                    '''
                )
            }
        }

        stage('Resolve Left DCP') {
            steps {
                script {
                    resolvedDcps.left = resolveDcp(
                        config.left,
                        config
                    )

                    echo(
                        "Resolved left DCP: " +
                        resolvedDcps.left
                    )
                }
            }
        }

        stage('Resolve Right DCP') {
            steps {
                script {
                    resolvedDcps.right = resolveDcp(
                        config.right,
                        config
                    )

                    echo(
                        "Resolved right DCP: " +
                        resolvedDcps.right
                    )
                }
            }
        }

        stage('Whole-Flow Build') {
            when {
                expression {
                    return config.runImplementation
                }
            }

            steps {
                script {
                    if (!resolvedDcps.left) {
                        error('Left DCP was not resolved')
                    }

                    if (!resolvedDcps.right) {
                        error('Right DCP was not resolved')
                    }

                    echo 'Starting integrated whole-flow build'

                    withEnv([
                        "PROJECT_NAME=${config.projectName}",
                        "FPGA_PLATFORM=${config.platform}",
                        "IMAGE_CODE=${config.imageCode}",
                        "LEFT_DCP=${resolvedDcps.left}",
                        "RIGHT_DCP=${resolvedDcps.right}",
                        "BUILD_OUTPUT_DIR=${config.directories.output}"
                    ]) {
                        sh(
                            label: 'Run whole-flow build',
                            script: '''
                                set -euo pipefail

                                echo "Whole-flow inputs"
                                echo "  Project:   $PROJECT_NAME"
                                echo "  Platform:  $FPGA_PLATFORM"
                                echo "  Image:     $IMAGE_CODE"
                                echo "  Left DCP:  $LEFT_DCP"
                                echo "  Right DCP: $RIGHT_DCP"
                                echo "  Output:    $BUILD_OUTPUT_DIR"

                                mkdir -p "$BUILD_OUTPUT_DIR"

                                /*
                                 * TODO:
                                 * Replace this with the existing whole-flow
                                 * Make, shell, or Vivado Tcl command.
                                 *
                                 * Example:
                                 *
                                 * ./run_wholeflow.sh \
                                 *     --project "$PROJECT_NAME" \
                                 *     --platform "$FPGA_PLATFORM" \
                                 *     --image-code "$IMAGE_CODE" \
                                 *     --left-dcp "$LEFT_DCP" \
                                 *     --right-dcp "$RIGHT_DCP"
                                 */

                                echo "PLACEHOLDER: whole-flow build"
                            '''
                        )
                    }
                }
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

        always {
            archiveArtifacts(
                artifacts: '''
                    dcps/**/*.dcp,
                    output/**/*
                ''',
                allowEmptyArchive: true,
                fingerprint: true
            )
        }
    }
}


/*
 * Build or download one DCP.
 *
 * dcpConfig contains information specific to this DCP:
 *
 *   name
 *   variant
 *   source
 *   url
 *   topModule
 *   sourceDir
 *   outputFile
 *
 * wholeConfig contains information shared by the whole build:
 *
 *   projectName
 *   platform
 *   imageCode
 *   directories
 */
String resolveDcp(
    Map dcpConfig,
    Map wholeConfig
) {
    echo(
        "Resolving ${dcpConfig.name} DCP " +
        "using source ${dcpConfig.source}"
    )

    switch (dcpConfig.source) {
        case 'BUILD':
            withEnv([
                "DCP_NAME=${dcpConfig.name}",
                "DCP_TOP=${dcpConfig.topModule}",
                "DCP_SOURCE_DIR=${dcpConfig.sourceDir}",
                "DCP_OUTPUT=${dcpConfig.outputFile}",
                "PROJECT_NAME=${wholeConfig.projectName}",
                "FPGA_PLATFORM=${wholeConfig.platform}",
                "IMAGE_CODE=${wholeConfig.imageCode}"
            ]) {
                sh(
                    label: "Build ${dcpConfig.name} DCP",
                    script: '''
                        set -euo pipefail

                        echo "DCP build configuration"
                        echo "  Name:       $DCP_NAME"
                        echo "  Top:        $DCP_TOP"
                        echo "  Source dir: $DCP_SOURCE_DIR"
                        echo "  Output:     $DCP_OUTPUT"
                        echo "  Project:    $PROJECT_NAME"
                        echo "  Platform:   $FPGA_PLATFORM"
                        echo "  Image code: $IMAGE_CODE"

                        mkdir -p "$(dirname "$DCP_OUTPUT")"

                        /*
                         * TODO:
                         * Replace this placeholder with the existing
                         * build_dcp.tcl invocation.
                         *
                         * Example:
                         *
                         * vivado \
                         *     -mode batch \
                         *     -source fpga/tcl/build_dcp.tcl \
                         *     -tclargs \
                         *         -dcp_top "$DCP_TOP" \
                         *         -dcp_out "$DCP_OUTPUT"
                         */

                        echo "PLACEHOLDER: build $DCP_NAME"

                        /*
                         * Temporary nonempty file so the skeleton can run.
                         * Remove this when the real DCP build is connected.
                         */
                        printf 'placeholder DCP: %s\n' \
                            "$DCP_NAME" \
                            > "$DCP_OUTPUT"
                    '''
                )
            }

            break

        case 'URL':
            withEnv([
                "DCP_NAME=${dcpConfig.name}",
                "DCP_URL=${dcpConfig.url}",
                "DCP_OUTPUT=${dcpConfig.outputFile}"
            ]) {
                sh(
                    label: "Download ${dcpConfig.name} DCP",
                    script: '''
                        set -euo pipefail

                        mkdir -p "$(dirname "$DCP_OUTPUT")"

                        TEMP_FILE="${DCP_OUTPUT}.part"

                        rm -f "$TEMP_FILE"

                        echo "Downloading $DCP_NAME"
                        echo "  URL:    $DCP_URL"
                        echo "  Output: $DCP_OUTPUT"

                        curl \
                            --fail \
                            --location \
                            --retry 3 \
                            --retry-delay 5 \
                            --output "$TEMP_FILE" \
                            "$DCP_URL"

                        mv "$TEMP_FILE" "$DCP_OUTPUT"
                    '''
                )
            }

            break

        default:
            error(
                "Unsupported DCP source " +
                "'${dcpConfig.source}' for " +
                dcpConfig.name
            )
    }

    /*
     * Verify that BUILD or URL produced a usable local file.
     */
    if (!fileExists(dcpConfig.outputFile)) {
        error(
            "${dcpConfig.name} DCP was not created: " +
            dcpConfig.outputFile
        )
    }

    sh(
        label: "Verify ${dcpConfig.name} DCP",
        script: """
            set -euo pipefail

            test -s '${dcpConfig.outputFile}'

            ls -lh '${dcpConfig.outputFile}'
        """
    )

    /*
     * The caller receives the final local path, regardless of whether
     * the DCP was built or downloaded.
     */
    return dcpConfig.outputFile
}