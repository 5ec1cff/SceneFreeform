package android.content.pm;

import android.content.Intent;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(PackageManager.class)
public abstract class PackageManagerHidden {
    public abstract ResolveInfo resolveActivityAsUser(Intent intent, int flags, int userId);
    public abstract int getPackageUidAsUser(String packageName, int userId);
}
