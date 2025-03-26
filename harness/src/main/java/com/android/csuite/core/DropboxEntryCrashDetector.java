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

import android.service.dropbox.DropBoxManagerServiceDumpProto;
import android.service.dropbox.DropBoxManagerServiceDumpProto.Entry;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A package crash detector based on dropbox entries. */
public class DropboxEntryCrashDetector {
    public static final Set<String> DROPBOX_APP_CRASH_TAGS =
            Set.of(
                    "SYSTEM_TOMBSTONE",
                    "system_app_anr",
                    "system_app_native_crash",
                    "system_app_crash",
                    "data_app_anr",
                    "data_app_native_crash",
                    "data_app_crash");
    private static final DateTimeFormatter DROPBOX_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss_SSS");
    // Pattern for finding a package name following one of the tags such as "Process:" or
    // "Package:".
    private static final Pattern DROPBOX_PACKAGE_NAME_PATTERN =
            Pattern.compile(
                    "(Process|Cmdline|Package|Cmd line):("
                            + " *)([a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");

    private final TempFileSupplier mTempFileSupplier;
    private final RunUtilProvider mRunUtilProvider;
    private final ITestDevice mDevice;

    /** Get an instance of a dropbox entry based crash detector */
    public static DropboxEntryCrashDetector getInstance(ITestDevice device) {
        return new DropboxEntryCrashDetector(
                device,
                () -> RunUtil.getDefault(),
                () -> Files.createTempFile(TestUtils.class.getName(), ".tmp"));
    }

    @VisibleForTesting
    DropboxEntryCrashDetector(
            ITestDevice device,
            RunUtilProvider runUtilProvider,
            TempFileSupplier tempFileSupplier) {
        mDevice = device;
        mRunUtilProvider = runUtilProvider;
        mTempFileSupplier = tempFileSupplier;
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @param packageName package name for filtering the entries. Can be null.
     * @param startTime entry start timestamp to filter the results. Can be null.
     * @param endTime entry end timestamp to filter the results. Can be null.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    public List<DropboxEntry> getDropboxEntries(
            Set<String> tags,
            String packageName,
            DeviceTimestamp startTime,
            DeviceTimestamp endTime)
            throws IOException {
        return getDropboxEntriesFromProtoDump(tags, packageName, startTime, endTime);
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    @VisibleForTesting
    List<DropboxEntry> getDropboxEntriesFromProtoDump(Set<String> tags) throws IOException {
        CommandResult resHelp =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                1L * 60 * 1000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell dumpsys dropbox --help",
                                        mDevice.getSerialNumber()));
        if (resHelp.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("Dropbox dump help command failed: " + resHelp);
        }
        if (!resHelp.getStdout().contains("--proto")) {
            // If dumping proto format is not supported such as in Android 10, the command will
            // still succeed with exit code 0 and output strings instead of protobuf bytes,
            // causing parse error. In this case we fallback to dumping dropbox --print option.
            return getDropboxEntriesFromStdout(tags);
        }

        List<DropboxEntry> entries = new ArrayList<>();

        for (String tag : tags) {
            Path dumpFile = mTempFileSupplier.get();

            CommandResult res =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    4L * 60 * 1000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s shell dumpsys dropbox --proto %s > %s",
                                            mDevice.getSerialNumber(), tag, dumpFile));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Dropbox dump command failed: " + res);
            }

            if (Files.size(dumpFile) == 0) {
                CLog.d("Skipping empty proto " + dumpFile);
                continue;
            }

            CLog.d("Parsing proto for tag %s. Size: %s", tag, Files.size(dumpFile));
            DropBoxManagerServiceDumpProto proto;
            try {
                proto = DropBoxManagerServiceDumpProto.parseFrom(Files.readAllBytes(dumpFile));
            } catch (InvalidProtocolBufferException e) {
                CLog.e(
                        "Falling back to stdout dropbox dump due to unexpected proto parse error:"
                                + " %s",
                        e);
                return getDropboxEntriesFromStdout(tags);
            }
            Files.delete(dumpFile);

            for (Entry entry : proto.getEntriesList()) {
                entries.add(
                        new DropboxEntry(entry.getTimeMs(), tag, entry.getData().toStringUtf8()));
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(DropboxEntry::getTime))
                .collect(Collectors.toList());
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @param packageName package name for filtering the entries. Can be null.
     * @param startTime entry start timestamp to filter the results. Can be null.
     * @param endTime entry end timestamp to filter the results. Can be null.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    @VisibleForTesting
    List<DropboxEntry> getDropboxEntriesFromProtoDump(
            Set<String> tags,
            String packageName,
            DeviceTimestamp startTime,
            DeviceTimestamp endTime)
            throws IOException {
        return getDropboxEntriesFromProtoDump(tags).stream()
                .filter(
                        entry ->
                                ((startTime == null || entry.getTime() >= startTime.get())
                                        && (endTime == null || entry.getTime() < endTime.get())))
                .filter(
                        entry ->
                                packageName == null
                                        || isDropboxEntryFromPackageProcess(
                                                entry.getData(), packageName))
                .collect(Collectors.toList());
    }

    /* Checks whether a dropbox entry is logged from the given package name. */
    @VisibleForTesting
    boolean isDropboxEntryFromPackageProcess(String entryData, String packageName) {
        Matcher m = DROPBOX_PACKAGE_NAME_PATTERN.matcher(entryData);

        boolean matched = false;
        while (m.find()) {
            matched = true;
            if (m.group(3).equals(packageName)) {
                return true;
            }
        }

        // Package/process name is identified but not equal to the packageName provided
        if (matched) {
            return false;
        }

        // If the process name is not identified, fall back to checking if the package name is
        // present in the entry. This is because the process name detection logic above does not
        // guarantee to identify the process name.
        return Pattern.compile(
                        String.format(
                                // Pattern for checking whether a given package name exists.
                                "(.*(?:[^a-zA-Z0-9_\\.]+)|^)%s((?:[^a-zA-Z0-9_\\.]+).*|$)",
                                packageName.replaceAll("\\.", "\\\\.")))
                .matcher(entryData)
                .find();
    }

    @VisibleForTesting
    List<DropboxEntry> getDropboxEntriesFromStdout(Set<String> tags) throws IOException {
        HashMap<String, DropboxEntry> entries = new HashMap<>();

        // The first step is to read the entry names and timestamps from the --file dump option
        // output because the --print dump option does not contain timestamps.
        CommandResult res;
        Path fileDumpFile = mTempFileSupplier.get();
        res =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                4L * 60 * 1000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell dumpsys dropbox --file  > %s",
                                        mDevice.getSerialNumber(), fileDumpFile));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("Dropbox dump command failed: " + res);
        }

        String lastEntryName = null;
        for (String line : Files.readAllLines(fileDumpFile)) {
            if (DropboxEntry.isDropboxEntryName(line)) {
                lastEntryName = line.trim();
                entries.put(lastEntryName, DropboxEntry.fromEntryName(line));
            } else if (DropboxEntry.isDropboxFilePath(line) && lastEntryName != null) {
                entries.get(lastEntryName).parseTimeFromFilePath(line);
            }
        }
        Files.delete(fileDumpFile);

        // Then we get the entry data from the --print dump output. Entry names parsed from the
        // --print dump output are verified against the entry names from the --file dump output to
        // ensure correctness.
        Path printDumpFile = mTempFileSupplier.get();
        res =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                4L * 60 * 1000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell dumpsys dropbox --print > %s",
                                        mDevice.getSerialNumber(), printDumpFile));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("Dropbox dump command failed: " + res);
        }

        lastEntryName = null;
        for (String line : Files.readAllLines(printDumpFile)) {
            if (DropboxEntry.isDropboxEntryName(line)) {
                lastEntryName = line.trim();
            }

            if (lastEntryName != null && entries.containsKey(lastEntryName)) {
                entries.get(lastEntryName).addData(line);
                entries.get(lastEntryName).addData("\n");
            }
        }
        Files.delete(printDumpFile);

        return entries.values().stream()
                .filter(entry -> tags.contains(entry.getTag()))
                .collect(Collectors.toList());
    }

    /** A class that stores the information of a dropbox entry. */
    public static final class DropboxEntry {
        private long mTime;
        private String mTag;
        private final StringBuilder mData = new StringBuilder();
        private static final Pattern ENTRY_NAME_PATTERN =
                Pattern.compile(
                        "\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2} .+ \\(.+, [0-9]+ .+\\)");
        private static final Pattern DATE_PATTERN =
                Pattern.compile("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        private static final Pattern FILE_NAME_PATTERN = Pattern.compile(" +/.+@[0-9]+\\..+");

        /** Returns the entrt's time stamp on device. */
        public long getTime() {
            return mTime;
        }

        private void addData(String data) {
            mData.append(data);
        }

        private void parseTimeFromFilePath(String input) {
            mTime = Long.parseLong(input.substring(input.indexOf('@') + 1, input.indexOf('.')));
        }

        /** Returns the entrt's tag. */
        public String getTag() {
            return mTag;
        }

        /** Returns the entrt's data. */
        public String getData() {
            return mData.toString();
        }

        @Override
        public String toString() {
            long time = getTime();
            String formattedTime =
                    DROPBOX_TIME_FORMATTER.format(
                            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
            return String.format(
                    "Dropbox entry tag: %s\n"
                            + "Dropbox entry timestamp: %s\n"
                            + "Dropbox entry time: %s\n%s",
                    getTag(), time, formattedTime, getData());
        }

        @VisibleForTesting
        DropboxEntry(long time, String tag, String data) {
            mTime = time;
            mTag = tag;
            addData(data);
        }

        private DropboxEntry() {
            // Intentionally left blank;
        }

        private static DropboxEntry fromEntryName(String name) {
            DropboxEntry entry = new DropboxEntry();
            Matcher matcher = DATE_PATTERN.matcher(name);
            if (!matcher.find()) {
                throw new RuntimeException("Unexpected entry name: " + name);
            }
            entry.mTag = name.trim().substring(matcher.group().length()).trim().split(" ")[0];
            return entry;
        }

        private static boolean isDropboxEntryName(String input) {
            return ENTRY_NAME_PATTERN.matcher(input).find();
        }

        private static boolean isDropboxFilePath(String input) {
            return FILE_NAME_PATTERN.matcher(input).find();
        }
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }

    @VisibleForTesting
    interface TempFileSupplier {
        Path get() throws IOException;
    }
}
