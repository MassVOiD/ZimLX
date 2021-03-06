package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;

import java.util.HashSet;
import java.util.Set;

public class CustomAppFilter extends org.zimmob.zimlx.ZimAppFilter {
    private final Context mContext;

    public CustomAppFilter(Context context) {
        super(context);
        mContext = context;
    }

    static void resetAppFilter(Context context) {
        Utilities.getZimPrefs(context).setHiddenAppSet(new HashSet<String>());
    }

    static void setComponentNameState(Context context, ComponentKey key, boolean hidden) {
        String comp = key.toString();
        Set<String> hiddenApps = new HashSet<>(getHiddenApps(context));
        while (hiddenApps.contains(comp)) {
            hiddenApps.remove(comp);
        }
        if (hidden) {
            hiddenApps.add(comp);
        }
        setHiddenApps(context, hiddenApps);
    }

    static boolean isHiddenApp(Context context, ComponentKey key) {
        return getHiddenApps(context).contains(key.toString());
    }

    @SuppressWarnings("ConstantConditions") // This can't be null anyway
    public static Set<String> getHiddenApps(Context context) {
        return new HashSet<>(Utilities.getZimPrefs(context).getHiddenAppSet());
    }

    public static void setHiddenApps(Context context, Set<String> hiddenApps) {
        Utilities.getZimPrefs(context).setHiddenAppSet(hiddenApps);
    }

    @Override
    public boolean shouldShowApp(ComponentName componentName, UserHandle user) {
        return super.shouldShowApp(componentName, user)
                && !isHiddenApp(mContext, new ComponentKey(componentName, user));
    }
}
