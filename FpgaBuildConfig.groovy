class FpgaComponentConfig {
    String variant
    String manifestBranch
    String imageCode
    String source
    String url
    String encrypt
    String encryptionFilePath
    String topModule
    String baseDir
    String manifestFile
    Boolean needXDC
    String pathXDC
    String outputFile

    FpgaComponentConfig(Map values = [:]) {
        this.variant = values.variant ?: ""
        this.manifestBranch = values.manifestBranch ?: ""
        this.imageCode = values.imageCode ?: ""
        this.source = values.source ?: "BUILD"
        this.url = values.url ?: ""
        this.encrypt = values.encrypt ?: "NO"
        this.encryptionFilePath = values.encryptionFilePath ?: ""
        this.topModule = values.topModule ?: ""
        this.baseDir = values.baseDir ?: ""
        this.manifestFile = values.manifestFile ?: ""
        this.needXDC = values.needXDC != null ? Boolean.valueOf(values.needXDC) : false
        this.pathXDC = this.needXDC ? (values.pathXDC ?: "") : ""
        this.outputFile = values.outputFile ?: ""
    }

    void validate(String componentName) {
        if (!manifestBranch) {
            throw new IllegalArgumentException("${componentName.toUpperCase()}_MANIFEST_BRANCH is required")
        }
        if (source == "URL" && !url) {
            throw new IllegalArgumentException("${componentName.toUpperCase()}_DCP_URL is required when ${componentName.toUpperCase()}_DCP_SOURCE is URL")
        }
        if (encrypt == "YES" && !encryptionFilePath) {
            throw new IllegalArgumentException("${componentName.toUpperCase()}_ENCRYPTIONFILE_PATH is required when ${componentName.toUpperCase()}_ENCRYPT is YES")
        }
        if (needXDC && !pathXDC) {
            throw new IllegalArgumentException("${componentName.toUpperCase()}_PATH_XDC is required when ${componentName.toUpperCase()}_NEED_XDC is true")
        }
    }
}

class FpgaBuildConfig {
    static final String DEFAULT_MANIFEST_URL = "ssh://git@bitbucket.eng.idirectgt.com:7999/fpga_hdl/manifest.git"

    String projectName
    String platform
    String manifestUrl
    Boolean runImplementation
    Map components = [:]
    Map directories = [:]
    Map historical = [
        alteraLicenseFile : "",
        xilinxLicenseFile : "",
        shaType           : "",
        devToolsBranch    : "",
        note              : "historical / not currently used by build flow"
    ]

    FpgaBuildConfig(Map values = [:]) {
        this.projectName = values.projectName ?: ""
        this.platform = values.platform ?: ""
        this.manifestUrl = values.manifestUrl ?: DEFAULT_MANIFEST_URL
        this.runImplementation = values.runImplementation ?: true

        if (values.components) {
            values.components.each { name, component ->
                this.components[name] = component instanceof FpgaComponentConfig
                    ? component
                    : new FpgaComponentConfig(component ?: [:])
            }
        }

        if (values.directories) {
            this.directories = values.directories
        }
        if (values.historical) {
            this.historical.putAll(values.historical)
        }
    }

    static FpgaBuildConfig fromMap(Map values = [:]) {
        def config = new FpgaBuildConfig(values)
        if (!config.projectName) {
            throw new IllegalArgumentException("PROJECT_NAME is required")
        }
        config.components.each { name, component ->
            component.validate(name)
        }
        return config
    }
}
