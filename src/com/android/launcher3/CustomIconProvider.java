package com.android.launcher3;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import android.os.UserHandle;
import com.android.launcher3.compat.UserManagerCompat;

import android.content.res.TypedArray;
import android.content.res.Resources;
import android.os.Bundle;
import java.util.Calendar;
import java.util.List;

import android.os.Handler;
import android.content.IntentFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;

public class CustomIconProvider extends IconProvider
{
    private BroadcastReceiver mBroadcastReceiver;
    protected PackageManager mPackageManager;

    private Context mContext;
    private IconsHandler mIconsHandler;

    public CustomIconProvider(Context context) {
        super();
        mContext = context;
        mIconsHandler = IconCache.getIconsHandler(context);
        mBroadcastReceiver = new DynamicIconProviderReceiver(this);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        context.registerReceiver(mBroadcastReceiver, intentFilter, null, new Handler(LauncherModel.getWorkerLooper()));
        mPackageManager = context.getPackageManager();
    }

    private int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }

    private int getCorrectShape(Bundle bundle, Resources resources) {
        if (bundle != null) {
            int roundIcons = bundle.getInt("com.google.android.calendar.dynamic_icons_nexus_round", 0);
            if (roundIcons != 0) {
                try {
                    TypedArray obtainTypedArray = resources.obtainTypedArray(roundIcons);
                    int resourceId = obtainTypedArray.getResourceId(dayOfMonth(), 0);
                    obtainTypedArray.recycle();
                    return resourceId;
                }
                catch (Resources.NotFoundException ex) {
                }
            }
        }

        return 0;
    }

    private boolean isCalendar(final String s) {
        return "com.google.android.calendar".equals(s);
    }

    @Override
    public Drawable getIcon(final LauncherActivityInfoCompat launcherActivityInfoCompat, int iconDpi) {
        Drawable drawable = null;
        String packageName = launcherActivityInfoCompat.getApplicationInfo().packageName;
        Bitmap bm = mIconsHandler.getDrawableIconForPackage(launcherActivityInfoCompat.getComponentName());
        if (bm == null) {
            return launcherActivityInfoCompat.getIcon(iconDpi);
        }

        if (isCalendar(packageName)) {
            try {
                ActivityInfo activityInfo = mPackageManager.getActivityInfo(launcherActivityInfoCompat.getComponentName(), PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                Bundle metaData = activityInfo.metaData;
                Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
                int shape = getCorrectShape(metaData, resourcesForApplication);
                if (shape != 0) {
                    drawable = resourcesForApplication.getDrawableForDensity(shape, iconDpi);
                }
            }
            catch (PackageManager.NameNotFoundException ex3) {}
        }

        if (drawable == null) {
            drawable = super.getIcon(launcherActivityInfoCompat, iconDpi);
        }

        return new BitmapDrawable(mContext.getResources(), Utilities.createIconBitmap(bm, mContext));
    }

    public String getIconSystemState(String s) {
        if (isCalendar(s)) {
            return mSystemState + " " + dayOfMonth();
        }
        return mSystemState;
    }

    class DynamicIconProviderReceiver extends BroadcastReceiver
    {
        CustomIconProvider mDynamicIconProvider;

        DynamicIconProviderReceiver(final CustomIconProvider dynamicIconProvider) {
            mDynamicIconProvider = dynamicIconProvider;
        }

        public void onReceive(final Context context, final Intent intent) {
            for (UserHandle userHandle : UserManagerCompat.getInstance(context).getUserProfiles()) {
                LauncherAppState instance = LauncherAppState.getInstance();
                instance.getModel().onPackageChanged("com.google.android.calendar", userHandle);
                List queryForPinnedShortcuts = instance.getShortcutManager().queryForPinnedShortcuts("com.google.android.calendar", userHandle);
                if (!queryForPinnedShortcuts.isEmpty()) {
                    instance.getModel().updatePinnedShortcuts("com.google.android.calendar", queryForPinnedShortcuts, userHandle);
                }
            }
        }
    }
}
