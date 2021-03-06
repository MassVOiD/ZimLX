package org.zimmob.zimlx;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ComponentKey;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;

import org.jetbrains.annotations.NotNull;
import org.zimmob.zimlx.gestures.GestureController;
import org.zimmob.zimlx.iconpack.EditIconActivity;
import org.zimmob.zimlx.iconpack.IconPackManager;
import org.zimmob.zimlx.iconpack.IconPackManager.CustomIconEntry;
import org.zimmob.zimlx.override.CustomInfoProvider;
import org.zimmob.zimlx.settings.ui.SettingsActivity;
import org.zimmob.zimlx.views.ZimBackgroundView;

public class ZimLauncher extends NexusLauncherActivity implements ZimPreferences.OnPreferenceChangeListener {

    public static Context mContext;
    public static final int REQUEST_PERMISSION_STORAGE_ACCESS = 666;
    public static final int REQUEST_PERMISSION_LOCATION_ACCESS = 667;
    public static final int CODE_EDIT_ICON = 100;

    public static Drawable currentEditIcon = null;
    public static ItemInfo currentEditInfo = null;
    private GestureController gestureController;

    private ZimPreferencesChangeCallback prefCallback = new ZimPreferencesChangeCallback(this);
    public ZimPreferences zimPrefs;
    private boolean paused = false;
    private boolean sRestart = false;
    public ZimBackgroundView background;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !Utilities.hasStoragePermission(this)) {
            Utilities.requestStoragePermission(this);
        }
        super.onCreate(savedInstanceState);
        mContext = this;
        zimPrefs = Utilities.getZimPrefs(mContext);
        zimPrefs.registerCallback(prefCallback);
        background = findViewById(R.id.zim_background);
        gestureController = new GestureController(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (FeatureFlags.QSB_ON_FIRST_SCREEN != showSmartspace()) {
            if (Utilities.ATLEAST_NOUGAT) {
                recreate();
            } else {
                finish();
                startActivity(getIntent());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        restartIfPending();
        paused = false;
    }

    public void onPause() {
        super.onPause();

        paused = true;
    }

    public void restartIfPending() {
        if (sRestart) {
            //zimApp.restart(false);
            ZimAppKt.getZimApp(mContext).restart(false);
        }
    }

    @Override
    public void finishBindingItems() {
        super.finishBindingItems();
        Utilities.onLauncherStart();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Utilities.onLauncherStart();
    }


    public GestureController getGestureController() {
        return gestureController;
    }

    private boolean showSmartspace() {
        return Utilities.getPrefs(this).getBoolean(SettingsActivity.SMARTSPACE_PREF, true);
    }

    @Override
    public void onValueChanged(@NotNull String key, @NotNull ZimPreferences prefs, boolean force) {

    }

    public static ZimLauncher getLauncher(Context context) {
        if (context instanceof Launcher) {
            return (ZimLauncher) context;
        }
        return ((ZimLauncher) ((ContextWrapper) context).getBaseContext());
    }

    public void scheduleRestart() {
        if (paused) {
            sRestart = true;
        } else {
            Utilities.restartLauncher(this);
        }
    }

    public void onDestroy() {
        super.onDestroy();

        Utilities.getZimPrefs(this).unregisterCallback();

        if (sRestart) {
            sRestart = false;
            LauncherAppState.destroyInstance();
            ZimPreferences.Companion.destroyInstance();
        }
    }


    public boolean shouldRecreate() {
        return !sRestart;
    }

    public void refreshGrid() {
        mWorkspace.refreshChildren();
    }

    public void startEditIcon(ItemInfo itemInfo, CustomInfoProvider<ItemInfo> infoProvider) {
        ComponentKey component;

        if (itemInfo instanceof AppInfo) {
            component = ((AppInfo) itemInfo).toComponentKey();
            currentEditIcon = IconPackManager.Companion.getInstance(this).getEntryForComponent(component).getDrawable();
        } else if (itemInfo instanceof ShortcutInfo) {
            component = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);
            currentEditIcon = new BitmapDrawable(mContext.getResources(), ((ShortcutInfo) itemInfo).iconBitmap);
        } else if (itemInfo instanceof FolderInfo) {
            component = ((FolderInfo) itemInfo).toComponentKey();
            currentEditIcon = ((FolderInfo) itemInfo).getIcon(mContext);
        } else {
            component = null;
            currentEditIcon = null;
        }

        currentEditInfo = itemInfo;
        Boolean folderInfo = itemInfo instanceof FolderInfo;
        Intent intent = EditIconActivity
                .Companion
                .newIntent(this, infoProvider.getTitle(itemInfo), folderInfo, component);
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        BlankActivity.Companion
                .startActivityForResult(this, intent, CODE_EDIT_ICON, flags, (resultCode, data) -> {
                    handleEditIconResult(resultCode, data);
                    return null;
                });

    }

    private void handleEditIconResult(int resultCode, Bundle data) {
        if (resultCode == Activity.RESULT_OK) {
            if (currentEditInfo == null) {
                return;
            }
            ItemInfo itemInfo = currentEditInfo;
            String entryString = data.getString(EditIconActivity.EXTRA_ENTRY);
            CustomIconEntry customIconEntry = CustomIconEntry.Companion.fromString(entryString);

            CustomInfoProvider.Companion.forItem(this, itemInfo).setIcon(itemInfo, customIconEntry);
        }
    }
}
