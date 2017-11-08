/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.app.NotificationChannel.DEFAULT_CHANNEL_ID;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationManager.IMPORTANCE_UNSPECIFIED;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;

import com.android.settings.R;
import com.android.settings.TestConfig;
import com.android.settings.applications.LayoutPreference;
import com.android.settings.testutils.SettingsRobolectricTestRunner;
import com.android.settings.widget.SwitchBar;
import com.android.settings.wrapper.NotificationChannelGroupWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = Build.VERSION_CODES.O)
public class BlockPreferenceControllerTest {

    private Context mContext;
    @Mock
    private NotificationBackend mBackend;
    @Mock
    private NotificationManager mNm;
    @Mock
    private UserManager mUm;

    @Mock
    NotificationSettingsBase.ImportanceListener mImportanceListener;

    private BlockPreferenceController mController;
    @Mock
    private LayoutPreference mPreference;
    private SwitchBar mSwitch;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        shadowApplication.setSystemService(Context.NOTIFICATION_SERVICE, mNm);
        shadowApplication.setSystemService(Context.USER_SERVICE, mUm);
        mContext = shadowApplication.getApplicationContext();
        mController = spy(new BlockPreferenceController(mContext, mImportanceListener, mBackend));
        mSwitch = new SwitchBar(mContext);
        when(mPreference.findViewById(R.id.switch_bar)).thenReturn(mSwitch);
    }

    @Test
    public void testNoCrashIfNoOnResume() throws Exception {
        mController.isAvailable();
        mController.updateState(mock(LayoutPreference.class));
        mController.onSwitchChanged(null, false);
    }

    @Test
    public void testIsAvailable_notIfNull() throws Exception {
        mController.onResume(null, null, null, null);
        assertFalse(mController.isAvailable());
    }

    @Test
    public void testIsAvailable_notIfChannelNotBlockable() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.systemApp = true;
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.getImportance()).thenReturn(IMPORTANCE_HIGH);
        mController.onResume(appRow, channel, null, null);
        assertFalse(mController.isAvailable());
    }

    @Test
    public void testIsAvailable_notIfGroupNotBlockable() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.systemApp = true;
        mController.onResume(appRow, null, mock(NotificationChannelGroupWrapper.class), null);
        assertFalse(mController.isAvailable());
    }

    @Test
    public void testIsAvailable_notIfAppNotBlockable() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.systemApp = true;
        mController.onResume(appRow, null, null, null);
        assertFalse(mController.isAvailable());
    }

    @Test
    public void testIsAvailable_systemApp() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.systemApp = true;
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.getImportance()).thenReturn(IMPORTANCE_NONE);
        mController.onResume(appRow, channel, null, null);
        assertTrue(mController.isAvailable());
    }

    @Test
    public void testIsAvailable_nonSystemApp() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.systemApp = false;
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.getImportance()).thenReturn(IMPORTANCE_HIGH);
        mController.onResume(appRow, channel, null, null);
        assertTrue(mController.isAvailable());
    }

    @Test
    public void testUpdateState_app() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        appRow.banned = true;
        mController.onResume(appRow, null, null, null);
        mController.updateState(mPreference);

        assertNotNull(mPreference.findViewById(R.id.switch_bar));

        assertFalse(mSwitch.isChecked());

        appRow.banned = false;
        mController.onResume(appRow, null, null, null);
        mController.updateState(mPreference);

        assertTrue(mSwitch.isChecked());
    }

    @Test
    public void testUpdateState_group() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannelGroupWrapper group = mock(NotificationChannelGroupWrapper.class);
        when(group.getGroup()).thenReturn(mock(NotificationChannelGroup.class));
        when(group.isBlocked()).thenReturn(true);
        mController.onResume(appRow, null, group, null);
        mController.updateState(mPreference);

        assertFalse(mSwitch.isChecked());

        appRow.banned = true;
        mController.onResume(appRow, null, group, null);
        when(group.isBlocked()).thenReturn(true);
        mController.updateState(mPreference);

        assertFalse(mSwitch.isChecked());

        appRow.banned = false;
        mController.onResume(appRow, null, group, null);
        when(group.isBlocked()).thenReturn(false);
        mController.updateState(mPreference);

        assertTrue(mSwitch.isChecked());
    }

    @Test
    public void testUpdateState_channelBlocked() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannel channel = new NotificationChannel("a", "a", IMPORTANCE_NONE);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        assertFalse(mSwitch.isChecked());

        appRow.banned = true;
        channel = new NotificationChannel("a", "a", IMPORTANCE_HIGH);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        assertFalse(mSwitch.isChecked());

        appRow.banned = false;
        channel = new NotificationChannel("a", "a", IMPORTANCE_HIGH);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        assertTrue(mSwitch.isChecked());
    }

    @Test
    public void testUpdateState_noCrashIfCalledTwice() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannel channel = new NotificationChannel("a", "a", IMPORTANCE_LOW);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);
        mController.updateState(mPreference);
    }

    @Test
    public void testUpdateState_doesNotResetImportance() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannel channel = new NotificationChannel("a", "a", IMPORTANCE_LOW);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        assertEquals(IMPORTANCE_LOW, channel.getImportance());
    }

    @Test
    public void testOnSwitchChanged_channel_default() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannel channel =
                new NotificationChannel(DEFAULT_CHANNEL_ID, "a", IMPORTANCE_UNSPECIFIED);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        mController.onSwitchChanged(null, false);
        assertEquals(IMPORTANCE_NONE, channel.getImportance());

        mController.onSwitchChanged(null, true);
        assertEquals(IMPORTANCE_UNSPECIFIED, channel.getImportance());

        verify(mBackend, times(2)).updateChannel(any(), anyInt(), any());

    }

    @Test
    public void testOnSwitchChanged_channel_nonDefault() throws Exception {
        NotificationBackend.AppRow appRow = new NotificationBackend.AppRow();
        NotificationChannel channel = new NotificationChannel("a", "a", IMPORTANCE_HIGH);
        mController.onResume(appRow, channel, null, null);
        mController.updateState(mPreference);

        mController.onSwitchChanged(null, false);
        assertEquals(IMPORTANCE_NONE, channel.getImportance());

        mController.onSwitchChanged(null, true);
        assertEquals(IMPORTANCE_DEFAULT, channel.getImportance());

        verify(mBackend, times(2)).updateChannel(any(), anyInt(), any());
    }
}
