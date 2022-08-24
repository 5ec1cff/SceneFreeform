package android.app;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(ActivityOptions.class)
public class ActivityOptionsHidden {
    public void setLaunchWindowingMode(int windowingMode) {
        throw new RuntimeException("");
    }
}
