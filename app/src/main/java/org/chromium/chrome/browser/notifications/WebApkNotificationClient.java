// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.RemoteException;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.webapk.lib.client.WebApkServiceConnectionManager;
import org.chromium.webapk.lib.runtime_library.IWebApkApi;

/**
 * WebApkNotificationClient provides an API to display and close notifications remotely in
 * context of a WebAPK, enriching the notification with the WebAPK's small icon when available.
 */
public class WebApkNotificationClient {
    private static final String TAG = "cr_WebApk";

    // Callback which catches RemoteExceptions thrown due to IWebApkApi failure.
    private abstract static class ApiUseCallback
            implements WebApkServiceConnectionManager.ConnectionCallback {
        public abstract void useApi(IWebApkApi api) throws RemoteException;

        @Override
        public void onConnected(IWebApkApi api) {
            try {
                useApi(api);
            } catch (RemoteException e) {
                Log.w(TAG, "WebApkAPI use failed.", e);
            }
        }
    }

    /**
     * Connect to a WebAPK's bound service, build a notification and hand it over to the WebAPK to
     * display. Handing over the notification makes the notification look like it originated from
     * the WebAPK - not Chrome - in the Android UI.
     */
    public static void notifyNotification(final String webApkPackage,
            final NotificationBuilderBase notificationBuilder, final String platformTag,
            final int platformID) {
        final ApiUseCallback connectionCallback = new ApiUseCallback() {
            @Override
            public void useApi(IWebApkApi api) throws RemoteException {
                int smallIconId = api.getSmallIconId();
                // Prior to Android M, the small icon had to be from the resources of the app whose
                // NotificationManager is used in {@link NotificationManager#notify()}. On Android
                // M+, the small icon has to be from the resources of the app whose context is
                // passed to the {@link Notification.Builder()} constructor.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android M+ introduced
                    // {@link Notification.Builder#setSmallIcon(Bitmap icon)}.
                    if (!notificationBuilder.hasSmallIconBitmap()) {
                        notificationBuilder.setSmallIcon(
                                decodeImageResource(webApkPackage, smallIconId));
                    }
                } else {
                    notificationBuilder.setSmallIcon(smallIconId);
                }
                api.notifyNotification(platformTag, platformID, notificationBuilder.build());
            }
        };

        WebApkServiceConnectionManager.getInstance().connect(
                ContextUtils.getApplicationContext(), webApkPackage, connectionCallback);
    }

    /**
     * Cancel notification previously shown by WebAPK.
     */
    public static void cancelNotification(
            String webApkPackage, final String platformTag, final int platformID) {
        final ApiUseCallback connectionCallback = new ApiUseCallback() {
            @Override
            public void useApi(IWebApkApi api) throws RemoteException {
                api.cancelNotification(platformTag, platformID);
            }
        };
        WebApkServiceConnectionManager.getInstance().connect(
                ContextUtils.getApplicationContext(), webApkPackage, connectionCallback);
    }

    /** Decodes bitmap from WebAPK's resources. */
    private static Bitmap decodeImageResource(String webApkPackage, int resourceId) {
        PackageManager packageManager = ContextUtils.getApplicationContext().getPackageManager();
        try {
            Resources resources = packageManager.getResourcesForApplication(webApkPackage);
            return BitmapFactory.decodeResource(resources, resourceId);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
