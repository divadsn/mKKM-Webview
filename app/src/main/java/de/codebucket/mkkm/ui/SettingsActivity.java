package de.codebucket.mkkm.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import de.codebucket.mkkm.BuildConfig;
import de.codebucket.mkkm.R;

import me.jfenn.attribouter.Attribouter;

public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, FragmentManager.OnBackStackChangedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }

        return true;
    }

    @Override
    public void onBackStackChanged() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            setTitle(R.string.title_activity_settings);
        }
    }

    @Override
    public boolean onPreferenceStartFragment(androidx.preference.PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment;
        if (pref.getKey().equals("about")) {
            setTitle(R.string.title_attribouter_about);
            fragment = Attribouter.from(this)
                    .withGitHubToken(BuildConfig.GITHUB_TOKEN)
                    .withFile(R.xml.about)
                    .toFragment();
        } else {
            fragment = Fragment.instantiate(this, pref.getFragment());
        }

        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.animator.fly_in, R.animator.fade_out, R.animator.fade_in, R.animator.fly_out);
            transaction.replace(R.id.container, fragment);
            transaction.addToBackStack("PreferenceFragment");
            transaction.commit();
            return true;
        }

        return false;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Preference about = findPreference("about");
            about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((SettingsActivity) getActivity()).onPreferenceStartFragment(SettingsFragment.this, preference);
                    return true;
                }
            });

            Preference version = findPreference("version");
            version.setSummary(getString(R.string.pref_version_description, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE, BuildConfig.GIT_VERSION));
        }

        @Override
        public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            return true;
        }
    }
}
