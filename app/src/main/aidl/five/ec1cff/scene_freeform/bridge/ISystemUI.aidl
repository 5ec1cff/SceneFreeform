package five.ec1cff.scene_freeform.bridge;

interface ISystemUI {
    String getVersion() = 1;
    oneway void updateConfig(in Bundle newConfig) = 2;
    oneway void requestReloadConfig() = 3;
    oneway void setPackageWhitelist(String name, boolean isWhitelist) = 4;
}