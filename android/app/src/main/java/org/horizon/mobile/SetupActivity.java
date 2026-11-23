package org.horizon.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports the user's prepared game resources into app-private storage, then starts the Rust game. */
public final class SetupActivity extends Activity {
    private static final int PICK_RESOURCES = 41;
    private static final String GAME_VERSION = "26.3";
    private static final int MAX_ENTRIES = 250_000;
    private static final long MAX_UNPACKED_BYTES = 4L * 1024L * 1024L * 1024L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button importButton;
    private Button playButton;
    private ProgressBar progress;

    private File gameDir() {
        return new File(getFilesDir(), "game");
    }

    private File resourcesDir() {
        return new File(gameDir(), ".local/horizon-client/" + GAME_VERSION);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshStatus(null);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(11, 18, 30));
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 30));
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));
        page.setBackgroundColor(Color.rgb(11, 18, 30));

        TextView eyebrow = text("HORIZON · MINECRAFT IN RUST", 12, Color.rgb(91, 207, 170));
        eyebrow.setLetterSpacing(0.12f);
        page.addView(eyebrow, fullWidth());

        TextView title = text("Minecraft Horizons", 30, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(10), 0, dp(8));
        page.addView(title, fullWidth());

        TextView description = text(
                "The game engine is included in Rust. The Minecraft Java Edition game files are not. "
                        + "Import a prepared resource bundle from your own licensed installation to play.",
                15,
                Color.rgb(192, 205, 220));
        description.setLineSpacing(dp(5), 1.0f);
        page.addView(description, fullWidth());

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, dp(22)));

        status = text("Checking resources…", 14, Color.rgb(150, 168, 190));
        page.addView(status, fullWidth());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = fullWidth();
        progressParams.topMargin = dp(12);
        page.addView(progress, progressParams);

        importButton = new Button(this);
        importButton.setText("Choose prepared resources (.zip)");
        importButton.setAllCaps(false);
        importButton.setOnClickListener(view -> chooseResources());
        LinearLayout.LayoutParams importParams = fullWidth();
        importParams.topMargin = dp(24);
        page.addView(importButton, importParams);

        playButton = new Button(this);
        playButton.setText("Start Minecraft Horizons");
        playButton.setAllCaps(false);
        playButton.setOnClickListener(view -> startGame());
        LinearLayout.LayoutParams playParams = fullWidth();
        playParams.topMargin = dp(8);
        page.addView(playButton, playParams);

        TextView footer = text(
                "No Minecraft assets or account data are bundled. Your worlds and imported files stay in this app’s private storage.",
                12,
                Color.rgb(123, 142, 165));
        footer.setPadding(0, dp(22), 0, 0);
        page.addView(footer, fullWidth());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 18, 30));
        scroll.addView(page);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private boolean hasResources() {
        return new File(resourcesDir(), "horizon-resources.json").isFile();
    }

    private void refreshStatus(String message) {
        boolean ready = hasResources();
        status.setText(message != null
                ? message
                : ready
                    ? "Minecraft 26.3 resources are ready. Your game folder is private to this app."
                    : "No prepared resources found. Select the ZIP made from your Minecraft Java Edition installation.");
        playButton.setEnabled(ready);
        importButton.setEnabled(true);
    }

    private void chooseResources() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/zip");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" });
        startActivityForResult(picker, PICK_RESOURCES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_RESOURCES && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importResources(uri);
        }
    }

    private void importResources(Uri uri) {
        importButton.setEnabled(false);
        playButton.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        status.setText("Importing and checking the resource bundle…");
        io.execute(() -> {
            String result;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Could not read the selected file.");
                extractBundle(input);
                result = "Import complete. Minecraft 26.3 resources are ready.";
            } catch (Exception error) {
                result = "Import failed: " + safeMessage(error);
            }
            final String message = result;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                progress.setIndeterminate(false);
                refreshStatus(message);
            });
        });
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void extractBundle(InputStream input) throws Exception {
        File target = resourcesDir();
        File parent = target.getParentFile();
        if (parent == null) throw new IllegalStateException("Invalid private resource path.");
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Could not create the resource folder.");
        File staging = new File(parent, GAME_VERSION + ".importing");
        File backup = new File(parent, GAME_VERSION + ".previous");
        deleteTree(staging);
        deleteTree(backup);
        if (!staging.mkdirs()) throw new IllegalStateException("Could not prepare the import folder.");

        long written = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalStateException("The archive has too many files.");
                File output = checkedChild(staging, entry.getName());
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Could not create an archive folder.");
                } else {
                    File directory = output.getParentFile();
                    if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
                        throw new IllegalStateException("Could not create an archive folder.");
                    }
                    try (FileOutputStream file = new FileOutputStream(output)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            written += count;
                            if (written > MAX_UNPACKED_BYTES) throw new IllegalStateException("The expanded archive is too large.");
                            file.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }
        }

        unwrapOneFolder(staging);
        validateResourceManifest(staging);
        if (target.exists() && !target.renameTo(backup)) {
            deleteTree(staging);
            throw new IllegalStateException("Could not replace the existing resource bundle.");
        }
        if (!staging.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target);
            deleteTree(staging);
            throw new IllegalStateException("Could not install the resource bundle.");
        }
        deleteTree(backup);
    }

    private void validateResourceManifest(File root) throws Exception {
        File manifest = new File(root, "horizon-resources.json");
        if (!manifest.isFile() || manifest.length() == 0 || manifest.length() > 4L * 1024L * 1024L) {
            deleteTree(root);
            throw new IllegalStateException("This ZIP does not contain a valid horizon-resources.json manifest.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) manifest.length());
        try (FileInputStream input = new FileInputStream(manifest)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        if (json.optInt("schema", -1) != 7 || !GAME_VERSION.equals(json.optString("minecraft"))) {
            deleteTree(root);
            throw new IllegalStateException("The resource bundle must be prepared for Minecraft 26.3 (Horizon schema 7).");
        }
    }

    private File checkedChild(File root, String rawName) throws Exception {
        if (rawName == null || rawName.isEmpty()) throw new IllegalStateException("The ZIP contains an empty path.");
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IllegalStateException("The ZIP contains an absolute path.");
        for (String part : name.split("/")) {
            if ("..".equals(part)) throw new IllegalStateException("The ZIP contains a path outside its resource folder.");
        }
        File result = new File(root, name).getCanonicalFile();
        String base = root.getCanonicalPath() + File.separator;
        if (!result.getPath().startsWith(base) && !result.equals(root.getCanonicalFile())) {
            throw new IllegalStateException("The ZIP contains a path outside its resource folder.");
        }
        return result;
    }

    private void unwrapOneFolder(File root) throws Exception {
        if (new File(root, "horizon-resources.json").isFile()) return;
        File[] children = root.listFiles();
        if (children == null || children.length != 1 || !children[0].isDirectory()
                || !new File(children[0], "horizon-resources.json").isFile()) return;
        File wrapper = children[0];
        File[] contents = wrapper.listFiles();
        if (contents == null) throw new IllegalStateException("Could not read the resource folder.");
        for (File child : contents) {
            File destination = new File(root, child.getName());
            if (!child.renameTo(destination)) throw new IllegalStateException("Could not unpack the resource folder.");
        }
        if (!wrapper.delete()) throw new IllegalStateException("Could not finish unpacking the resource folder.");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private void startGame() {
        if (!hasResources()) {
            refreshStatus(null);
            return;
        }
        Intent game = new Intent(this, android.app.NativeActivity.class);
        startActivity(game);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
