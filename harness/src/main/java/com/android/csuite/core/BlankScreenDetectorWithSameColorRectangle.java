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

import com.google.common.annotations.VisibleForTesting;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;

public class BlankScreenDetectorWithSameColorRectangle {

    // Returns the result of the analysis of whether the given image represents a blank screen.
    public static boolean hasBlankScreen(Path path) {
        // TODO(b/292125742) - implement logic to determine blank screen based on threshold.

        return false;
    }

    // Given an RGB image, finds the biggest same-color rectangle.
    @VisibleForTesting
    static Rectangle maxSameColorRectangle(BufferedImage image) {
        int[][] imageMatrix = getPixels(image);
        int[][] similarityMatrix = new int[imageMatrix.length][imageMatrix[0].length];
        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[0].length; j++) {
                similarityMatrix[i][j] = 0;
            }
        }
        Rectangle maxRectangle = new Rectangle();

        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[0].length; j++) {
                if (i == 0) {
                    similarityMatrix[i][j] = 1;
                } else if (imageMatrix[i][j] == imageMatrix[i - 1][j]) {
                    similarityMatrix[i][j] = similarityMatrix[i - 1][j] + 1;
                } else {
                    similarityMatrix[i][j] = 1;
                }
            }
            Rectangle currentBiggestRectangle = maxSubRectangle(similarityMatrix[i], i);
            if (getRectangleArea(currentBiggestRectangle) > getRectangleArea(maxRectangle)) {
                maxRectangle = currentBiggestRectangle;
            }
        }
        return maxRectangle;
    }

    // Finds the SubRectangle with the largest possible area given a row of column heights and its
    // index in a larger matrix.
    @VisibleForTesting
    static Rectangle maxSubRectangle(int[] heightsRow, int index) {
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        Rectangle maxRectangle = new Rectangle();

        for (int i = 0; i < heightsRow.length; i++) {
            while (!stack.isEmpty() && heightsRow[stack.peek()] > heightsRow[i]) {
                int height = heightsRow[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                int area = height * width;
                if (area > getRectangleArea(maxRectangle)) {
                    int leftCornerXCoord = stack.isEmpty() ? 0 : stack.peek() + 1;
                    int leftCornerYCoord = index - height + 1;
                    maxRectangle.setRect(leftCornerXCoord, leftCornerYCoord, width, height);
                }
            }
            stack.push(i);
        }

        while (!stack.isEmpty()) {
            int height = heightsRow[stack.pop()];
            int width =
                    stack.isEmpty() ? heightsRow.length : (heightsRow.length - stack.peek() - 1);
            int area = height * width;
            if (area > getRectangleArea(maxRectangle)) {
                int leftCornerXCoord = stack.isEmpty() ? 0 : stack.peek() + 1;
                int leftCornerYCoord = index - height + 1;
                maxRectangle.setRect(leftCornerXCoord, leftCornerYCoord, width, height);
            }
        }
        return maxRectangle;
    }

    // Converts a BufferedImage to a two-dimensional array containing the int representation of
    // its pixels.
    private static int[][] getPixels(BufferedImage image) {
        int[][] pixels = new int[image.getHeight()][image.getWidth()];
        for (int i = 0; i < pixels.length; i++) {
            for (int j = 0; j < pixels[0].length; j++) {
                pixels[i][j] = image.getRGB(j, i);
            }
        }
        return pixels;
    }

    private static long getRectangleArea(Rectangle rectangle) {
        return (long) rectangle.width * (long) rectangle.height;
    }
}
