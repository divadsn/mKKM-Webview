package de.codebucket.mkkm.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import de.codebucket.mkkm.KKMWebViewClient;
import de.codebucket.mkkm.R;
import de.codebucket.mkkm.database.model.Account;
import de.codebucket.mkkm.login.AccountUtils;
import de.codebucket.mkkm.util.PicassoDrawable;

import static de.codebucket.mkkm.KKMWebViewClient.getPageUrl;

public abstract class DrawerActivity extends WebViewActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int TIME_INTERVAL = 2000;

    protected NavigationView mNavigationView;
    protected long mBackPressed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setupDrawer() {
        // Set up drawer menu
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        mNavigationView.getMenu().getItem(0).setChecked(true);

        // Set title to first navbar item
        setTitle(mNavigationView.getMenu().getItem(0).getTitle());
    }

    public void setupHeaderView(Account account) {
        View headerView = mNavigationView.getHeaderView(0);

        TextView headerNameView = headerView.findViewById(R.id.drawer_header_name);
        headerNameView.setText(getString(R.string.nav_header_title, account.getFirstName(), account.getLastName()));

        TextView headerEmailView = headerView.findViewById(R.id.drawer_header_email);
        headerEmailView.setText(getString(R.string.nav_header_subtitle, account.getEmail()));
    }

    public void setupHeaderAvatar(final Bitmap bitmap) {
        final ImageView headerAvatarView = mNavigationView.getHeaderView(0).findViewById(R.id.drawer_header_avatar);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PicassoDrawable drawable = new PicassoDrawable(DrawerActivity.this, bitmap, headerAvatarView.getDrawable(), false);
                headerAvatarView.setImageDrawable(drawable);
            }
        });
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        // Check if current page is not home
        String homeUrl = getPageUrl("home");
        if (!homeUrl.equals(mWebview.getUrl())) {
            mWebview.loadUrl(homeUrl);
            return;
        }

        // Press back to exit twice
        if (mBackPressed + TIME_INTERVAL < System.currentTimeMillis()) {
            Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show();
            mBackPressed = System.currentTimeMillis();
            return;
        }

        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            mWebview.reload();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        switch (item.getItemId()) {
            case R.id.nav_tickets:
                mWebview.loadUrl(getPageUrl("home")); // TODO: Replace with TicketOverviewFragment
                break;
            case R.id.nav_purchase:
                Toast.makeText(this, R.string.purchase_warning, Toast.LENGTH_LONG).show();
                mWebview.loadUrl(getPageUrl("ticket/buy")); // TODO: Add custom webview handler for purchasing
                break;
            case R.id.nav_account:
                mWebview.loadUrl(getPageUrl("account")); // TODO: Replace with UserAccountFragment
                break;
            case R.id.nav_pricing:
                mWebview.loadUrl("https://www.codebucket.de/mobilekkm/cennik.html");
                break;
            case R.id.nav_backup:
                startActivity(new Intent(DrawerActivity.this, BackupActivity.class));
                break;
            case R.id.nav_logout:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_logout_title)
                        .setMessage(R.string.dialog_logout_warning)
                        .setNegativeButton(R.string.dialog_no, null)
                        .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                doLogout();
                            }
                        })
                        .show();
                break;
            case R.id.nav_settings:
                startActivity(new Intent(DrawerActivity.this, SettingsActivity.class));
                break;
        }

        // Change title only on checkable items
        if (item.isCheckable()) {
            setTitle(item.getTitle());
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onPageChanged(WebView view, String page) {
        MenuItem item = null;

        switch (page) {
            case KKMWebViewClient.PAGE_OVERVIEW:
            case KKMWebViewClient.PAGE_CONTROL:
                item = mNavigationView.getMenu().findItem(R.id.nav_tickets);
                break;
            case KKMWebViewClient.PAGE_PURCHASE:
                item = mNavigationView.getMenu().findItem(R.id.nav_purchase);
                break;
            case KKMWebViewClient.PAGE_ACCOUNT:
                item = mNavigationView.getMenu().findItem(R.id.nav_account);
                break;
        }

        if (item != null && !item.isChecked()) {
            mNavigationView.setCheckedItem(item);
            setTitle(item.getTitle());
        }
    }

    protected void doLogout() {
        AccountUtils.removeAccount(AccountUtils.getAccount());

        // Return back to login screen
        startActivity(new Intent(DrawerActivity.this, LoginActivity.class));
        finish();
    }
}
