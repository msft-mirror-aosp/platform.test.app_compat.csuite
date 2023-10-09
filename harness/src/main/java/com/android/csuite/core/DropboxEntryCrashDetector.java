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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.stream.Stream;

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
                    "\\b(Process|Cmdline|Package|Cmd line):("
                            + " *)([a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");

    private final TempDirectorySupplier mTempDirectorySupplier;
    private final RunUtilProvider mRunUtilProvider;
    private final ITestDevice mDevice;
    @VisibleForTesting static final String FILE_OUTPUT_NAME = "file-output.txt";
    @VisibleForTesting static final String PRINT_OUTPUT_NAME = "print-output.txt";
    @VisibleForTesting static final String DROPBOX_TAR_NAME = "dropbox.tar.gz";

    /** Get an instance of a dropbox entry based crash detector */
    public static DropboxEntryCrashDetector getInstance(ITestDevice device) {
        return new DropboxEntryCrashDetector(
                device,
                () -> RunUtil.getDefault(),
                () -> Files.createTempDirectory(TestUtils.class.getName()));
    }

    @VisibleForTesting
    DropboxEntryCrashDetector(
            ITestDevice device,
            RunUtilProvider runUtilProvider,
            TempDirectorySupplier tempDirectorySupplier) {
        mDevice = device;
        mRunUtilProvider = runUtilProvider;
        mTempDirectorySupplier = tempDirectorySupplier;
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
        // Will first attempt the adb pull method as it's most reliable and fast among all the
        // methods.
        List<DropboxEntry> entries = null;

        try {
            entries = getDropboxEntriesFromProtoDump(tags);
        } catch (IOException e) {
            // This method could fail when the data of dropbox is too large and the proto will
            // be truncated causing parse error.
            CLog.e(
                    "Falling back to adb pull method. Failed to get dropbox entries from proto"
                            + " dump: "
                            + e);
        }

        if (entries == null) {
            try {
                entries = getDropboxEntriesFromAdbPull(tags, startTime, endTime);
            } catch (IOException e) {
                // This method relies on a few compress and decompress tools on the host and the
                // device. It could fail if they aren't available.
                CLog.e(
                        "Falling back to text dump method. Failed to get dropbox entries from adb"
                                + " pull: "
                                + e);
            }
        }

        if (entries == null) {
            entries = getDropboxEntriesFromStdout();
        }

        return entries.stream()
                .filter(entry -> tags.contains(entry.getTag()))
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
    List<DropboxEntry> getDropboxEntriesFromAdbPull(
            Set<String> tags, DeviceTimestamp startTime, DeviceTimestamp endTime)
            throws IOException {
        List<DropboxEntry> entries = new ArrayList<>();

        CommandResult resLs =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                1L * 60 * 1000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell ls /data/system/dropbox/",
                                        mDevice.getSerialNumber()));
        if (resLs.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("tar command failed: " + resLs);
        }
        List<String> compressList = new ArrayList<>();
        for (String line : resLs.getStdout().split("\\s+")) {
            if (line.isEmpty()) {
                continue;
            }
            Path path = Path.of("/data/system/dropbox/").resolve(line);
            EntryFile entryFile = new EntryFile(path);
            if (tags.contains(entryFile.getTag())
                    && entryFile.getTime() >= startTime.get()
                    && entryFile.getTime() <= endTime.get()) {
                compressList.add(path.toString());
            }
        }

        if (compressList.isEmpty()) {
            return entries;
        }

        CommandResult resTar =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                1L * 60 * 1000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell tar -czf /data/local/tmp/%s %s",
                                        mDevice.getSerialNumber(),
                                        DROPBOX_TAR_NAME,
                                        String.join(" ", compressList)));
        if (resTar.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("tar command failed: " + resTar);
        }

        Path tmpDir = mTempDirectorySupplier.get();
        try {
            CommandResult resPull =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    1L * 60 * 1000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s pull /data/local/tmp/%s %s",
                                            mDevice.getSerialNumber(), DROPBOX_TAR_NAME, tmpDir));
            if (resPull.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Adb pull command failed: " + resPull);
            }

            mRunUtilProvider
                    .get()
                    .runTimedCmd(
                            1L * 60 * 1000,
                            "sh",
                            "-c",
                            String.format(
                                    "adb -s %s shell rm -rf /data/local/tmp/%s",
                                    mDevice.getSerialNumber(), DROPBOX_TAR_NAME));

            CommandResult resUntar =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    1L * 60 * 1000,
                                    "tar",
                                    "-xzf",
                                    tmpDir.resolve(DROPBOX_TAR_NAME).toString(),
                                    "-C",
                                    tmpDir.toString());
            if (resUntar.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Decompress command failed: " + resUntar);
            }

            Path dropboxDir = tmpDir.resolve("data/system/dropbox/");
            try (Stream<Path> originalEntryFiles = Files.list(dropboxDir)) {
                for (Path path : originalEntryFiles.collect(Collectors.toList())) {
                    EntryFile entryFile = new EntryFile(path);

                    String data = null;
                    switch (entryFile.getExtension()) {
                        case "txt.gz":
                            CommandResult resRead =
                                    mRunUtilProvider
                                            .get()
                                            .runTimedCmd(
                                                    1L * 60 * 1000,
                                                    "gunzip",
                                                    "-c",
                                                    path.toString());
                            if (resRead.getStatus() != CommandStatus.SUCCESS) {
                                throw new IOException("Decompress command failed: " + resRead);
                            }
                            data = resRead.getStdout();
                            break;
                        case "txt":
                            data = Files.readString(path, StandardCharsets.UTF_8);
                            break;
                        case "lost":
                        case "dat.gz":
                        default:
                            // Ignore
                    }
                    if (data == null) {
                        continue;
                    }
                    entries.add(new DropboxEntry(entryFile.getTime(), entryFile.getTag(), data));
                }
            }

        } finally {
            deleteDirectory(tmpDir);
        }

        return entries;
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
            throw new IOException(
                    "The current device doesn't support dumping dropbox entries in proto format.");
        }

        List<DropboxEntry> entries = new ArrayList<>();

        Path tmpDir = mTempDirectorySupplier.get();
        try {
            for (String tag : tags) {
                Path dumpFile = getProtoDumpFilePath(tmpDir, tag);
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
                DropBoxManagerServiceDumpProto proto =
                        DropBoxManagerServiceDumpProto.parseFrom(Files.readAllBytes(dumpFile));
                for (Entry entry : proto.getEntriesList()) {
                    entries.add(
                            new DropboxEntry(
                                    entry.getTimeMs(), tag, entry.getData().toStringUtf8()));
                }
            }
        } finally {
            deleteDirectory(tmpDir);
        }
        return entries.stream()
                .sorted(Comparator.comparing(DropboxEntry::getTime))
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    List<DropboxEntry> getDropboxEntriesFromStdout() throws IOException {
        HashMap<String, DropboxEntry> entries = new HashMap<>();

        // The first step is to read the entry names and timestamps from the --file dump option
        // output because the --print dump option does not contain timestamps.
        CommandResult res;
        Path tmpDir = mTempDirectorySupplier.get();
        try {
            res =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    4L * 60 * 1000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s shell dumpsys dropbox --file  > %s",
                                            mDevice.getSerialNumber(),
                                            tmpDir.resolve(FILE_OUTPUT_NAME)));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Dropbox dump command failed: " + res);
            }

            String lastEntryName = null;
            for (String line : Files.readAllLines(tmpDir.resolve(FILE_OUTPUT_NAME))) {
                if (DropboxEntry.isDropboxEntryName(line)) {
                    lastEntryName = line.trim();
                    entries.put(lastEntryName, DropboxEntry.fromEntryName(line));
                } else if (DropboxEntry.isDropboxFilePath(line) && lastEntryName != null) {
                    entries.get(lastEntryName).parseTimeFromFilePath(line);
                }
            }

            // Then we get the entry data from the --print dump output. Entry names parsed from the
            // --print dump output are verified against the entry names from the --file dump output
            // to
            // ensure correctness.
            res =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    4L * 60 * 1000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s shell dumpsys dropbox --print > %s",
                                            mDevice.getSerialNumber(),
                                            tmpDir.resolve(PRINT_OUTPUT_NAME)));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Dropbox dump command failed: " + res);
            }

            lastEntryName = null;
            for (String line : Files.readAllLines(tmpDir.resolve(PRINT_OUTPUT_NAME))) {
                if (DropboxEntry.isDropboxEntryName(line)) {
                    lastEntryName = line.trim();
                }

                if (lastEntryName != null && entries.containsKey(lastEntryName)) {
                    entries.get(lastEntryName).addData(line);
                    entries.get(lastEntryName).addData("\n");
                }
            }
        } finally {
            deleteDirectory(tmpDir);
        }

        return new ArrayList<>(entries.values());
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        if (exc == null) {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        } else {
                            throw exc;
                        }
                    }
                });
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

    private static class EntryFile {
        private String mTag;
        private long mTime;
        private String mExtension;

        private EntryFile(Path path) throws IOException {
            String fileName = path.getFileName().toString();
            int idxAt = fileName.indexOf('@');
            int idxDot = fileName.indexOf('.');
            if (idxAt <= 0 || idxDot <= 0) {
                throw new IOException("Unrecognized dropbox entry file name " + path);
            }
            mTag = fileName.substring(0, idxAt);
            try {
                mTime = Long.parseLong(fileName.substring(idxAt + 1, idxDot));
            } catch (NumberFormatException e) {
                throw new IOException(e);
            }

            mExtension = fileName.substring(idxDot + 1);
        }

        private String getTag() {
            return mTag;
        }

        private long getTime() {
            return mTime;
        }

        private String getExtension() {
            return mExtension;
        }
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }

    @VisibleForTesting
    interface TempDirectorySupplier {
        Path get() throws IOException;
    }

    @VisibleForTesting
    static Path getProtoDumpFilePath(Path dir, String tag) {
        return dir.resolve(tag + ".proto");
    }
}
