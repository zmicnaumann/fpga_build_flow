package pkg.dcp

class ContractVersionHelper {

    def steps

    ContractVersionHelper(steps) {
        this.steps = steps
    }


    String getVersionInformation(Map config) {

        String manifestPath =
            resolveManifestPath(
                config.artifactoryBase,
                config.component,
                config.variant,
                config.buildNumber
            )

        String localManifest =
            "${config.workDir}/manifest.xml"

        steps.sh """
            mkdir -p "${config.workDir}"

            # Skeleton:
            # download/copy manifest from Artifactory
            #
            # jf rt dl "${manifestPath}" "${localManifest}"
        """

        String manifestXml =
            steps.readFile(localManifest)

        String contractRevision =
            getRepoRevision(
                manifestXml,
                config.contractRepoName
            )

        String localRepo =
            "${config.workDir}/contractRepo"

        steps.sh """
            rm -rf "${localRepo}"

            git clone "${config.contractRepoUrl}" "${localRepo}"

            cd "${localRepo}"
            git checkout "${contractRevision}"
        """

        return getInterfaceVersion(
            localRepo,
            config.versionFilePath
        )
    }


    static String resolveManifestPath(
        String artifactoryBase,
        String component,
        String variant,
        String buildNumber
    ) {

        // Skeleton only for now.
        //
        // Eventually this should contain the ONE authoritative
        // Artifactory path convention.

        return "${artifactoryBase}/${component}/${variant}/${buildNumber}/manifest.xml"
    }


    static String getRepoRevision(
        String manifestXml,
        String repoName
    ) {

        def manifest =
            new XmlSlurper().parseText(manifestXml)

        def project =
            manifest.project.find {
                it.@name.toString() == repoName
            }

        if (!project) {
            throw new RuntimeException(
                "Could not find repo '${repoName}' in manifest"
            )
        }

        String revision =
            project.@revision.toString()

        if (!revision) {
            throw new RuntimeException(
                "Repo '${repoName}' has no revision in manifest"
            )
        }

        return revision
    }


    static String getInterfaceVersion(
        String repoPath,
        String versionFilePath
    ) {

        String fullPath =
            "${repoPath}/${versionFilePath}"

        List<String> lines =
            new File(fullPath).readLines()

        if (lines.size() < 2) {
            throw new RuntimeException(
                "Version file '${fullPath}' does not contain a second line"
            )
        }

        return normalizeVersion(lines[1])
    }


    static String normalizeVersion(
        String version
    ) {
        return version
            .trim()
            .replace('.', '_')
    }
}