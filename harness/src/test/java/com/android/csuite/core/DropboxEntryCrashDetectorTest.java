/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.service.dropbox.DropBoxManagerServiceDumpProto;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DropboxEntryCrashDetector.DropboxEntry;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DropboxEntryCrashDetectorTest {
    private ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private static final String TEST_PACKAGE_NAME = "package.name";

    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void isDropboxEntryFromPackageProcess_notStartOfLine_returnsFalse() throws Exception {
        String dropboxEntryData = "\nPackage: gms.package\ncallingPackage: com.app.package \n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_cmdlineMatched_returnsTrue() throws Exception {
        String dropboxEntryData = "Cmd line: com.app.package";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processMatched_returnsTrue() throws Exception {
        String dropboxEntryData = "Process: com.app.package";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processMatchedInLines_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package\nline";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByOtherChar_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package, (time)\nline";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByDot_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package.sub, (time)\nline";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByColon_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package:sub, (time)\nline";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByUnderscore_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package_sub, (time)\nline";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_doesNotContainPackageName_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithUnderscorePrefix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\na_com.app.package\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithUnderscorePostfix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package_a\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithDotPrefix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\na.com.app.package\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithDotPostfix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package.a\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithColonPostfix_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package:a\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void
            isDropboxEntryFromPackageProcess_packageNameWithAcceptiblePrefixAndPostfix_returnsTrue()
                    throws Exception {
        String dropboxEntryData = "line\ncom.app.package)\n";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void
            isDropboxEntryFromPackageProcess_wrongProcessNameWithCorrectPackageName_returnsFalse()
                    throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package_other\ncom.app.package";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_MultipleProcessNamesWithOneMatching_returnsTrue()
            throws Exception {
        String dropboxEntryData =
                "line\n"
                        + "Process: com.app.package_other\n"
                        + "Process: com.app.package\n"
                        + "Process: com.other";
        String packageName = "com.app.package";
        DropboxEntryCrashDetector sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void getDropboxEntriesFromAdbPull_noEntriesWithinTimeRange_returnsEmpty()
            throws Exception {
        DropboxEntryCrashDetector sut = createSubjectUnderTest();
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("ls /data/system/dropbox")))
                .thenReturn(createSuccessfulCommandResultWithStdout("tag@1.txt"));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("shell tar")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("pull /data/local/tmp")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("tar"),
                        Mockito.eq("-xzf"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> entries =
                sut.getDropboxEntriesFromAdbPull(
                        Set.of("tag"), new DeviceTimestamp(2), new DeviceTimestamp(3));

        assertThat(entries).isEmpty();
    }

    @Test
    public void getDropboxEntriesFromAdbPull_noEntriesUnderTag_returnsEmpty() throws Exception {
        DropboxEntryCrashDetector sut = createSubjectUnderTest();
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("ls /data/system/dropbox")))
                .thenReturn(createSuccessfulCommandResultWithStdout("tag2@2.txt"));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("shell tar")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("pull /data/local/tmp")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("tar"),
                        Mockito.eq("-xzf"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> entries =
                sut.getDropboxEntriesFromAdbPull(
                        Set.of("tag1"), new DeviceTimestamp(1), new DeviceTimestamp(3));

        assertThat(entries).isEmpty();
    }

    @Test
    public void getDropboxEntriesFromAdbPull_entryMatched_returnsEntry() throws Exception {
        String tag = "tag";
        long time = 2;
        String data = "content";
        Path tmpDir = mFileSystem.getPath("tmp");
        Files.createDirectories(tmpDir);
        Path dumpFile = tmpDir.resolve("data/system/dropbox/" + tag + "@" + time + ".txt");
        Files.createDirectories(dumpFile.getParent());
        Files.createFile(dumpFile);
        Files.writeString(dumpFile, data);
        DropboxEntryCrashDetector sut = createSubjectUnderTestWithTempDirectory(tmpDir);
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("ls /data/system/dropbox")))
                .thenReturn(createSuccessfulCommandResultWithStdout(tag + "@" + time + ".txt"));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("shell tar")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("pull /data/local/tmp")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("tar"),
                        Mockito.eq("-xzf"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> entries =
                sut.getDropboxEntriesFromAdbPull(
                        Set.of(tag), new DeviceTimestamp(time - 1), new DeviceTimestamp(time + 1));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTag()).isEqualTo(tag);
        assertThat(entries.get(0).getTime()).isEqualTo(time);
        assertThat(entries.get(0).getData()).isEqualTo(data);
    }

    @Test
    public void
            getDropboxEntriesFromProtoDump_containsEntriesOutsideTimeRange_onlyReturnsNewEntries()
                    throws Exception {
        DropboxEntryCrashDetector sut = Mockito.spy(createSubjectUnderTest());
        DeviceTimestamp startTime = new DeviceTimestamp(1);
        DeviceTimestamp endTime = new DeviceTimestamp(3);
        Mockito.doThrow(new IOException())
                .when(sut)
                .getDropboxEntriesFromAdbPull(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(
                        inv ->
                                List.of(
                                        new DropboxEntryCrashDetector.DropboxEntry(
                                                0,
                                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DropboxEntryCrashDetector
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry1"),
                                        new DropboxEntryCrashDetector.DropboxEntry(
                                                2,
                                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DropboxEntryCrashDetector
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry2"),
                                        new DropboxEntryCrashDetector.DropboxEntry(
                                                100,
                                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DropboxEntryCrashDetector
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry3")))
                .when(sut)
                .getDropboxEntriesFromProtoDump(DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS);

        String result =
                sut
                        .getDropboxEntries(
                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS,
                                TEST_PACKAGE_NAME,
                                startTime,
                                endTime)
                        .stream()
                        .map(DropboxEntry::toString)
                        .collect(Collectors.joining("\n"));

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
        assertThat(result).doesNotContain("entry3");
    }

    @Test
    public void
            getDropboxEntriesFromProtoDump_containsOtherProcessEntries_onlyReturnsPackageEntries()
                    throws Exception {
        DropboxEntryCrashDetector sut = Mockito.spy(createSubjectUnderTest());
        DeviceTimestamp startTime = new DeviceTimestamp(1);
        Mockito.doThrow(new IOException())
                .when(sut)
                .getDropboxEntriesFromAdbPull(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(
                        inv ->
                                List.of(
                                        new DropboxEntryCrashDetector.DropboxEntry(
                                                2,
                                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DropboxEntryCrashDetector
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                "other.package" + " entry1"),
                                        new DropboxEntryCrashDetector.DropboxEntry(
                                                2,
                                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DropboxEntryCrashDetector
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry2")))
                .when(sut)
                .getDropboxEntriesFromProtoDump(DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS);

        String result =
                sut
                        .getDropboxEntries(
                                DropboxEntryCrashDetector.DROPBOX_APP_CRASH_TAGS,
                                TEST_PACKAGE_NAME,
                                startTime,
                                null)
                        .stream()
                        .map(DropboxEntry::toString)
                        .collect(Collectors.joining("\n"));

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
    }

    @Test
    public void getDropboxEntriesFromProtoDump_noEntries_returnsEmptyList() throws Exception {
        String tag = "tag";
        Path tmpDir = mFileSystem.getPath("tmp");
        Files.createDirectories(tmpDir);
        Path dumpFile = DropboxEntryCrashDetector.getProtoDumpFilePath(tmpDir, tag);
        Files.createFile(dumpFile);
        DropboxEntryCrashDetector sut = createSubjectUnderTestWithTempDirectory(tmpDir);
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --help")))
                .thenReturn(createSuccessfulCommandResultWithStdout("--proto"));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --proto")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntriesFromProtoDump(Set.of(tag));

        assertThat(result).isEmpty();
    }

    @Test
    public void getDropboxEntriesFromProtoDump_entryExists_returnsEntry() throws Exception {
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --help")))
                .thenReturn(createSuccessfulCommandResultWithStdout("--proto"));
        long time = 123;
        String data = "abc";
        String tag = "tag";
        Path tmpDir = mFileSystem.getPath("tmp");
        Files.createDirectories(tmpDir);
        Path dumpFile = DropboxEntryCrashDetector.getProtoDumpFilePath(tmpDir, tag);
        Files.createFile(dumpFile);
        DropBoxManagerServiceDumpProto proto =
                DropBoxManagerServiceDumpProto.newBuilder()
                        .addEntries(
                                DropBoxManagerServiceDumpProto.Entry.newBuilder()
                                        .setTimeMs(time)
                                        .setData(ByteString.copyFromUtf8(data)))
                        .build();
        Files.write(dumpFile, proto.toByteArray());
        DropboxEntryCrashDetector sut = createSubjectUnderTestWithTempDirectory(tmpDir);
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --proto")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntriesFromProtoDump(Set.of(tag));

        assertThat(result.get(0).getTime()).isEqualTo(time);
        assertThat(result.get(0).getData()).isEqualTo(data);
        assertThat(result.get(0).getTag()).isEqualTo(tag);
    }

    @Test
    public void getDropboxEntriesFromStdout_entryExists_returnsEntry() throws Exception {
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --file")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --print")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        Path tmpDir = mFileSystem.getPath("tmp");
        Files.createDirectories(tmpDir);
        Path fileDumpFile = tmpDir.resolve(DropboxEntryCrashDetector.FILE_OUTPUT_NAME);
        Files.createFile(fileDumpFile);
        Path printDumpFile = tmpDir.resolve(DropboxEntryCrashDetector.PRINT_OUTPUT_NAME);
        Files.createFile(printDumpFile);
        String fileResult =
                "Drop box contents: 351 entries\n"
                        + "Max entries: 1000\n"
                        + "Low priority rate limit period: 2000 ms\n"
                        + "Low priority tags: {data_app_wtf, keymaster, system_server_wtf,"
                        + " system_app_strictmode, system_app_wtf, system_server_strictmode,"
                        + " data_app_strictmode, netstats}\n"
                        + "\n"
                        + "2022-09-05 04:17:21 system_server_wtf (text, 1730 bytes)\n"
                        + "    /data/system/dropbox/system_server_wtf@1662351441269.txt\n"
                        + "2022-09-05 04:31:06 event_data (text, 39 bytes)\n"
                        + "    /data/system/dropbox/event_data@1662352266197.txt\n";
        String printResult =
                "Drop box contents: 351 entries\n"
                    + "Max entries: 1000\n"
                    + "Low priority rate limit period: 2000 ms\n"
                    + "Low priority tags: {data_app_wtf, keymaster, system_server_wtf,"
                    + " system_app_strictmode, system_app_wtf, system_server_strictmode,"
                    + " data_app_strictmode, netstats}\n"
                    + "\n"
                    + "========================================\n"
                    + "2022-09-05 04:17:21 system_server_wtf (text, 1730 bytes)\n"
                    + "Process: system_server\n"
                    + "Subject: ActivityManager\n"
                    + "Build:"
                    + " generic/cf_x86_64_phone/vsoc_x86_64:UpsideDownCake/MASTER/8990215:userdebug/dev-keys\n"
                    + "Dropped-Count: 0\n"
                    + "\n"
                    + "android.util.Log$TerribleFailure: Sending non-protected broadcast"
                    + " com.android.bluetooth.btservice.BLUETOOTH_COUNTER_METRICS_ACTION from"
                    + " system uid 1002 pkg com.android.bluetooth\n"
                    + "    at android.util.Log.wtf(Log.java:332)\n"
                    + "    at android.util.Log.wtf(Log.java:326)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.checkBroadcastFromSystem(ActivityManagerService.java:13609)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.broadcastIntentLocked(ActivityManagerService.java:14330)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.broadcastIntentInPackage(ActivityManagerService.java:14530)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService$LocalService.broadcastIntentInPackage(ActivityManagerService.java:17065)\n"
                    + "    at"
                    + " com.android.server.am.PendingIntentRecord.sendInner(PendingIntentRecord.java:526)\n"
                    + "    at"
                    + " com.android.server.am.PendingIntentRecord.sendWithResult(PendingIntentRecord.java:311)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.sendIntentSender(ActivityManagerService.java:5379)\n"
                    + "    at"
                    + " android.app.PendingIntent.sendAndReturnResult(PendingIntent.java:1012)\n"
                    + "    at android.app.PendingIntent.send(PendingIntent.java:983)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService$DeliveryTracker.deliverLocked(AlarmManagerService.java:5500)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService.deliverAlarmsLocked(AlarmManagerService.java:4400)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService$AlarmThread.run(AlarmManagerService.java:4711)\n"
                    + "Caused by: java.lang.Throwable\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.checkBroadcastFromSystem(ActivityManagerService.java:13610)\n"
                    + "    ... 11 more\n"
                    + "\n"
                    + "========================================\n"
                    + "2022-09-05 04:31:06 event_data (text, 39 bytes)\n"
                    + "start=1662350731248\n"
                    + "end=1662352266140\n"
                    + "\n";
        Files.write(fileDumpFile, fileResult.getBytes());
        Files.write(printDumpFile, printResult.getBytes());
        DropboxEntryCrashDetector sut =
                Mockito.spy(createSubjectUnderTestWithTempDirectory(tmpDir));
        Mockito.doThrow(new IOException())
                .when(sut)
                .getDropboxEntriesFromAdbPull(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doThrow(new IOException()).when(sut).getDropboxEntriesFromProtoDump(Mockito.any());

        List<DropboxEntry> result =
                sut.getDropboxEntries(
                        Set.of("system_server_wtf"),
                        "system_server",
                        new DeviceTimestamp(Long.MIN_VALUE),
                        new DeviceTimestamp(Long.MAX_VALUE));

        assertThat(result.get(0).getTime()).isEqualTo(1662351441269L);
        assertThat(result.get(0).getData()).contains("Sending non-protected broadcast");
        assertThat(result.get(0).getTag()).isEqualTo("system_server_wtf");
        assertThat(result.size()).isEqualTo(1);
    }

    private DropboxEntryCrashDetector createSubjectUnderTestWithTempDirectory(Path dir) {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        return new DropboxEntryCrashDetector(mDevice, () -> mRunUtil, () -> dir);
    }

    private DropboxEntryCrashDetector createSubjectUnderTest() throws DeviceNotAvailableException {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo ${EPOCHREALTIME")))
                .thenReturn(createSuccessfulCommandResultWithStdout("1"));
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("34"));
        return new DropboxEntryCrashDetector(
                mDevice,
                () -> mRunUtil,
                () -> Files.createTempFile(mFileSystem.getPath("/"), "test", ".tmp"));
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }
}
