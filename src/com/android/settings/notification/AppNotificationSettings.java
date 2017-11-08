/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.notification;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.applications.AppInfoBase;
import com.android.settings.widget.MasterSwitchPreference;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** These settings are per app, so should not be returned in global search results. */
public class AppNotificationSettings extends NotificationSettingsBase {
    private static final String TAG = "AppNotificationSettings";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static String KEY_GENERAL_CATEGORY = "categories";

    private List<NotificationChannelGroup> mChannelGroupList;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.NOTIFICATION_APP_NOTIFICATION;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mUid < 0 || TextUtils.isEmpty(mPkg) || mPkgInfo == null) {
            Log.w(TAG, "Missing package or uid or packageinfo");
            finish();
            return;
        }

        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
            mDynamicPreferences.clear();
        }

        if (mShowLegacyChannelConfig) {
            addPreferencesFromResource(R.xml.channel_notification_settings);
        } else {
            addPreferencesFromResource(R.xml.app_notification_settings);
            // Load channel settings
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... unused) {
                    mChannelGroupList = mBackend.getGroups(mPkg, mUid).getList();
                    Collections.sort(mChannelGroupList, mChannelGroupComparator);
                    return null;
                }

                @Override
                protected void onPostExecute(Void unused) {
                    if (getHost() == null) {
                        return;
                    }
                    populateList();
                }
            }.execute();
        }
        getPreferenceScreen().setOrderingAsAdded(true);

        for (NotificationPreferenceController controller : mControllers) {
            controller.onResume(mAppRow, mChannel, mChannelGroup, mSuspendedAppsAdmin);
            controller.displayPreference(getPreferenceScreen());
        }
        updatePreferenceStates();
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.notification_settings;
    }

    @Override
    protected List<AbstractPreferenceController> getPreferenceControllers(Context context) {
        mControllers = new ArrayList<>();
        mControllers.add(new HeaderPreferenceController(context, this));
        mControllers.add(new BlockPreferenceController(context, mImportanceListener, mBackend));
        mControllers.add(new BadgePreferenceController(context, mBackend));
        mControllers.add(new AllowSoundPreferenceController(
                context, mImportanceListener, mBackend));
        mControllers.add(new ImportancePreferenceController(context));
        mControllers.add(new SoundPreferenceController(context, this,
                mImportanceListener, mBackend));
        mControllers.add(new LightsPreferenceController(context, mBackend));
        mControllers.add(new VibrationPreferenceController(context, mBackend));
        mControllers.add(new VisibilityPreferenceController(context, new LockPatternUtils(context),
                mBackend));
        mControllers.add(new DndPreferenceController(context, getLifecycle(), mBackend));
        mControllers.add(new AppLinkPreferenceController(context));
        mControllers.add(new DescriptionPreferenceController(context));
        mControllers.add(new NotificationsOffPreferenceController(context));
        mControllers.add(new DeletedChannelsPreferenceController(context, mBackend));
        return new ArrayList<>(mControllers);
    }


    private void populateList() {
        if (!mDynamicPreferences.isEmpty()) {
            // If there's anything in mChannelGroups, we've called populateChannelList twice.
            // Clear out existing channels and log.
            Log.w(TAG, "Notification channel group posted twice to settings - old size " +
                    mDynamicPreferences.size() + ", new size " + mChannelGroupList.size());
            for (Preference p : mDynamicPreferences) {
                getPreferenceScreen().removePreference(p);
            }
        }
        if (mChannelGroupList.isEmpty()) {
            PreferenceCategory groupCategory = new PreferenceCategory(getPrefContext());
            groupCategory.setTitle(R.string.notification_channels);
            groupCategory.setKey(KEY_GENERAL_CATEGORY);
            getPreferenceScreen().addPreference(groupCategory);
            mDynamicPreferences.add(groupCategory);

            Preference empty = new Preference(getPrefContext());
            empty.setTitle(R.string.no_channels);
            empty.setEnabled(false);
            groupCategory.addPreference(empty);
        } else {
            populateGroupList();
            mImportanceListener.onImportanceChanged();
        }
    }

    private void populateGroupList() {
        PreferenceCategory groupCategory = new PreferenceCategory(getPrefContext());
        groupCategory.setTitle(R.string.notification_channels);
        groupCategory.setKey(KEY_GENERAL_CATEGORY);
        groupCategory.setOrderingAsAdded(true);
        getPreferenceScreen().addPreference(groupCategory);
        mDynamicPreferences.add(groupCategory);
        for (NotificationChannelGroup group : mChannelGroupList) {
            final List<NotificationChannel> channels = group.getChannels();
            int N = channels.size();
            // app defined groups with one channel and channels with no group display the channel
            // name and no summary and link directly to the channel page unless the group is blocked
            if ((group.getId() == null || N < 2) && !group.isBlocked()) {
                Collections.sort(channels, mChannelComparator);
                for (int i = 0; i < N; i++) {
                    final NotificationChannel channel = channels.get(i);
                    populateSingleChannelPrefs(groupCategory, channel, "");
                }
            } else {
                populateGroupPreference(groupCategory, group, N);
            }
        }
    }

    void populateGroupPreference(PreferenceGroup parent,
            final NotificationChannelGroup group, int channelCount) {
        MasterSwitchPreference groupPref = new MasterSwitchPreference(
                getPrefContext());
        groupPref.setSwitchEnabled(mSuspendedAppsAdmin == null
                && isChannelGroupBlockable(group));
        groupPref.setKey(group.getId());
        groupPref.setTitle(group.getName());
        groupPref.setChecked(!group.isBlocked());
        groupPref.setSummary(getResources().getQuantityString(
                R.plurals.notification_group_summary, channelCount, channelCount));
        Bundle groupArgs = new Bundle();
        groupArgs.putInt(AppInfoBase.ARG_PACKAGE_UID, mUid);
        groupArgs.putString(AppInfoBase.ARG_PACKAGE_NAME, mPkg);
        groupArgs.putString(Settings.EXTRA_CHANNEL_GROUP_ID, group.getId());
        Intent groupIntent = Utils.onBuildStartFragmentIntent(getActivity(),
                ChannelGroupNotificationSettings.class.getName(),
                groupArgs, null, R.string.notification_group_title, null, false,
                getMetricsCategory());
        groupPref.setIntent(groupIntent);

        groupPref.setOnPreferenceChangeListener(
                (preference, o) -> {
                    boolean value = (Boolean) o;
                    group.setBlocked(!value);
                    mBackend.updateChannelGroup(mPkg, mUid, group);

                    return true;
                });
        parent.addPreference(groupPref);
    }

    private Comparator<NotificationChannelGroup> mChannelGroupComparator =
            new Comparator<NotificationChannelGroup>() {

                @Override
                public int compare(NotificationChannelGroup left, NotificationChannelGroup right) {
                    // Non-grouped channels (in placeholder group with a null id) come last
                    if (left.getId() == null && right.getId() != null) {
                        return 1;
                    } else if (right.getId() == null && left.getId() != null) {
                        return -1;
                    }
                    return left.getId().compareTo(right.getId());
                }
            };
}
