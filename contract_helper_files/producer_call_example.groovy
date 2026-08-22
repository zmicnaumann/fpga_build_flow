String contractVersion = getProducerContractVersion([
    contractRepoPath : "${env.WORKSPACE}/myContractRepo",
    versionFilePath  : "path/to/intf.ver"
])