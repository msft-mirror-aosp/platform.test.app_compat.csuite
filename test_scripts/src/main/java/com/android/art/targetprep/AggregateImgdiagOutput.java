/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.art.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.ITargetPreparer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Collect and parse imgdiag output files. */
public class AggregateImgdiagOutput implements ITestLoggerReceiver, ITargetPreparer {
    @Option(
            name = "imgdiag-out-path",
            description = "Path to directory containing imgdiag output files.")
    private String mImgdiagOutPath;

    private ITestLogger mTestLogger;

    // Imgdiag outputs this string when a dirty object is unreachable from any Class.
    private static final String UNREACHABLE_OBJECT = "<no path from class>";

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    @Override
    public void setUp(TestInformation testInformation) {}

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {

        ImgdiagData imgdiagData = collectImgdiagData(testInformation);
        createDirtyImageObjects(imgdiagData.dirtyObjects);
        dumpDirtyObjects(imgdiagData.dirtyObjects);

        mTestLogger.testLog(
                "dirty-page-counts",
                LogDataType.JSON,
                new ByteArrayInputStreamSource(
                        new JSONObject(imgdiagData.dirtyPageCounts).toString().getBytes()));
    }

    void dumpDirtyObjects(Map<String, Set<String>> dirtyObjects) {
        JSONObject jsonObj = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : dirtyObjects.entrySet()) {
            try {
                jsonObj.put(entry.getKey(), new JSONArray(entry.getValue()));
            } catch (JSONException e) {
                Assert.fail(e.getMessage());
            }
        }

        mTestLogger.testLog(
                "all-dirty-objects",
                LogDataType.JSON,
                new ByteArrayInputStreamSource(jsonObj.toString().getBytes()));
    }

    static class ImgdiagData {
        // "process name" -> set of dirty objects.
        public Map<String, Set<String>> dirtyObjects;
        // "process name" -> dirty page count in ObjectSection.
        public Map<String, Integer> dirtyPageCounts;

        ImgdiagData(Map<String, Set<String>> dirtyObjects, Map<String, Integer> dirtyPageCounts) {
            this.dirtyObjects = dirtyObjects;
            this.dirtyPageCounts = dirtyPageCounts;
        }
    }

    ImgdiagData collectImgdiagData(TestInformation testInformation)
            throws DeviceNotAvailableException {
        Assert.assertTrue(testInformation.getDevice().doesFileExist(mImgdiagOutPath));

        Pattern imgdiagOutRegex = Pattern.compile("imgdiag_(\\S+_\\d+)\\.txt");
        String dirtyObjPrefix = "dirty_obj:";
        String dirtyPageCountPrefix = "SectionObjects";

        Map<String, Set<String>> dirtyObjects = new HashMap<String, Set<String>>();
        Map<String, Integer> dirtyPageCounts = new HashMap<String, Integer>();

        IFileEntry deviceImgdiagOutDir = testInformation.getDevice().getFileEntry(mImgdiagOutPath);
        for (IFileEntry child : deviceImgdiagOutDir.getChildren(false)) {
            Matcher m = imgdiagOutRegex.matcher(child.getName());
            if (!m.matches()) {
                continue;
            }

            String key = m.group(1);
            String fileContents = testInformation.getDevice().pullFileContents(child.getFullPath());

            // Get the number after the last '=' sign, e.g.:
            // SectionObjects size=9607584 range=0-9607584 private dirty pages=140
            Optional<String> dirtyPageCount =
                    fileContents
                            .lines()
                            .filter(line -> line.startsWith(dirtyPageCountPrefix))
                            .findFirst()
                            .map(line -> line.split("="))
                            .map(tokens -> tokens[tokens.length - 1]);

            // Can only happen if imgdiag output is empty, skip this file.
            if (!dirtyPageCount.isPresent()) {
                continue;
            }
            dirtyPageCounts.put(key, Integer.valueOf(dirtyPageCount.get()));

            Set<String> procDirtyObjects =
                    fileContents
                            .lines()
                            .filter(line -> line.startsWith(dirtyObjPrefix))
                            .map(line -> line.substring(dirtyObjPrefix.length()).strip())
                            .collect(Collectors.toSet());

            dirtyObjects.put(key, procDirtyObjects);
        }

        return new ImgdiagData(dirtyObjects, dirtyPageCounts);
    }

    // Sort dirty objects, split by dex location and upload.
    void createDirtyImageObjects(Map<String, Set<String>> dirtyObjects) {
        Map<BigInteger, Set<String>> sortKeys = generateSortKeys(dirtyObjects);
        List<Set<String>> sortedObjs = sortDirtyObjects(sortKeys, dirtyObjects);
        appendSortKeys(sortedObjs);

        List<String> resObjects =
                sortedObjs.stream().flatMap(Collection::stream).collect(Collectors.toList());
        Map<String, List<String>> splitDirtyObjects = splitByDexLocation(resObjects);

        for (Map.Entry<String, List<String>> entry : splitDirtyObjects.entrySet()) {
            mTestLogger.testLog(
                    "dirty-image-objects-" + entry.getKey(),
                    LogDataType.TEXT,
                    new ByteArrayInputStreamSource(String.join("\n", entry.getValue()).getBytes()));
        }
    }

    // Calculate a Map of dirty objects in the format: sortKey -> [objects].
    // Each sortKey is a bit mask, where the Nth bit is set if the given object
    // is dirty in the Nth process.
    static Map<BigInteger, Set<String>> generateSortKeys(Map<String, Set<String>> dirtyObjects) {
        Collection<String> allObjects =
                dirtyObjects.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());

        Map<BigInteger, Set<String>> sortKeys = new HashMap<BigInteger, Set<String>>();
        for (String dirtyObj : allObjects) {
            // Skip unreachable objects.
            if (dirtyObj.equals(UNREACHABLE_OBJECT)) {
                continue;
            }

            // Generate sort key for dirty object.
            // Go through each process and set corresponding bit to '1' if
            // the object is dirty in that process.
            BigInteger sortKey = BigInteger.ZERO;
            for (Collection<String> procDirtyObjects : dirtyObjects.values()) {
                sortKey = sortKey.shiftLeft(1);
                if (procDirtyObjects.contains(dirtyObj)) {
                    sortKey = sortKey.or(BigInteger.ONE);
                }
            }

            // Put dirty objects with the same sortKey together.
            sortKeys.computeIfAbsent(sortKey, k -> new HashSet<String>());
            sortKeys.get(sortKey).add(dirtyObj);
        }

        return sortKeys;
    }

    // Calculate similarity using intersection divided by union.
    static float jaccardIndex(BigInteger k1, BigInteger k2) {
        return (float) k1.and(k2).bitCount() / (float) k1.or(k2).bitCount();
    }

    // Compare two keys by how similar they are to the base key.
    static int similarityCompare(BigInteger base, BigInteger k1, BigInteger k2) {
        return Float.compare(jaccardIndex(base, k1), jaccardIndex(base, k2));
    }

    // Sorty dirty objects so that objects with similar "dirtiness" pattern
    // are placed next to each other.
    List<Set<String>> sortDirtyObjects(
            Map<BigInteger, Set<String>> sortKeys, Map<String, Set<String>> dirtyObjects) {
        List<Set<String>> sortedObjs = new ArrayList<Set<String>>();

        // Start with an entry that is dirty in a few processes.
        Map.Entry<BigInteger, Set<String>> minEntry =
                sortKeys.entrySet().stream()
                        .min(
                                (e1, e2) ->
                                        Arrays.compare(
                                                new int[] {
                                                    e1.getKey().bitCount(), e1.getValue().size()
                                                },
                                                new int[] {
                                                    e2.getKey().bitCount(), e2.getValue().size()
                                                }))
                        .get();

        BigInteger lastKey = minEntry.getKey();
        sortedObjs.add(minEntry.getValue());
        sortKeys.remove(minEntry.getKey());

        // String representation of sortKey bits.
        // Helps to check that objects with similar sort keys are placed
        // together.
        List<String> dbgSortKeys = new ArrayList<String>();
        dbgSortKeys.add(
                String.format(
                        "%" + dirtyObjects.size() + "s %s",
                        lastKey.toString(2),
                        sortedObjs.get(sortedObjs.size() - 1).size()));

        while (!sortKeys.isEmpty()) {
            final BigInteger currentKey = lastKey;
            // Select next entry that has a key most similar to currentKey.
            Map.Entry<BigInteger, Set<String>> nextEntry =
                    sortKeys.entrySet().stream()
                            .max(
                                    (e1, e2) ->
                                            similarityCompare(currentKey, e1.getKey(), e2.getKey()))
                            .get();

            lastKey = nextEntry.getKey();
            sortedObjs.add(nextEntry.getValue());
            sortKeys.remove(nextEntry.getKey());

            dbgSortKeys.add(
                    String.format(
                            "%" + dirtyObjects.size() + "s %s",
                            lastKey.toString(2),
                            sortedObjs.get(sortedObjs.size() - 1).size()));
        }

        mTestLogger.testLog(
                "dbg-sort-keys",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(String.join("\n", dbgSortKeys).getBytes()));

        return sortedObjs;
    }

    static void appendSortKeys(List<Set<String>> sortedObjs) {
        for (int i = 0; i < sortedObjs.size(); i += 1) {
            final int sortIndex = i;
            sortedObjs.set(
                    i,
                    sortedObjs.get(i).stream()
                            .map(obj -> String.format("%s %s", obj, sortIndex))
                            .collect(Collectors.toSet()));
        }
    }

    static boolean isArtModuleObject(String dexLocation) {
        return dexLocation.startsWith("/apex/com.android.art/");
    }

    static boolean isPrimitiveArray(String dexLocation) {
        return dexLocation.startsWith("primitive");
    }

    static Map<String, List<String>> splitByDexLocation(List<String> objects) {
        Map<String, List<String>> res = new HashMap<String, List<String>>();
        res.put("art", new ArrayList<String>());
        res.put("framework", new ArrayList<String>());
        for (String entry : objects) {
            String[] pathAndObj = entry.split(" ", 2);
            String dexLocation = pathAndObj[0];
            String obj = pathAndObj[1];

            if (isArtModuleObject(dexLocation) || isPrimitiveArray(dexLocation)) {
                res.get("art").add(obj);
            } else {
                res.get("framework").add(obj);
            }
        }
        return res;
    }
}
