String contractVersion = getBuildContractVersion([
    component        : params.COMPONENT,
    variant          : params.VARIANT,
    buildNumber      : params.BUILD_NUMBER,

    artifactoryBase  : env.DCP_ARTIFACTORY_BASE,

    contractRepoName : "myContractRepo.git",
    contractRepoUrl  : env.CONTRACT_REPO_URL,
    versionFilePath  : "path/to/intf.ver",

    workDir          : "${env.WORKSPACE}/contract_version"
])