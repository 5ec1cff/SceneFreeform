package five.ec1cff.scene_freeform.bridge;

interface ISystemServer {
    String getVersion() = 1;
    oneway void updateConfig(in Bundle newConfig) = 2;
    oneway void requestReloadConfig() = 3;
    oneway void addFreeformPackage(String name) = 4;
    oneway void setPackageBlacklist(String name, boolean isBlacklist) = 5;
}