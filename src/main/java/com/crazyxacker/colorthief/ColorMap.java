package com.crazyxacker.colorthief;

import java.util.ArrayList;
import java.util.List;

/**
 * Color map
 */
public class ColorMap {
    public final List<MMCQ.ColorSpaceBox> boxes = new ArrayList<>();

    public void push(MMCQ.ColorSpaceBox box) {
        boxes.add(box);
    }

    public int[][] palette() {
        int numVBoxes = boxes.size();
        int[][] palette = new int[numVBoxes][];
        for (int i = 0; i < numVBoxes; i++) {
            palette[i] = boxes.get(i).avg(false);
        }
        return palette;
    }

    public int size() {
        return boxes.size();
    }

    public int[] map(int[] color) {
        for (MMCQ.ColorSpaceBox vbox : boxes) {
            if (vbox.contains(color)) {
                return vbox.avg(false);
            }
        }
        return nearest(color);
    }

    public int[] nearest(int[] color) {
        double d1 = Double.MAX_VALUE;
        double d2;
        int[] pColor = null;

        for (MMCQ.ColorSpaceBox box : boxes) {
            int[] vbColor = box.avg(false);
            d2 = Math
                    .sqrt(
                            Math.pow(color[0] - vbColor[0], 2)
                                    + Math.pow(color[1] - vbColor[1], 2)
                                    + Math.pow(color[2] - vbColor[2], 2));
            if (d2 < d1) {
                d1 = d2;
                pColor = vbColor;
            }
        }
        return pColor;
    }

}
