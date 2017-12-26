package org.petabytes.awesomeblogs;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDelegate;
import android.text.TextUtils;

import com.annimon.stream.Optional;
import com.crashlytics.android.Crashlytics;
import com.f2prateek.rx.preferences.Preference;
import com.f2prateek.rx.preferences.RxSharedPreferences;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.iid.FirebaseInstanceId;

import org.petabytes.api.Api;
import org.petabytes.awesomeblogs.auth.Authenticator;
import org.petabytes.awesomeblogs.base.Verifiable;
import org.petabytes.awesomeblogs.feeds.FeedsActivity;
import org.petabytes.awesomeblogs.util.Devices;
import org.petabytes.awesomeblogs.util.LifeCycles;
import org.petabytes.awesomeblogs.util.Preferences;
import org.petabytes.awesomeblogs.util.Strings;
import org.petabytes.coordinator.ActivityLayoutBinder;

import hugo.weaving.DebugLog;
import io.fabric.sdk.android.Fabric;
import rx.Observable;

public class AwesomeBlogsApp extends Application {

    private static AwesomeBlogsApp instance;
    private ActivityLayoutBinder activityLayoutBinder;
    private Api api;
    private Authenticator authenticator;
    private RxSharedPreferences preferences;
    private FirebaseAnalytics analytics;

    @DebugLog
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Fabric.with(this, new Crashlytics());
        instance = this;

        registerActivityLifecycleCallbacks(new LifeCycles.Activity() {
            @DebugLog
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (!(activity instanceof Verifiable)) {
                    return;
                }
                authenticator().isSignIn()
                    .filter(isSignIn -> !isSignIn)
                    .doOnNext($ -> activity.finish())
                    .doOnNext($ -> Preferences.accessToken().set(null))
                    .flatMap($ -> authenticator().signIn(instance))
                    .take(1)
                    .onErrorResumeNext(Observable.empty())
                    .subscribe($ -> startActivity(FeedsActivity.intent(instance)));
            }
        });
    }

    public static AwesomeBlogsApp get() {
        return instance;
    }

    public final ActivityLayoutBinder activityLayoutBinder() {
        return activityLayoutBinder == null ? activityLayoutBinder = createActivityLayoutBinder() : activityLayoutBinder;
    }

    protected ActivityLayoutBinder createActivityLayoutBinder() {
        return ActivityLayoutBinder.DEFAULT;
    }

    public final Api api() {
        return api == null ? api = createApi() : api;
    }

    protected Api createApi() {
        return new Api(this,
            () -> "awesome-blogs-android/" + BuildConfig.VERSION_NAME,
            () -> {
                Preference<String> preference = Preferences.deviceId();
                String deviceId = preference.get();
                if (TextUtils.isEmpty(deviceId)) {
                    deviceId = Devices.getId();
                    preference.set(deviceId);
                }
                return deviceId;
            },
            () -> {
                Preference<String> preference = Preferences.fcmToken();
                String fcmToken = preference.get();
                if (TextUtils.isEmpty(fcmToken)) {
                    fcmToken = Optional.ofNullable(FirebaseInstanceId.getInstance().getToken()).orElse(Strings.EMPTY);
                    preference.set(fcmToken);
                }
                return fcmToken;
            },
            () -> Preferences.accessToken().get(),
            false);
    }

    public final Authenticator authenticator() {
        return authenticator == null ? authenticator = createAuthenticator() : authenticator;
    }

    protected Authenticator createAuthenticator() {
        return new Authenticator();
    }

    public final RxSharedPreferences preferences() {
        return preferences == null ? preferences = createPreferences() : preferences;
    }

    RxSharedPreferences createPreferences() {
        return RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(this));
    }

    public final FirebaseAnalytics analytics() {
        return analytics == null ? analytics = createAnalytics() : analytics;
    }

    FirebaseAnalytics createAnalytics() {
        return FirebaseAnalytics.getInstance(this);
    }
}
