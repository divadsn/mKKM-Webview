package de.codebucket.mkkm.activity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import de.codebucket.mkkm.MobileKKM;
import de.codebucket.mkkm.database.model.Account;
import de.codebucket.mkkm.database.model.AccountDao;
import de.codebucket.mkkm.login.AccountUtils;
import de.codebucket.mkkm.util.EncryptUtils;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "Splash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already signed in
        final android.accounts.Account deviceAccount = AccountUtils.getCurrentAccount();

        if (deviceAccount != null) {
            // Migrate plain password to encrypted credentials
            String password = AccountUtils.getPasswordEncrypted(deviceAccount);
            if (!EncryptUtils.isBase64(password)) {
                try {
                    String encryptedPassword = EncryptUtils.encrpytString(password);
                    AccountUtils.setPassword(deviceAccount, encryptedPassword);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to encrypt existing password", ex);
                }
            }
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Open login activity if no account found
                if (deviceAccount == null) {
                    launch(new Intent(SplashActivity.this, LoginActivity.class));
                    return;
                }

                final String passengerId = AccountUtils.getPassengerId(deviceAccount);
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        AccountDao dao = MobileKKM.getDatabase().accountDao();
                        Account account = dao.getById(passengerId);

                        // Don't continue if no instance found
                        if (account == null) {
                            AccountUtils.removeAccount(deviceAccount);
                            launch(new Intent(SplashActivity.this, LoginActivity.class));
                            return;
                        }

                        // Open MainActivity with signed in user
                        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                        intent.putExtra("account", account);
                        launch(intent);
                    }
                });
            }
        }, 500L);
    }

    private void launch(Intent intent) {
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}