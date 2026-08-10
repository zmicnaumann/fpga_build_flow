def config = [:]
load 'FpgaBuildConfig.groovy'

def printConfig = {
    echo "=== Pipeline Config ==="
    echo "projectName       : ${config.projectName}"
    echo "platform          : ${config.platform}"
    echo "manifestUrl       : ${config.manifestUrl}"
    echo "runImplementation : ${config.runImplementation}"
    echo "workspace         : ${config.directories.workspace}"
    echo "dcps              : ${config.directories.dcps}"
    echo "output            : ${config.directories.output}"
    echo "buildRoot         : ${config.directories.buildRoot}"

    config.components.each { name, component ->
        echo "${name}.variant     : ${component.variant}"
        echo "${name}.manifest    : ${component.manifestBranch}"
        echo "${name}.image       : ${component.imageCode}"
        echo "${name}.source      : ${component.source}"
        echo "${name}.url         : ${component.url}"
        echo "${name}.encrypt     : ${component.encrypt}"
        echo "${name}.needXDC     : ${component.needXDC}"
        echo "${name}.pathXDC     : ${component.pathXDC}"
        echo "${name}.outputFile  : ${component.outputFile}"
    }

    echo "--- historical / not currently used ---"
    echo "alteraLicenseFile : ${config.historical.alteraLicenseFile}"
    echo "xilinxLicenseFile : ${config.historical.xilinxLicenseFile}"
    echo "shaType           : ${config.historical.shaType}"
    echo "devToolsBranch    : ${config.historical.devToolsBranch}"
    echo "======================="
}

def resolveVariantMetadata = { String kind, String variantName, String imageCode ->
    def catalog = [
        wcore: [
            wcore_450mp: [
                topModule    : "wasp_450mp_top",
                baseDir      : "450mp/05/",
                manifestFile : "mpower.xml",
                needXDC      : false,
                pathXDC      : ""
            ],
            wcore_450mp_difi: [
                topModule    : "wasp_450mp_difi_top",
                baseDir      : "450mp/07/",
                manifestFile : "mpower.xml",
                needXDC      : false,
                pathXDC      : ""
            ]
        ],
        wstack: [
            mpower: [
                topModule    : "wstack_mpower_top",
                baseDir      : "mpower/${imageCode}",
                manifestFile : "mpower.xml",
                needXDC      : true,
                pathXDC      : "./constr/mpower.xdc"
            ],
            wdk: [
                topModule    : "wstack_wdk_top",
                baseDir      : "wdk/${imageCode}",
                manifestFile : "wdk.xml",
                needXDC      : false,
                pathXDC      : ""
            ],
            ebem: [
                topModule    : "wstack_ebem_top",
                baseDir      : "ebem/${imageCode}",
                manifestFile : "ebem.xml",
                needXDC      : false,
                pathXDC      : ""
            ]
        ]
    ]

    def match = catalog[kind]?.get(variantName)
    if (!match) {
        error("Unsupported ${kind} variant: ${variantName}")
    }
    return match
}

def resolveDcp = { Map dcpConfig, Map wholeConfig ->
    echo "Resolving ${dcpConfig.name} DCP using source ${dcpConfig.source}"

    switch (dcpConfig.source) {
        case 'BUILD':
            withEnv([
                "DCP_NAME=${dcpConfig.name}",
                "DCP_TOP=${dcpConfig.topModule}",
                "DCP_SOURCE_DIR=${dcpConfig.baseDir}",
                "DCP_OUTPUT=${dcpConfig.outputFile}",
                "PROJECT_NAME=${wholeConfig.projectName}",
                "FPGA_PLATFORM=${wholeConfig.platform}",
                "IMAGE_CODE=${dcpConfig.imageCode}"
            ]) {
                sh '''
                    set -euo pipefail
                    mkdir -p "$(dirname "$DCP_OUTPUT")"
                    echo "DCP build configuration"
                    echo "  Name:       $DCP_NAME"
                    echo "  Top:        $DCP_TOP"
                    echo "  Source dir: $DCP_SOURCE_DIR"
                    echo "  Output:     $DCP_OUTPUT"
                    echo "  Project:    $PROJECT_NAME"
                    echo "  Platform:   $FPGA_PLATFORM"
                    echo "  Image code: $IMAGE_CODE"

                    # TODO: replace this placeholder with the actual build flow
                    printf 'placeholder DCP: %s\n' "$DCP_NAME" > "$DCP_OUTPUT"
                '''
            }
            break

        case 'URL':
            withEnv([
                "DCP_NAME=${dcpConfig.name}",
                "DCP_URL=${dcpConfig.url}",
                "DCP_OUTPUT=${dcpConfig.outputFile}"
            ]) {
                sh '''
                    set -euo pipefail
                    mkdir -p "$(dirname "$DCP_OUTPUT")"
                    TEMP_FILE="${DCP_OUTPUT}.part"
                    rm -f "$TEMP_FILE"

                    echo "Downloading $DCP_NAME"
                    echo "  URL:    $DCP_URL"
                    echo "  Output: $DCP_OUTPUT"

                    curl --fail --location --retry 3 --retry-delay 5 --output "$TEMP_FILE" "$DCP_URL"
                    mv "$TEMP_FILE" "$DCP_OUTPUT"
                '''
            }
            break

        default:
            error("Unsupported DCP source '${dcpConfig.source}' for ${dcpConfig.name}")
    }

    if (!fileExists(dcpConfig.outputFile)) {
        error("${dcpConfig.name} DCP was not created: ${dcpConfig.outputFile}")
    }

    sh """
        set -euo pipefail
        test -s '${dcpConfig.outputFile}'
        ls -lh '${dcpConfig.outputFile}'
    """

    return dcpConfig.outputFile
}

pipeline {
    agent {
        label 'fpga-linux'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(
            name         : 'PROJECT_NAME',
            defaultValue : 'projname',
            description  : 'Top-level FPGA project name'
        )

        choice(
            name         : 'PLATFORM',
            choices      : ['450mp'],
            description  : 'Target FPGA platform'
        )

        choice(
            name        : 'WCORE_VARIANT',
            choices     : ['wcore_450mp', 'wcore_450mp_difi'],
            description : 'WCore variant'
        )

        string(
            name         : 'WCORE_MANIFEST_BRANCH',
            defaultValue : 'main',
            description  : 'Manifest branch for wcore'
        )

        choice(
            name        : 'WCORE_IMAGE_CODE',
            defaultValue : '01',
            choices     : ['01', '02', '03', '04', '05', '06', '07'],
            description : 'Image code for wcore'
        )

        choice(
            name        : 'WCORE_DCP_SOURCE',
            choices     : ['BUILD', 'URL'],
            description : 'How to resolve wcore DCP'
        )

        string(
            name         : 'WCORE_DCP_URL',
            defaultValue : '',
            description  : 'Required when wcore source is URL'
        )

        choice(
            name        : 'WCORE_ENCRYPT',
            choices     : ['NO', 'YES'],
            description : 'Encrypt the wcore DCP'
        )

        string(
            name         : 'WCORE_ENCRYPTIONFILE_PATH',
            defaultValue : '',
            description  : 'Required when wcore encrypt is YES'
        )

        choice(
            name        : 'WSTACK_VARIANT',
            choices     : ['mpower', 'wdk', 'ebem'],
            description : 'WStack variant'
        )

        string(
            name         : 'WSTACK_MANIFEST_BRANCH',
            defaultValue : 'main',
            description  : 'Manifest branch for wstack'
        )

        choice(
            name        : 'WSTACK_IMAGE_CODE',
            defaultValue : '01',
            choices     : ['01', '02'],
            description : 'Image code for wstack'
        )

        choice(
            name        : 'WSTACK_DCP_SOURCE',
            choices     : ['BUILD', 'URL'],
            description : 'How to resolve wstack DCP'
        )

        string(
            name         : 'WSTACK_DCP_URL',
            defaultValue : '',
            description  : 'Required when wstack source is URL'
        )

        choice(
            name        : 'WSTACK_ENCRYPT',
            choices     : ['NO', 'YES'],
            description : 'Encrypt the wstack DCP'
        )

        string(
            name         : 'WSTACK_ENCRYPTIONFILE_PATH',
            defaultValue : '',
            description  : 'Required when wstack encrypt is YES'
        )

        booleanParam(
            name         : 'RUN_IMPLEMENTATION',
            defaultValue : true,
            description  : 'Run the integrated whole-flow build'
        )
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def selectedWcore = resolveVariantMetadata('wcore', params.WCORE_VARIANT, params.WCORE_IMAGE_CODE)
                    def selectedWstack = resolveVariantMetadata('wstack', params.WSTACK_VARIANT, params.WSTACK_IMAGE_CODE)

                    config = FpgaBuildConfig.fromMap([
                        projectName       : params.PROJECT_NAME.trim(),
                        platform          : params.PLATFORM,
                        manifestUrl       : FpgaBuildConfig.DEFAULT_MANIFEST_URL,
                        runImplementation : params.RUN_IMPLEMENTATION,
                        directories       : [
                            workspace : env.WORKSPACE,
                            dcps     : "${env.WORKSPACE}/dcps",
                            output   : "${env.WORKSPACE}/output"
                        ],
                        historical        : [
                            alteraLicenseFile : '',
                            xilinxLicenseFile : '',
                            shaType           : '',
                            devToolsBranch    : '',
                            note              : 'historical / not currently used by build flow'
                        ],
                        components: [
                            wcore: [
                                name               : 'wcore',
                                variant            : params.WCORE_VARIANT,
                                manifestBranch     : params.WCORE_MANIFEST_BRANCH.trim(),
                                imageCode          : params.WCORE_IMAGE_CODE,
                                source             : params.WCORE_DCP_SOURCE,
                                url                : params.WCORE_DCP_URL.trim(),
                                encrypt            : params.WCORE_ENCRYPT,
                                encryptionFilePath : params.WCORE_ENCRYPTIONFILE_PATH.trim(),
                                topModule          : selectedWcore.topModule,
                                baseDir            : selectedWcore.baseDir,
                                manifestFile       : selectedWcore.manifestFile,
                                needXDC            : selectedWcore.needXDC,
                                pathXDC            : selectedWcore.pathXDC,
                                outputFile         : "${env.WORKSPACE}/dcps/wcore.dcp"
                            ],
                            wstack: [
                                name               : 'wstack',
                                variant            : params.WSTACK_VARIANT,
                                manifestBranch     : params.WSTACK_MANIFEST_BRANCH.trim(),
                                imageCode          : params.WSTACK_IMAGE_CODE,
                                source             : params.WSTACK_DCP_SOURCE,
                                url                : params.WSTACK_DCP_URL.trim(),
                                encrypt            : params.WSTACK_ENCRYPT,
                                encryptionFilePath : params.WSTACK_ENCRYPTIONFILE_PATH.trim(),
                                topModule          : selectedWstack.topModule,
                                baseDir            : selectedWstack.baseDir,
                                manifestFile       : selectedWstack.manifestFile,
                                needXDC            : selectedWstack.needXDC,
                                pathXDC            : selectedWstack.pathXDC,
                                outputFile         : "${env.WORKSPACE}/dcps/wstack.dcp"
                            ]
                        ]
                    ])

                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${config.platform} ${config.projectName}"
                    currentBuild.description = "wcore=${config.components.wcore.variant}, wstack=${config.components.wstack.variant}"
                }
            }
        }

        stage('Prepare Workspace') {
            steps {
                sh label: 'Prepare build directories', script: '''
                    set -euo pipefail
                    mkdir -p "$WORKSPACE/dcps" "$WORKSPACE/output"
                    mkdir -p "$WORKSPACE/build/wcore_build" "$WORKSPACE/build/wstack_build" "$WORKSPACE/build/whole_build"
                    echo "DCP directory: $WORKSPACE/dcps"
                    echo "Output directory: $WORKSPACE/output"
                '''
            }
        }

        stage('Print Config') {
            steps {
                script {
                    printConfig()
                }
            }
        }

        stage('Resolve DCPs') {
            steps {
                script {
                    def resolved = [:]
                    config.components.each { name, component ->
                        resolved[name] = resolveDcp(component, config)
                    }
                    config.resolvedDcps = resolved
                }
            }
        }

        stage('Whole-Flow Build') {
            when {
                expression { return config.runImplementation }
            }

            steps {
                script {
                    if (!config.resolvedDcps?.wcore || !config.resolvedDcps?.wstack) {
                        error('Both DCPs must be resolved before whole-flow build')
                    }

                    echo 'Starting integrated whole-flow build'

                    withEnv([
                        "PROJECT_NAME=${config.projectName}",
                        "FPGA_PLATFORM=${config.platform}",
                        "WCORE_DCP=${config.resolvedDcps.wcore}",
                        "WSTACK_DCP=${config.resolvedDcps.wstack}",
                        "BUILD_OUTPUT_DIR=${config.directories.output}"
                    ]) {
                        sh '''
                            set -euo pipefail
                            echo "Whole-flow inputs"
                            echo "  Project:    $PROJECT_NAME"
                            echo "  Platform:   $FPGA_PLATFORM"
                            echo "  WCore DCP:  $WCORE_DCP"
                            echo "  WStack DCP: $WSTACK_DCP"
                            echo "  Output:     $BUILD_OUTPUT_DIR"
                            mkdir -p "$BUILD_OUTPUT_DIR"
                            echo "PLACEHOLDER: whole-flow build"
                        '''
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
