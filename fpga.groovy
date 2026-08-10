def build_dcp_only(Map configInput = [:]

   // String platform,
   // String buildGoal,
   // STRING encrypt,
   // String imageCode,
   // String manifestFile,
   // String manifestBranch,
   // String devToolsBranch,
   // String shaType,
   // String url_dcp_wcore,
   // String url_dcp_wstack

) {

   def config = [
      platform            : configInput.platform ?: "",
      buildGoal           : configInput.buildGoal ?: "",
      encrypt             : configInput.encrypt ?: "NO",
      imageCode           : configInput.imageCode ?: "",
      manifestFile        : configInput.manifestFile ?: "",
      manifestBranch      : configInput.manifestBranch ?: "",
      devToolsBranch      : configInput.devToolsBranch ?: "",
      shaType             : configInput.shaType ?: "",
      urlDcpWcore         : configInput.url_dcp_wcore ?: "",
      urlDcpWstack        : configInput.url_dcp_wstack ?: "",
      pathEncryptionKey   : configInput.path_encryption_key ?: "",
      manifestUrl         : configInput.manifestUrl ?: "ssh://git@bitbucket.eng.idirectgt.com:7999/fpga_hdl/manifest.git",
      lmLicenseFile       : configInput.lmLicenseFile ?: "7104@PEXMentor1.corp.idirectgt.com",
      xilinxLicenseFile   : configInput.xilinxLicenseFile ?: "7103@PEXMentor1.corp.idirectgt.com",
      needXDC             : configInput.needXDC ?: false,
      pathXDC             : configInput.pathXDC ?: "",
      dcpTop              : configInput.dcpTop ?: "",
      prependDescriptor   : "mainline-"
   ]

   echo "Config received: ${configInput}"
   echo "platform: ${config.platform}"

   if (config.manifestBranch?.contains("master")) {
      currentBuild.description = config.prependDescriptor + config.manifestBranch.toString()
   } else {
      currentBuild.description = config.manifestBranch.toString()
   }

   if (config.manifestBranch?.trim()) {
      echo "resolved manifest branch: ${config.manifestBranch}"
   }

   /* Build the project, if an error occurs during compilation build result is set to failure
      otherwise timing is verified.  If timing failure detected set build to unstable.
      Always upload whatever artifacts are available (reports, bistream, etc.) */
   try {

      stage("Cloning Repos") {
         repotool.run_repo("${config.manifestUrl}", "${config.manifestBranch}", "${config.manifestFile}")
      }

      stage("Print passed params into build_dcp_only") {
         print("platform:         ${config.platform}")
         print("buildGoal:        ${config.buildGoal}")
         print("encrypt_TF:       ${config.encrypt}")
         print("imageCode:        ${config.imageCode}")
         print("manifestFile:     ${config.manifestFile}")
         print("manifestBranch:   ${config.manifestBranch}")
         print("devToolsBranch:   ${config.devToolsBranch}")
         print("shaType:          ${config.shaType}")
         print("url_dcp_wcore:    ${config.urlDcpWcore}")
         print("url_dcp_wstack:   ${config.urlDcpWstack}")
         print("pathEncryptionKey:${config.pathEncryptionKey}")
      }

      stage("Building Image Code, Generating artifacts") {
         if (config.buildGoal == "single_dcp") {
            sh """
               echo "single_dcp flow"

               set -e
               cd ${env.WORKSPACE}/${config.platform}/${config.imageCode}
               source ../../fpga/scripts/env-setup.sh --jenkins
               echo "--- Build Environment:"
               env | sort

               echo "--- Building 450mp_05 FPGA checkpoint and handoff files"

               # FIXME - this should be the new proj
               echo "--- Make proj called:"
               make proj

               if [ "${NEED_XDC}" = "true" ]; then
                  echo "--- For this DCP - Need to add constraints file"
                  make constr ARGS="${config.pathXDC}"
               else
                  echo "--- For this DCP - No need to add constraints file"
               fi

               # test if key.v exists
               if [ ! -f "${config.pathEncryptionKey}" ]; then
                  echo "File does not exist."
               else
                  echo "key.v exists."
               fi

               if [ "${config.encrypt}" = "YES" ]; then
                  echo "encrypt yes"

                  vivado -mode batch -source ../../fpga/tcl/xilinx/build_dcp.tcl -tclargs \
                     Makefile Encrypt \
                     -dcp_top "${config.dcpTop}" \
                     -dcp_out "build/fpga_top.dcp" \
                     -key_file "${config.pathEncryptionKey}"

                  # make reports
               else
                  echo "encrypt no"

                  vivado -mode batch -source ../../fpga/tcl/xilinx/build_dcp.tcl -tclargs \
                     Makefile \
                     -dcp_top "${config.dcpTop}" \
                     -dcp_out "build/fpga_top.dcp" \
                     -key_file "${config.pathEncryptionKey}"

               fi

            """
         } else if (config.buildGoal == "using_dcps") {
            script {
               echo "using_dcps flow"
            }
         } else if (config.buildGoal == "from_scratch") {
            script {
               echo "from_scratch flow"
            }
         } else {
            script {
               echo "ERROR: wrong input: ${config.buildGoal}"
            }
         }
      }
               // here we want to set top, and create a dcp
               // if [ "${env.ENCRYPT}" = "YES" ]; then
               //    echo "encrypt yes"
               //    // vivado -mode batch -source ../../fpga/tcl/build_dcp.tcl -tclargs \
               //    //    Makefile Encrypt
               //    //    -dcp_top "wstack" \
               //    //    -dcp_out "fpga_top.dcp" \
               //    //    -keyfile "${env.PATH_ENCRYPTION_KEY}"
               // else
               //    echo "encrypt no"
               //    // vivado -mode batch -source ../../fpga/tcl/build_dcp.tcl -tclargs \
               //    //    Makefile \
               //    //    -dcp_top "wstack" \
               //    //    -dcp_out "fpga_top.dcp"
               // fi

      stage("Signing") {
         def signedPlatforms = ["450mp", "4350", "wdk", "mpower"]
         if (signedPlatforms.contains(config.platform.toString())) {
            if (findFiles(glob: "**/build/*.bit.tar.gz").any()) {
               echo "--- Starting .bit artifact signing process for ${config.platform.toString()}"

               def bitTarFile = findFiles(glob: "**/build/*.bit.tar.gz")
               echo "--- Found artifact tar file: ${bitTarFile[0].path}"

               def index = bitTarFile[0].name.indexOf('.tar.gz')
               def bitFileName = bitTarFile[0].name.substring(0, index)
               echo "--- Will attempt to sign bit file: ${bitFileName}"

               index = bitTarFile[0].path.lastIndexOf('/')
               def tarFileDir = pwd() + '/' + bitTarFile[0].path.substring(0, index)

               echo "--- Signing Step 1: Checking out dev_tools..."
               checkout \
                  scm: [ $class : 'GitSCM',
                  branches: [[name: "refs/heads/" + config.devToolsBranch]],
                  extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dev_tools']],
                  userRemoteConfigs: [[ url: 'ssh://git@bitbucket.eng.idirectgt.com:7999/infra/dev_tools.git']]
                  ]

               lock('crypto_server') {
                  sshagent(['crypto_rsa']) {
                     dir('signwork') {
                        sh """
                        set -e
                        mv ${tarFileDir}/${bitTarFile[0].name} .
                        tar -xvf ${bitTarFile[0].name} ./${bitFileName}
                        rm -vf ${bitTarFile[0].name}

                        echo "--- Signing Step 2: Signing FPGA .bit file using signing server"
                        python3 ${WORKSPACE}/dev_tools/tools/idirect/ipkg/signer.py -d . -f ${bitFileName} -a crypto.eng.idirectgt.com --sha-adv-hash ${config.shaType}

                        echo "--- Signing Step 3: Create tar file with signature and move to artifacts folder"
                        tar -cvzf ${bitTarFile[0].name} ./${bitFileName}*
                        mv -vf ${bitTarFile[0].name} ${tarFileDir}
                        """
                     }
                  }
               }
            }
            else {
               echo "--- No ${config.platform} .bit artifacts found in build dir to sign"
            }
         }
         else {
            echo "--- Signing process skipped for ${config.platform}"
         }
      }

      stage("Verifying Timing") {
         echo "printing ls in area to look"
         sh "ls \"${WORKSPACE}/${config.platform}/${config.imageCode}/proj/\""
         if (fileExists("${WORKSPACE}/${config.platform}/${config.imageCode}/proj/timing.fail")) {
            currentBuild.result = 'UNSTABLE'
            echo "--- Timing Failure, Set Build Unstable"
            echo "--- Build = ${currentBuild.currentResult}"
         } else if (fileExists("${WORKSPACE}/${config.platform}/${config.imageCode}/proj/timing.pass")) {
            currentBuild.result = 'SUCCESS'
            echo "--- Timing Closure, Set Build Success"
            echo "--- Build = ${currentBuild.currentResult}"
         } else {
            currentBuild.result = 'SUCCESS'
            echo "--- No timing file needed for this DCP test"
            echo "--- Build = ${currentBuild.currentResult}"
         }
      }

   // Catch the error, set failure
   } catch ( Exception err ) {

      currentBuild.result = 'FAILURE'
      echo "--- Compilation Error, Set Build Failure"
      println(err.toString());
      println(err.getMessage());

   // Always upload artifacts
   } finally {

      stage("Archiving Artifacts") {
         echo "--- Archiving Artifacts to Jenkins Build"
         // Timing closure log file
         if (findFiles(glob: "**/proj/timing.log").any()) {
            def logFile = findFiles(glob: "**/proj/timing.log")
            echo "--- Archiving timing.log"
            archiveArtifacts artifacts: "${logFile[0].path}"
         }
         // FPGA tool reports (*_rpt.tar.gz)
         if (findFiles(glob: "**/build/*_rpt.tar.gz").any()) {
            def rptFiles = findFiles(glob: "**/build/*_rpt.tar.gz")
            echo "--- Archiving FPGA Tool Reports: ${rptFiles[0].path}"
            archiveArtifacts artifacts: "${rptFiles[0].path}"
         }
         // Xilinx bitstream (*.bit.tar.gz)
         if (findFiles(glob: "**/build/*.bit.tar.gz").any()) {
            def bitFiles = findFiles(glob: "**/build/*.bit.tar.gz")
            echo "--- Archiving Xilinx FPGA Bitstream: ${bitFiles[0].path}"
            archiveArtifacts artifacts: "${bitFiles[0].path}"
         }
         // Xilinx debug probes (*.ltx.tar.gz)
         if (findFiles(glob: "**/build/*.ltx.tar.gz").any()) {
            def ltxFiles = findFiles(glob: "**/build/*.ltx.tar.gz")
            echo "--- Archiving Xilinx FPGA Debug Probes: ${ltxFiles[0].path}"
            archiveArtifacts artifacts: "${ltxFiles[0].path}"
         }
         // Xilinx checkpoint (*.dcp)
         if (findFiles(glob: "**/build/*.dcp").any()) {
            def dcpFiles = findFiles(glob: "**/build/*.dcp")
            echo "--- Archiving Xilinx FPGA DCP File: ${dcpFiles[0].path}"
            archiveArtifacts artifacts: "${dcpFiles[0].path}"
         }
         // Altera bitstream (*.rbf.tar.gz)
         if (findFiles(glob: "**/build/*.rbf.tar.gz").any()) {
            def bitFiles = findFiles(glob: "**/build/*.rbf.tar.gz")
            echo "--- Archiving Altera FPGA Bitstream: ${bitFiles[0].path}"
            archiveArtifacts artifacts: "${bitFiles[0].path}"
         }
         // Altera bitstream (*.sof.tar.gz)
         if (findFiles(glob: "**/build/*.sof.tar.gz").any()) {
            def bitFiles = findFiles(glob: "**/build/*.sof.tar.gz")
            echo "--- Archiving Altera FPGA Bitstream: ${bitFiles[0].path}"
            archiveArtifacts artifacts: "${bitFiles[0].path}"
         }
         // Altera bitstream (*.rbf.bz2) - DLC SW requires BZ2
         if (findFiles(glob: "**/build/*.rbf.bz2").any()) {
            def bitFiles = findFiles(glob: "**/build/*.rbf.bz2")
            echo "--- Archiving Altera FPGA Bitstream: ${bitFiles[0].path}"
            archiveArtifacts artifacts: "${bitFiles[0].path}"
         }
         // Altera CPLD bitstream (*.pof)
         if (findFiles(glob: "**/build/*.pof").any()) {
            def bitFiles = findFiles(glob: "**/build/*.pof")
            echo "--- Archiving Altera CPLD Bitstream: ${bitFiles[0].path}"
            archiveArtifacts artifacts: "${bitFiles[0].path}"
         }
         // Altera NIOS binary (*.bin.tar.gz) - Cloak module
         if (findFiles(glob: "**/build/*.bin.tar.gz").any()) {
            def binFile = findFiles(glob: "**/build/*.bin.tar.gz")
            echo "--- Archiving Altera NIOS Binary: ${binFile[0].path}"
            archiveArtifacts artifacts: "${binFile[0].path}"
         }

         echo "--- Uploading artifacts to Artifactory"
         // All files in build directory get uploaded
         // Define artifactory path
         def uploadDir = "fpga/wcore_wstack/${config.platform}/${config.imageCode}/${BUILD_NUMBER}-${config.manifestBranch}"
         // remote rootfs builds require the repo manifest XML to be in artifactory for consumption
         // repotool groovy lib generates this now in the first stage, copy it to build dir for upload
         bash.copyFile("${WORKSPACE}/manifest.${JOB_BASE_NAME}-${BUILD_NUMBER}.xml", "${WORKSPACE}/${config.platform}/${config.imageCode}/build/Fpga_${config.platform}_${config.imageCode}.xml")

         // Check for files in build dir, loop through all file and upload
         if (findFiles(glob: "**/build/*").any()) {
            def artifacts = findFiles(glob: "**/build/*")
            for (int i = 0; i < artifacts.size(); i++) {
               if (artifacts[i].path.startsWith("dev_tools") == false) {
                  jfrog.uploadFile("${WORKSPACE}/${artifacts[i].path}","${uploadDir}/${artifacts[i].name}")
               }
            }
         } else {
            echo "--- No artifacts in build dir to upload"
         }
      }

      // BRANCH_NAME = MANIFEST_BRANCH, needed for conan.create_and_upload()
      //    conan.create_and_upload() will create the alias to latest@idirectgov/stable but it will
      //    not be uploaded. This is done during FPGA promotion.
      // Naming convention: fpga-<PLATFORM>-<IMAGE_CODE>/<FPGA_BUILD_NUMBER>@jenkins/<MANIFEST_BRANCH>
      stage("Creating Conan Package") {
         // if ( "${params.IMAGE_CODE}" == "cpld" ) {
         //    // CPLDs are preprogrammed, no need for a conan pacakge
         //    print "CPLD - Conan Package NOT required"
         // } else {
         //    // Create a conan package of the FPGA image + artifacts.
         //    //    This will allow SW builds to consume branch builds of FPGA images for easier
         //    //    testing and integration.
         //    print "Conan Package: fpga-${PLATFORM}-${IMAGE_CODE}/${FPGA_BUILD_NUMBER}@jenkins/${MANIFEST_BRANCH}"
         //    def (server, client) = setup.conan()
         //    def buildInfo = conan.create_and_upload(client, 'default', '', "${WORKSPACE}/fpga/conan-packager")
         // }
      }
   }
}