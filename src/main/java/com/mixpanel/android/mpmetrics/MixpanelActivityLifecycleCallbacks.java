package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable check;
    private boolean mIsForeground = false;
    private boolean mPaused = true;
    private static Double sStartSessionTime;

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance, MPConfig config) {
        mMpInstance = mpInstance;
        mConfig = config;
        if (sStartSessionTime == null) {
            sStartSessionTime = (double) System.currentTimeMillis();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) { }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(final Activity activity) {
        // no-op
    }

    public void onActivityPaused(long sessionSaveDelay) {
        mPaused = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }
        mCurrentActivity = null;

        Log.d("MixpanelLifecycle", "Activity pause.");
        Log.d("MixpanelLifecycle", String.format("Session end scheduled to %s ms from now.", sessionSaveDelay));

        mHandler.postDelayed(check = new Runnable(){
            @Override
            public void run() {
                if (mIsForeground && mPaused) {
                    mIsForeground = false;
                    try {
                        double sessionLength = System.currentTimeMillis() - sStartSessionTime;
                        if (sessionLength >= mConfig.getMinimumSessionDuration() && sessionLength < mConfig.getSessionTimeoutDuration()) {
                            double elapsedTime = sessionLength / 1000;
                            double elapsedTimeRounded = Math.round(elapsedTime * 10.0) / 10.0;
                            JSONObject sessionProperties = new JSONObject();
                            sessionProperties.put(AutomaticEvents.SESSION_LENGTH, elapsedTimeRounded);
                            mMpInstance.getPeople().increment(AutomaticEvents.TOTAL_SESSIONS, 1);
                            mMpInstance.getPeople().increment(AutomaticEvents.TOTAL_SESSIONS_LENGTH, elapsedTimeRounded);
                            mMpInstance.track(AutomaticEvents.SESSION, sessionProperties, true);
                            Log.d("MixpanelLifecycle", "Session saved.");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mMpInstance.onBackground();
                }
            }
        }, sessionSaveDelay);
    }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityResumed(Activity activity) {
        // no-op
    }

    public void onActivityResumed() {
        Log.d("MixpanelLifecycle", "Activity resume.");

        mPaused = false;
        boolean wasBackground = !mIsForeground;
        mIsForeground = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        if (wasBackground) {
            // App is in foreground now
            sStartSessionTime = (double) System.currentTimeMillis();
            mMpInstance.onForeground();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) { }

    protected boolean isInForeground() {
        return mIsForeground;
    }

    private final MixpanelAPI mMpInstance;
    private final MPConfig mConfig;
    private WeakReference<Activity> mCurrentActivity;
}
