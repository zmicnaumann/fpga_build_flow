// this could be a general method

import pkg.dcp.ContractVersionHelper

def getProducerContractVersion(Map config) {

    String contractVersion =
        ContractVersionHelper.getInterfaceVersion(
            config.contractRepoPath,
            config.versionFilePath
        )

    echo "Contract version: ${contractVersion}"

    return contractVersion
}