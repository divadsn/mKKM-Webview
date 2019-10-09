package de.codebucket.mkkm;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Locale;
import java.util.UUID;

import cat.ereza.customactivityoncrash.config.CaocConfig;

import de.codebucket.mkkm.activity.CrashReportActivity;
import de.codebucket.mkkm.database.AppDatabase;
import de.codebucket.mkkm.login.LoginHelper;
import de.codebucket.mkkm.service.TicketExpiryCheckService;
import de.codebucket.mkkm.util.Const;
import de.codebucket.mkkm.util.RuntimeHelper;

public class MobileKKM extends Application {

    private static final String TAG = "MobileKKM";
    private static final String SALT = "_mkkm";

    private static MobileKKM instance;
    private static SharedPreferences preferences;
    private static AppDatabase database;
    private static LoginHelper loginHelper;

    private static final long WAIT_BEFORE_RESTART = 1000;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Use Android Device ID as fingerprint
        // mKKM webapp uses fingerprint2.js to generate a fingerprint based on user-agent
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences.getString("fingerprint", null) == null) {
            preferences.edit().putString("fingerprint", getFingerprint()).apply();
        }

        // Init offline database (first step to native migration)
        database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "appdata.db")
                .addMigrations(new Migration(4, 5) {
                    @Override
                    public void migrate(SupportSQLiteDatabase database) {
                        database.execSQL("UPDATE tickets SET lines = '[]'");
                    }
                })
                .fallbackToDestructiveMigration()
                .build();

        // Login handler
        loginHelper = new LoginHelper(preferences.getString("fingerprint", null));

        // Create notification channel on Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        // Custom Activity on Crash initialization
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .enabled(true)
                .showErrorDetails(true)
                .showRestartButton(true)
                .trackActivities(true)
                .minTimeBetweenCrashesMs(1)
                .errorActivity(CrashReportActivity.class)
                .apply();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NotificationManager.class);
        NotificationChannel expiryChannel = new NotificationChannel(Const.ID.EXPIRY_NOTIFICATION_CHANNEL, getString(R.string.notification_channel_expiry), NotificationManager.IMPORTANCE_HIGH);
        expiryChannel.setDescription(getString(R.string.notification_channel_expiry_desc));
        expiryChannel.setVibrationPattern(new long[]{0, 100, 100, 100});
        notificationManager.createNotificationChannel(expiryChannel);
    }

    public String getFingerprint() {
        String deviceId = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID) + SALT;
        return UUID.nameUUIDFromBytes(deviceId.getBytes()).toString().replaceAll("-", "");
    }

    public void updateFingerprint(String fingerprint) {
        preferences.edit().putString("fingerprint", fingerprint).apply();
        loginHelper.updateFingerprint(fingerprint);
    }

    public boolean isNetworkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.isConnected();
            }
        }

        return false;
    }

    public void setupTicketService() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (preferences.getBoolean("enable_notifications", false)) {
            if (scheduler.getAllPendingJobs().isEmpty()) {
                ComponentName service = new ComponentName(this, TicketExpiryCheckService.class);
                JobInfo info = new JobInfo.Builder(Const.ID.EXPIRY_CHECK_SERVICE_ID, service)
                        .setPeriodic(6 * 60 * 60 * 1000) // every 6 hours, 4 times a day
                        .setPersisted(true)
                        .build();
                scheduler.schedule(info);
            }
        } else {
            scheduler.cancel(Const.ID.EXPIRY_CHECK_SERVICE_ID);
        }
    }

    public static MobileKKM getInstance() {
        return instance;
    }

    public static SharedPreferences getPreferences() {
        return preferences;
    }

    public static AppDatabase getDatabase() {
        return database;
    }

    public static LoginHelper getLoginHelper() {
        return loginHelper;
    }

    public static void restartApp(final Context context) {
        ProgressDialog.show(context, null, context.getString(R.string.state_loading), true, false);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(WAIT_BEFORE_RESTART);
                } catch (Exception ex) {
                    Log.e(TAG, "Error waiting", ex);
                }

                RuntimeHelper.triggerRestart(context);
            }
        });
    }

    public static Locale getSystemLocale() {
        Locale locale = Locale.getDefault();
        if (!locale.getCountry().equalsIgnoreCase("pl")) {
            return Locale.forLanguageTag("en-US");
        }

        return locale;
    }

    public static boolean isDebug() {
        return BuildConfig.DEBUG && BuildConfig.BUILD_TYPE.equalsIgnoreCase("debug");
    }
}
