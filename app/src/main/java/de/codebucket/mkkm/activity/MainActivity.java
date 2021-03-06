package de.codebucket.mkkm.activity;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

import de.codebucket.mkkm.KKMWebViewClient;
import de.codebucket.mkkm.MobileKKM;
import de.codebucket.mkkm.R;
import de.codebucket.mkkm.database.model.Account;
import de.codebucket.mkkm.database.model.AccountDao;
import de.codebucket.mkkm.database.model.Photo;
import de.codebucket.mkkm.database.model.PhotoDao;
import de.codebucket.mkkm.login.UserLoginTask;
import de.codebucket.mkkm.util.Const;

import static de.codebucket.mkkm.KKMWebViewClient.getPageUrl;

public class MainActivity extends DrawerActivity implements UserLoginTask.OnCallbackListener {

    private static final String WEBAPP_URL = getPageUrl("home");

    private Account mAccount;
    private UserLoginTask mAuthTask;
    private FloatingActionButton mActionButton;

    // Based on that we skip fetching account on sync
    private boolean firstSetup = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if user is logged in
        if (!getIntent().hasExtra("account")) {
            // Pass over intent data if activity was opened by an app link
            Intent intent = new Intent(this, SplashActivity.class);
            if (getIntent().getData() != null) {
                intent.setData(getIntent().getData());
            }

            startActivity(intent);
            finish();
            return;
        }

        // Set up action bar
        setupToolbar();

        // Set up navigation drawer
        setupDrawer();

        // Get user account from login
        mAccount = (Account) getIntent().getSerializableExtra("account");
        firstSetup = getIntent().getBooleanExtra("firstSetup", false);
        setupHeaderView(mAccount);

        // Hide citizen status if account does not have
        if (TextUtils.isEmpty(mAccount.getCitizenGuid())) {
            mNavigationView.getMenu().findItem(R.id.nav_citizen_status).setVisible(false);
        }

        // Set up webview layout
        setupWebView();

        // Set up floating action button
        mActionButton = findViewById(R.id.fab);
        mActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.loadUrl(getPageUrl("ticket/buy"));
            }
        });

        // Show facebook dialog on second run and only if it hasn't been shown yet
        final SharedPreferences preferences = MobileKKM.getPreferences();
        if (!firstSetup && !preferences.getBoolean("facebook_shown", false)) {
            // Always set this to true after showing the dialog
            preferences.edit().putBoolean("facebook_shown", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_facebook_title)
                    .setMessage(R.string.dialog_facebook_body)
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                // Open app page in facebook app
                                String facebookUrl = Const.getFacebookPageUrl(MainActivity.this);
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(facebookUrl));
                                startActivity(intent);
                            } catch (ActivityNotFoundException exc) {
                                // Believe me, this actually happens.
                                Toast.makeText(MainActivity.this, R.string.no_browser_activity, Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        }

        // Show payment result from intent data
        if (getIntent().getData() != null) {
            this.onNewIntent(getIntent());
        }

        // Load additional data from database and inject webapp
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                PhotoDao photoDao = MobileKKM.getDatabase().photoDao();
                Photo photo = photoDao.getById(mAccount.getPhotoId());

                // Set drawer header background if photo exists
                if (photo != null) {
                    setupHeaderAvatar(photo.getBitmap());
                }

                // Execute login and inject webapp
                injectWebapp();
            }
        });
    }

    public void injectWebapp() {
        if (mAuthTask != null) {
            return;
        }

        // Pass current account instance based on firstSetup value
        mAuthTask = new UserLoginTask(this);
        if (firstSetup) {
            mAuthTask.setAccount(mAccount);
        }

        // Do the sync first, then load the webapp
        mAuthTask.execute();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if token has expired and re-inject
        if (MobileKKM.getLoginHelper().hasSessionExpired()) {
            injectWebapp();
        }

        MobileKKM.getInstance().setupTicketService();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Don't continue if someone tried to call activity without url
        if (intent.getData() == null) {
            return;
        }

        final Uri data = intent.getData();
        final SharedPreferences preferences = MobileKKM.getPreferences();

        // Check if it contains both id and result parameters and if there is an ongoing payment
        if (data.getQueryParameter("id") == null || data.getQueryParameter("result") == null || !preferences.contains("last_payment_url")) {
            Snackbar.make(findViewById(R.id.swipe), R.string.no_payment, Snackbar.LENGTH_LONG).show();
            return;
        }

        Uri paymentUrl = Uri.parse(preferences.getString("last_payment_url", null));

        // Check if payment id is matching crc from ongoing payment url
        if (data.getQueryParameter("id").equals(paymentUrl.getQueryParameter("crc"))) {
            if (data.getQueryParameter("result").equals("ok")) {
                preferences.edit().remove("last_payment_url").apply();
            }
        }

        switch (data.getQueryParameter("result")) {
            case "ok":
                Snackbar.make(findViewById(R.id.swipe), R.string.payment_complete, Snackbar.LENGTH_LONG).show();
                break;
            case "error":
                Snackbar.make(findViewById(R.id.swipe), R.string.payment_error, Snackbar.LENGTH_LONG).show();
                break;
        }

        // Delete intent data if the activity was created
        if (intent.hasExtra("account")) {
            intent.setData(null);
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                mWebView.reload();
            }
        }, 500);
    }

    @Override
    public Object onPostLogin(Account account) throws IOException {
        // Always update stored account
        AccountDao accountDao = MobileKKM.getDatabase().accountDao();
        accountDao.insert(account);

        // Check if photoId has changed
        PhotoDao photoDao = MobileKKM.getDatabase().photoDao();
        String photoId = account.getPhotoId();

        if (!mAccount.getPhotoId().equals(photoId) || photoDao.getById(photoId) == null) {
            // Fetch photo from website and store in database
            Photo photo = MobileKKM.getLoginHelper().getPhoto(account);
            photoDao.insert(photo);

            // Remove old photo from database
            if (!photo.getPhotoId().equals(mAccount.getPhotoId())) {
                photoDao.deleteById(mAccount.getPhotoId()); // we can safely delete that photo
            }

            // Update drawer background
            setupHeaderAvatar(photo.getBitmap());
        }

        // Now we can update our local instance
        mAccount = account;

        // This should be always set to false after first sync
        firstSetup = false;
        return account;
    }

    @Override
    public void onSuccess(Object result) {
        mAuthTask = null;

        // Update drawer header
        Account account = (Account) result;
        setupHeaderView(account);

        // First inject session data into webview local storage, then load the webapp
        String startUrl = mWebView.getUrl() == null ? WEBAPP_URL : mWebView.getUrl();
        String inject = "<script type='text/javascript'>" +
                "localStorage.setItem('fingerprint', '" + MobileKKM.getLoginHelper().getFingerprint() + "');" +
                "localStorage.setItem('token', '" + MobileKKM.getLoginHelper().getSessionToken() + "');" +
                "window.location.replace('" + startUrl + "');" +
                "</script>";

        // Load the app
        mWebView.loadDataWithBaseURL("https://m.kkm.krakow.pl/inject", inject, "text/html", "utf-8", null);
    }

    @Override
    public void onError(int errorCode, String message) {
        mAuthTask = null;

        // Logout the user if the error is returned from backend
        if (errorCode == Const.ErrorCode.LOGIN_ERROR) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            doLogout();
        } else {
            Snackbar.make(findViewById(R.id.swipe), Const.getErrorMessage(errorCode, null), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.snackbar_retry, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            injectWebapp();
                        }
                    })
                    .setActionTextColor(Color.YELLOW)
                    .show();
        }
    }

    @Override
    public void onPageChanged(WebView view, String page) {
        super.onPageChanged(view, page);
        WindowManager.LayoutParams layout = getWindow().getAttributes();

        // Show floating action button on home page only
        if (KKMWebViewClient.PAGE_HOME.equals(page)) {
            mActionButton.show();
        } else {
            mActionButton.hide();
        }

        // Set display brightness to max during ticket control
        if (KKMWebViewClient.PAGE_CONTROL.equals(page)) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        } else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }

        getWindow().setAttributes(layout);
    }
}
