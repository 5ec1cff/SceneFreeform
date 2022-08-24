package android.content.pm;

import android.content.Intent;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(PackageManager.class)
public abstract class PackageManagerHidden {
    public abstract ResolveInfo resolveActivityAsUser(Intent intent, int flags, int userId);
    public abstract int getPackageUidAsUser(String packageName, int userId);
    public List<ResolveInfo> queryIntentActivitiesAsUser(Intent intent, int flags, int userId) {
        throw new RuntimeException("");
    }
}
