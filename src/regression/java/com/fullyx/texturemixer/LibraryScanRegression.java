package com.fullyx.texturemixer;

import com.fullyx.texturemixer.client.MixerLibrary;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class LibraryScanRegression {
    @SuppressWarnings("unchecked")
    public static int run() throws Exception {
        Path root = Files.createTempDirectory("texturemixer-folder-scan-").toRealPath();
        Files.writeString(root.resolve("Standalone.ZIP"), "recognition fixture");
        Files.writeString(root.resolve("readme.txt"), "ignored");
        Path nested = Files.createDirectories(root.resolve("nested"));
        Files.writeString(nested.resolve("Nested.zip"), "recognition fixture");
        Path extracted = Files.createDirectories(root.resolve("Extracted/assets/minecraft"));
        Files.writeString(extracted.resolve("internal.zip"), "not a separate pack");
        Method scan = MixerLibrary.class.getDeclaredMethod("scan", Path.class);
        scan.setAccessible(true);
        Object result = scan.invoke(null, root);
        Method packs = result.getClass().getDeclaredMethod("packs");
        packs.setAccessible(true);
        List<Path> found = (List<Path>) packs.invoke(result);
        if (found.size() != 3 || !found.contains(root.resolve("Standalone.ZIP"))
                || !found.contains(nested.resolve("Nested.zip")) || !found.contains(root.resolve("Extracted"))) {
            throw new AssertionError("Explicit folder discovery did not identify ZIPs and extracted pack roots");
        }
        if (found.stream().anyMatch(path -> path.endsWith("internal.zip"))) {
            throw new AssertionError("Discovery descended into an extracted resource pack");
        }
        Path many = Files.createDirectories(root.resolve("many"));
        for (int index = 0; index < 70; index++) Files.writeString(many.resolve(index + ".zip"), "recognition fixture");
        Object manyResult = scan.invoke(null, many);
        if (((List<Path>) packs.invoke(manyResult)).size() != 70) {
            throw new AssertionError("Folder discovery stopped at an unrelated candidate count");
        }
        boolean nonDirectoryRejected = false;
        try { scan.invoke(null, root.resolve("readme.txt")); }
        catch (InvocationTargetException expected) { nonDirectoryRejected = expected.getCause() instanceof java.io.IOException; }
        if (!nonDirectoryRejected) throw new AssertionError("File accepted as folder collection root");
        return 4;
    }

    public static void main(String[] arguments) throws Exception {
        System.out.println("TextureMixer folder scan regression: " + run() + " checks passed");
    }
}
