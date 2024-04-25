/*
 * Java Color Thief
 * by Sven Woltmann, Fonpit AG
 * 
 * http://www.androidpit.com
 * http://www.androidpit.de
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

import com.crazyxacker.colorthief.ColorMap;
import com.crazyxacker.colorthief.ColorThief;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;

public class ColorThiefPerformanceTest extends Application {
    private static final int NUM_TESTS_WARMUP = 500;
    private static final int NUM_TESTS = 500;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Image img1 = new Image(new File("./examples/img/photo1.jpg").toURL().toString());
        Image img2 = new Image(new File("./examples/img/photo2.jpg").toURL().toString());
        Image img3 = new Image(new File("./examples/img/photo3.jpg").toURL().toString());

        // Warm up JIT
        System.out.println("Warming up...");
        test(img1, img2, img3, NUM_TESTS_WARMUP);

        // Test
        System.out.println("Testing...");
        long start = System.currentTimeMillis();
        test(img1, img2, img3, NUM_TESTS);
        long end = System.currentTimeMillis();
        long total = end - start;
        System.out.println(
                "Total time = " + total + " ms / per image = " + ((double) total / NUM_TESTS / 3)
                        + " ms");

        Platform.exit();
    }

    private static void test(Image img1, Image img2, Image img3, int max) {
        long sum = 0;

        for (int i = 0; i < max; i++) {
            if (i % 100 == 0) {
                System.out.println("Round " + (i + 1) + " of " + max + "...");
            }

            ColorMap result = ColorThief.getColorMap(img1, 10);
            sum += result.boxes.size();

            result = ColorThief.getColorMap(img2, 10);
            sum += result.boxes.size();

            result = ColorThief.getColorMap(img3, 10);
            sum += result.boxes.size();
        }

        // The sum is calculated (and printed) so that the JIT doesn't think the
        // result is never used and optimizes the whole method calls away ;)
        System.out.println("Finished (sum = " + sum + ")");
    }

}
