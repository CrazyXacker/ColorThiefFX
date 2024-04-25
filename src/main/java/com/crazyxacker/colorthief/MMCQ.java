/*
 * Java Color Thief
 * by Sven Woltmann, Fonpit AG
 * 
 * https://www.androidpit.com
 * https://www.androidpit.de
 *
 * License
 * -------
 * Creative Commons Attribution 2.5 License:
 * http://creativecommons.org/licenses/by/2.5/
 *
 * Thanks
 * ------
 * Lokesh Dhakar - for the original Color Thief JavaScript version
 * available at http://lokeshdhakar.com/projects/color-thief/
 */

package com.crazyxacker.colorthief;

import java.util.*;

class MMCQ {

    private static final int SIGBITS = 5;
    private static final int RSHIFT = 8 - SIGBITS;
    private static final int MULT = 1 << RSHIFT;
    private static final int HISTOSIZE = 1 << (3 * SIGBITS);
    private static final int VBOX_LENGTH = 1 << SIGBITS;
    private static final double FRACT_BY_POPULATION = 0.75;
    private static final int MAX_ITERATIONS = 1000;

    /**
     * Get reduced-space color index for a pixel.
     * 
     * @param r
     *            the red value
     * @param g
     *            the green value
     * @param b
     *            the blue value
     * 
     * @return the color index
     */
    static int getColorIndex(int r, int g, int b) {
        return (r << (2 * SIGBITS)) + (g << SIGBITS) + b;
    }

    /**
     * 3D color space box.
     */
    public static class ColorSpaceBox {
        int r1;
        int r2;
        int g1;
        int g2;
        int b1;
        int b2;

        private final int[] histo;

        private int[] _avg;
        private Integer _volume;
        private Integer _count;

        public ColorSpaceBox(int r1, int r2, int g1, int g2, int b1, int b2, int[] histo) {
            this.r1 = r1;
            this.r2 = r2;
            this.g1 = g1;
            this.g2 = g2;
            this.b1 = b1;
            this.b2 = b2;

            this.histo = histo;
        }

        @Override
        public String toString() {
            return "r1: " + r1 + " / r2: " + r2 + " / g1: " + g1 + " / g2: " + g2 + " / b1: " + b1 + " / b2: " + b2;
        }

        public int volume(boolean force) {
            if (_volume == null || force) {
                _volume = ((r2 - r1 + 1) * (g2 - g1 + 1) * (b2 - b1 + 1));
            }

            return _volume;
        }

        public int count(boolean force) {
            if (_count == null || force) {
                int npix = 0;
                int i, j, k, index;

                for (i = r1; i <= r2; i++) {
                    for (j = g1; j <= g2; j++) {
                        for (k = b1; k <= b2; k++) {
                            index = getColorIndex(i, j, k);
                            npix += histo[index];
                        }
                    }
                }

                _count = npix;
            }

            return _count;
        }

        @Override
        public ColorSpaceBox clone() {
            return new ColorSpaceBox(r1, r2, g1, g2, b1, b2, histo);
        }

        public int[] avg(boolean force) {
            if (_avg == null || force) {
                int ntot = 0;

                int rsum = 0;
                int gsum = 0;
                int bsum = 0;

                int hval, i, j, k, histoindex;

                for (i = r1; i <= r2; i++) {
                    for (j = g1; j <= g2; j++) {
                        for (k = b1; k <= b2; k++) {
                            histoindex = getColorIndex(i, j, k);
                            hval = histo[histoindex];
                            ntot += hval;
                            rsum += (hval * (i + 0.5) * MULT);
                            gsum += (hval * (j + 0.5) * MULT);
                            bsum += (hval * (k + 0.5) * MULT);
                        }
                    }
                }

                if (ntot > 0) {
                    _avg = new int[] {~~(rsum / ntot), ~~(gsum / ntot), ~~(bsum / ntot)};
                } else {
                    _avg = new int[] {~~(MULT * (r1 + r2 + 1) / 2), ~~(MULT * (g1 + g2 + 1) / 2), ~~(MULT * (b1 + b2 + 1) / 2)};
                }
            }

            return _avg;
        }

        public boolean contains(int[] pixel) {
            int rval = pixel[0] >> RSHIFT;
            int gval = pixel[1] >> RSHIFT;
            int bval = pixel[2] >> RSHIFT;

            return (rval >= r1 && rval <= r2 && gval >= g1 && gval <= g2 && bval >= b1
                    && bval <= b2);
        }
    }

    /**
     * Histo (1-d array, giving the number of pixels in each quantized region of color space), or
     * null on error.
     */
    private static int[] getHisto(int[][] pixels) {
        int[] histo = new int[HISTOSIZE];
        int index, rval, gval, bval;

        for (int[] pixel : pixels) {
            rval = pixel[0] >> RSHIFT;
            gval = pixel[1] >> RSHIFT;
            bval = pixel[2] >> RSHIFT;
            index = getColorIndex(rval, gval, bval);
            histo[index]++;
        }
        return histo;
    }

    private static ColorSpaceBox vboxFromPixels(int[][] pixels, int[] histo) {
        int rmin = 1000000, rmax = 0;
        int gmin = 1000000, gmax = 0;
        int bmin = 1000000, bmax = 0;

        int rval, gval, bval;

        // find min/max
        for (int[] pixel : pixels) {
            rval = pixel[0] >> RSHIFT;
            gval = pixel[1] >> RSHIFT;
            bval = pixel[2] >> RSHIFT;

            if (rval < rmin) {
                rmin = rval;
            } else if (rval > rmax) {
                rmax = rval;
            }

            if (gval < gmin) {
                gmin = gval;
            } else if (gval > gmax) {
                gmax = gval;
            }

            if (bval < bmin) {
                bmin = bval;
            } else if (bval > bmax) {
                bmax = bval;
            }
        }

        return new ColorSpaceBox(rmin, rmax, gmin, gmax, bmin, bmax, histo);
    }

    private static ColorSpaceBox[] medianCutApply(int[] histo, ColorSpaceBox vbox) {
        if (vbox.count(false) == 0) {
            return null;
        }

        // only one pixel, no split
        if (vbox.count(false) == 1) {
            return new ColorSpaceBox[] {vbox.clone(), null};
        }

        int rw = vbox.r2 - vbox.r1 + 1;
        int gw = vbox.g2 - vbox.g1 + 1;
        int bw = vbox.b2 - vbox.b1 + 1;
        int maxw = Math.max(Math.max(rw, gw), bw);

        // Find the partial sum arrays along the selected axis.
        int total = 0;
        int[] partialSum = new int[VBOX_LENGTH];
        Arrays.fill(partialSum, -1); // -1 = not set / 0 = 0
        int[] lookAheadSum = new int[VBOX_LENGTH];
        Arrays.fill(lookAheadSum, -1); // -1 = not set / 0 = 0
        int i, j, k, sum, index;

        if (maxw == rw) {
            for (i = vbox.r1; i <= vbox.r2; i++) {
                sum = 0;
                for (j = vbox.g1; j <= vbox.g2; j++) {
                    for (k = vbox.b1; k <= vbox.b2; k++) {
                        index = getColorIndex(i, j, k);
                        sum += histo[index];
                    }
                }
                total += sum;
                partialSum[i] = total;
            }
        } else if (maxw == gw) {
            for (i = vbox.g1; i <= vbox.g2; i++) {
                sum = 0;
                for (j = vbox.r1; j <= vbox.r2; j++) {
                    for (k = vbox.b1; k <= vbox.b2; k++) {
                        index = getColorIndex(j, i, k);
                        sum += histo[index];
                    }
                }
                total += sum;
                partialSum[i] = total;
            }
        } else { /* maxw == bw */
            for (i = vbox.b1; i <= vbox.b2; i++) {
                sum = 0;
                for (j = vbox.r1; j <= vbox.r2; j++) {
                    for (k = vbox.g1; k <= vbox.g2; k++) {
                        index = getColorIndex(j, k, i);
                        sum += histo[index];
                    }
                }
                total += sum;
                partialSum[i] = total;
            }
        }

        for (i = 0; i < VBOX_LENGTH; i++) {
            if (partialSum[i] != -1) {
                lookAheadSum[i] = total - partialSum[i];
            }
        }

        // determine the cut planes
        return maxw == rw ? doCut('r', vbox, partialSum, lookAheadSum, total)
                : maxw == gw ? doCut('g', vbox, partialSum, lookAheadSum, total)
                        : doCut('b', vbox, partialSum, lookAheadSum, total);
    }

    private static ColorSpaceBox[] doCut(
            char color,
            ColorSpaceBox vbox,
            int[] partialSum,
            int[] lookAheadSum,
            int total) {
        int vbox_dim1;
        int vbox_dim2;

        if (color == 'r') {
            vbox_dim1 = vbox.r1;
            vbox_dim2 = vbox.r2;
        } else if (color == 'g') {
            vbox_dim1 = vbox.g1;
            vbox_dim2 = vbox.g2;
        } else { /* color == 'b' */
            vbox_dim1 = vbox.b1;
            vbox_dim2 = vbox.b2;
        }

        int left, right;
        ColorSpaceBox vbox1, vbox2;
        int d2, count2;

        for (int i = vbox_dim1; i <= vbox_dim2; i++) {
            if (partialSum[i] > total / 2) {
                vbox1 = vbox.clone();
                vbox2 = vbox.clone();

                left = i - vbox_dim1;
                right = vbox_dim2 - i;

                if (left <= right) {
                    d2 = Math.min(vbox_dim2 - 1, ~~(i + right / 2));
                } else {
                    // 2.0 and cast to int is necessary to have the same behaviour as in JavaScript
                    d2 = Math.max(vbox_dim1, ~~((int) (i - 1 - left / 2.0)));
                }

                // avoid 0-count boxes
                while (d2 < 0 || partialSum[d2] <= 0) {
                    d2++;
                }
                count2 = lookAheadSum[d2];
                while (count2 == 0 && d2 > 0 && partialSum[d2 - 1] > 0) {
                    count2 = lookAheadSum[--d2];
                }

                // set dimensions
                if (color == 'r') {
                    vbox1.r2 = d2;
                    vbox2.r1 = d2 + 1;
                } else if (color == 'g') {
                    vbox1.g2 = d2;
                    vbox2.g1 = d2 + 1;
                } else { /* color == 'b' */
                    vbox1.b2 = d2;
                    vbox2.b1 = d2 + 1;
                }

                return new ColorSpaceBox[] {vbox1, vbox2};
            }
        }

        throw new RuntimeException("VBox can't be cut");
    }

    public static ColorMap quantize(int[][] pixels, int maxColors) {
        // short-circuit
        if (pixels.length == 0 || maxColors < 2 || maxColors > 256) {
            return null;
        }

        int[] histo = getHisto(pixels);

        // get the beginning vbox from the colors
        ColorSpaceBox vbox = vboxFromPixels(pixels, histo);
        ArrayList<ColorSpaceBox> pq = new ArrayList<>();
        pq.add(vbox);

        // Round up to have the same behaviour as in JavaScript
        int target = (int) Math.ceil(FRACT_BY_POPULATION * maxColors);

        // first set of colors, sorted by population
        iter(pq, COMPARATOR_COUNT, target, histo);

        // Re-sort by the product of pixel occupancy times the size in color space.
        Collections.sort(pq, COMPARATOR_PRODUCT);

        // next set - generate the median cuts using the (npix * vol) sorting.
        if (maxColors > pq.size()) {
            iter(pq, COMPARATOR_PRODUCT, maxColors, histo);
        }

        // Reverse to put the highest elements first into the color map
        Collections.reverse(pq);

        // calculate the actual colors
        ColorMap cmap = new ColorMap();
        for (ColorSpaceBox vb : pq) {
            cmap.push(vb);
        }

        return cmap;
    }

    /**
     * Inner function to do the iteration.
     */
    private static void iter(List<ColorSpaceBox> lh, Comparator<ColorSpaceBox> comparator, int target, int[] histo) {
        int niters = 0;
        ColorSpaceBox vbox;

        while (niters < MAX_ITERATIONS) {
            vbox = lh.get(lh.size() - 1);
            if (vbox.count(false) == 0) {
                Collections.sort(lh, comparator);
                niters++;
                continue;
            }
            lh.remove(lh.size() - 1);

            // do the cut
            ColorSpaceBox[] vboxes = medianCutApply(histo, vbox);
            ColorSpaceBox vbox1 = vboxes[0];
            ColorSpaceBox vbox2 = vboxes[1];

            if (vbox1 == null) {
                throw new RuntimeException("vbox1 not defined; shouldn't happen!");
            }

            lh.add(vbox1);
            if (vbox2 != null) {
                lh.add(vbox2);
            }
            Collections.sort(lh, comparator);

            if (lh.size() >= target) {
                return;
            }
            if (niters++ > MAX_ITERATIONS) {
                return;
            }
        }
    }

    private static final Comparator<ColorSpaceBox> COMPARATOR_COUNT = Comparator.comparingInt(a -> a.count(false));

    private static final Comparator<ColorSpaceBox> COMPARATOR_PRODUCT = (a, b) -> {
        int aCount = a.count(false);
        int bCount = b.count(false);
        int aVolume = a.volume(false);
        int bVolume = b.volume(false);

        // If count is 0 for both (or the same), sort by volume
        if (aCount == bCount) {
            return aVolume - bVolume;
        }

        // Otherwise sort by products
        return Long.compare((long) aCount * aVolume, (long) bCount * bVolume);
    };
}
