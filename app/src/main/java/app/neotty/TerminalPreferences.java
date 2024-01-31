/*
*************************************************************************
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*************************************************************************
*/
package app.neotty;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class TerminalPreferences {

    private static final String PREF_SHOW_EXTRA_KEYS = "show_extra_keys";
    private static final String PREF_IGNORE_BELL = "ignore_bell";
    private static final String PREF_DATA_VERSION = "data_version";
    private static final String PREF_DEFAULT_SSH_USER = "default_ssh_user";
    private static final String PREF_DISABLE_AUTO_SCROLLING = "disable_auto_scrolling";

    private boolean mFirstRun;
    private boolean mShowExtraKeys;
    private boolean mIgnoreBellCharacter;
    private boolean mDisableAutoScroll;
    private int mDataVersion;
    private String mDefaultSshUser;

    public TerminalPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mShowExtraKeys = prefs.getBoolean(PREF_SHOW_EXTRA_KEYS, true);
        mIgnoreBellCharacter = prefs.getBoolean(PREF_IGNORE_BELL, false);
        mDisableAutoScroll = prefs.getBoolean(PREF_DISABLE_AUTO_SCROLLING, false);
        mDataVersion = prefs.getInt(PREF_DATA_VERSION, 0);
        mDefaultSshUser = prefs.getString(PREF_DEFAULT_SSH_USER, "root");
    }

    public boolean isExtraKeysEnabled() {
        return mShowExtraKeys;
    }

    public boolean toggleShowExtraKeys(Context context) {
        mShowExtraKeys = !mShowExtraKeys;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_SHOW_EXTRA_KEYS, mShowExtraKeys).apply();
        return mShowExtraKeys;
    }

    public boolean isBellIgnored() {
        return mIgnoreBellCharacter;
    }

    public void setIgnoreBellCharacter(Context context, boolean newValue) {
        mIgnoreBellCharacter = newValue;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_IGNORE_BELL, newValue).apply();
    }

    public boolean isAutoScrollDisabled() {
        return mDisableAutoScroll;
    }

    public void setDisableAutoScroll(Context context, boolean newValue) {
        mDisableAutoScroll = newValue;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_DISABLE_AUTO_SCROLLING, newValue).apply();
    }

    public void updateDataVersion(Context context) {
        mDataVersion = BuildConfig.VERSION_CODE;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(PREF_DATA_VERSION, mDataVersion).apply();
    }

    public int getDataVersion() {
        return mDataVersion;
    }

    public void setDefaultSshUser(Context context, String userName) {
        mDefaultSshUser = userName;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_DEFAULT_SSH_USER, userName).apply();
    }

    public String getDefaultSshUser() {
        return mDefaultSshUser;
    }
}
