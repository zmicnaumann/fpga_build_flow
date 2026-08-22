import pkg.dcp.ContractVersionHelper

def getBuildContractVersion(Map config) {

    def helper = new ContractVersionHelper(this)

    String contractVersion =
        helper.getVersionInformation([
            component        : config.component,
            variant          : config.variant,
            buildNumber      : config.buildNumber,

            artifactoryBase  : config.artifactoryBase,

            contractRepoName : config.contractRepoName,
            contractRepoUrl  : config.contractRepoUrl,
            versionFilePath  : config.versionFilePath,

            workDir          : config.workDir
        ])

    echo "Contract version: ${contractVersion}"

    return contractVersion
}