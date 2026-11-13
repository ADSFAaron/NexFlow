/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Second opinion for the screenshot tests on CI: do the rendered previews match the references to
 * within {@code tolerance} per colour channel?
 *
 * <p>Reference images are made on macOS. Layoutlib on CI's Linux rounds alpha blending differently,
 * so translucent text and gradients can come out exactly 1/255 apart in a channel. The plugin can
 * only tolerate a share of differing <em>pixels</em>, and that cannot tell the two cases apart:
 * the platform noise touched up to 0.54% of a preview's pixels, while rewording one settings label
 * changed 0.64%. What does separate them is how far a pixel moves — noise by 1, a real change by
 * far more — so that is what this checks.
 *
 * <p>Run after {@code validateGithubDebugScreenshotTest} fails; that task has already written every
 * preview to {@code renderedDir}. Fails on any pixel beyond the tolerance, a size change, a
 * reference with no render (the preview failed or was removed) and a render with no reference (a
 * new preview nobody approved).
 *
 * <p>Usage: {@code java tools/ScreenshotTolerance.java <referenceDir> <renderedDir> [tolerance]}
 */
public class ScreenshotTolerance {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: ScreenshotTolerance <referenceDir> <renderedDir> [tolerance]");
            System.exit(2);
        }
        Path references = Path.of(args[0]);
        Path rendered = Path.of(args[1]);
        int tolerance = args.length > 2 ? Integer.parseInt(args[2]) : 1;

        List<String> problems = new ArrayList<>();
        List<Path> referenceFiles = pngsUnder(references);
        int withinTolerance = 0;

        for (Path reference : referenceFiles) {
            Path relative = references.relativize(reference);
            Path render = rendered.resolve(relative);
            if (!Files.exists(render)) {
                problems.add(relative + ": no rendered image (the preview failed to render, or was removed)");
                continue;
            }
            BufferedImage a = ImageIO.read(reference.toFile());
            BufferedImage b = ImageIO.read(render.toFile());
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                problems.add(relative + ": size " + a.getWidth() + "x" + a.getHeight()
                        + " -> " + b.getWidth() + "x" + b.getHeight());
                continue;
            }
            long beyond = 0;
            int worst = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    int delta = maxChannelDelta(a.getRGB(x, y), b.getRGB(x, y));
                    if (delta > worst) worst = delta;
                    if (delta > tolerance) beyond++;
                }
            }
            if (beyond > 0) {
                problems.add(relative + ": " + beyond + " pixel(s) differ by more than " + tolerance
                        + " (largest channel difference " + worst + ")");
            } else if (worst > 0) {
                withinTolerance++;
            }
        }

        for (Path render : pngsUnder(rendered)) {
            Path relative = rendered.relativize(render);
            if (!Files.exists(references.resolve(relative))) {
                problems.add(relative + ": rendered, but has no reference image");
            }
        }

        System.out.println(referenceFiles.size() + " reference image(s) checked; " + withinTolerance
                + " differ only within " + tolerance + "/255 per channel.");
        if (!problems.isEmpty()) {
            System.out.println(problems.size() + " real difference(s):");
            problems.forEach(p -> System.out.println("  " + p));
            System.exit(1);
        }
    }

    private static int maxChannelDelta(int p, int q) {
        int max = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int d = Math.abs(((p >>> shift) & 0xFF) - ((q >>> shift) & 0xFF));
            if (d > max) max = d;
        }
        return max;
    }

    private static List<Path> pngsUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        }
    }
}
