package com.plumemu.fakeemulator;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * A stand-in emulator for RealLaunchTest: built by build.sh under the package and activity name of
 * an entry of the platform data (an activity-alias points here), it reports the Intent it received
 * to the test app by broadcast and tries to read the game through the URI grant it was given.
 *
 * build.sh may set two meta-data values: which package receives the report (default the launcher's
 * test APK; the app's end-to-end test uses the app's own package) and how long the "game" stays
 * open before it closes (default 0; the end-to-end test holds it past Plumemu's 10 s minimum so a
 * play session is counted).
 */
public class FakeEmulatorActivity extends Activity {
    static final String REPORT_ACTION = "com.plumemu.launcher.test.FAKE_EMULATOR_REPORT";
    static final String TEST_PACKAGE = "com.plumemu.launcher.test";
    static final String META_REPORT_PACKAGE = "com.plumemu.fakeemulator.REPORT_PACKAGE";
    static final String META_HOLD_MS = "com.plumemu.fakeemulator.HOLD_MS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent received = getIntent();
        Bundle meta = ownMetaData();
        String reportPackage = meta.getString(META_REPORT_PACKAGE, TEST_PACKAGE);
        long holdMs = meta.getInt(META_HOLD_MS, 0);
        Intent report = new Intent(REPORT_ACTION).setPackage(reportPackage);
        report.putExtra("component", String.valueOf(received.getComponent() == null ? null : received.getComponent().flattenToString()));
        report.putExtra("action", received.getAction());
        report.putExtra("data", received.getDataString());
        report.putExtra("type", received.getType());
        report.putExtra("flags", received.getFlags());
        Bundle extras = received.getExtras();
        Bundle copied = new Bundle();
        if (extras != null) {
            for (String key : extras.keySet()) {
                copied.putString(key, String.valueOf(extras.get(key)));
            }
        }
        report.putExtra("extras", copied);
        Uri uri = received.getData();
        ClipData clip = received.getClipData();
        if (clip != null && clip.getItemCount() > 0) uri = clip.getItemAt(0).getUri();
        if (uri != null && "content".equals(uri.getScheme())) report.putExtra("content", readAll(uri));
        sendBroadcast(report);
        if (holdMs <= 0) {
            finish();
        } else {
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, holdMs);
        }
    }

    private Bundle ownMetaData() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData == null ? new Bundle() : info.metaData;
        } catch (PackageManager.NameNotFoundException e) {
            return new Bundle();
        }
    }

    private String readAll(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return "error: no stream";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "error: " + e;
        }
    }
}
