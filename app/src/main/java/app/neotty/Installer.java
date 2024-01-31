/*
*************************************************************************
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*************************************************************************
*/
package app.neotty;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Runtime data installer for assets embedded into APK.
 */
@SuppressWarnings("WeakerAccess")
public class Installer {

    /**
     * Performs installation of runtime data if necessary.
     */
    public static void setupIfNeeded(final Activity activity, final Runnable whenDone) {
        // List of files to extract.
        final String[] runtimeDataFiles = {
            "bios-256k.bin",
            "efi-virtio.rom",
            "kvmvapic.bin",
            "vgabios.bin",
            "vgabios-ati.bin",
            "vgabios-bochs-display.bin",
            "vgabios-cirrus.bin",
            "vgabios-ramfb.bin",
            "vgabios-stdvga.bin",
            "vgabios-virtio.bin",
            "vgabios-vmware.bin",
            Config.CDROM_IMAGE_NAME,
            Config.PRIMARY_HDD_IMAGE_NAME,
            Config.SECONDARY_HDD_IMAGE_NAME,
        };

        boolean allFilesPresent = true;
        for (String dataFile : runtimeDataFiles) {
            if (!new File(Config.getDataDirectory(activity), dataFile).exists()) {
                allFilesPresent = false;
                break;
            }
        }

        final TerminalPreferences prefs = new TerminalPreferences(activity);

        // If all files are present and application was not upgraded, no need to
        // extract files.
        if (allFilesPresent && BuildConfig.VERSION_CODE == prefs.getDataVersion()) {
            whenDone.run();
            return;
        }

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
        dialogBuilder.setCancelable(false);
        dialogBuilder.setView(R.layout.installer_progress);

        final AlertDialog progress = dialogBuilder.create();
        progress.show();

        new Thread() {
            @Override
            public void run() {
                try {
                    AssetManager assetManager = activity.getAssets();
                    final byte[] buffer = new byte[16384];
                    for (String dataFile : runtimeDataFiles) {
                        File outputFile = new File(Config.getDataDirectory(activity), dataFile);

                        // We do not want to overwrite user data during upgrade.
                        if (dataFile.equals(Config.PRIMARY_HDD_IMAGE_NAME) && outputFile.exists()) {
                            continue;
                        }
                        if (dataFile.equals(Config.SECONDARY_HDD_IMAGE_NAME) && outputFile.exists()) {
                            continue;
                        }

                        Log.i(Config.INSTALLER_LOG_TAG, "extracting runtime data: " + dataFile);
                        try (InputStream inStream = assetManager.open(dataFile)) {
                            try (FileOutputStream outStream = new FileOutputStream(outputFile)) {
                                int readBytes;
                                while ((readBytes = inStream.read(buffer)) != -1) {
                                    outStream.write(buffer, 0, readBytes);
                                }
                                outStream.flush();
                            }
                        }
                    }

                    // Need to register current data version, so we can track it and determine
                    // whether data files need to be extracted again.
                    prefs.updateDataVersion(activity);

                    activity.runOnUiThread(whenDone);
                } catch (final Exception e) {
                    Log.e(Config.INSTALLER_LOG_TAG, "runtime data installation failed", e);
                    activity.runOnUiThread(() -> {
                        try {
                            new AlertDialog.Builder(activity)
                                .setTitle(R.string.installer_error_title)
                                .setMessage(R.string.installer_error_body)
                                .setNegativeButton(R.string.exit_label, (dialog, which) -> {
                                    dialog.dismiss();
                                    activity.finish();
                                }).setPositiveButton(R.string.installer_error_try_again_button, (dialog, which) -> {
                                dialog.dismiss();
                                Installer.setupIfNeeded(activity, whenDone);
                            }).show();
                        } catch (WindowManager.BadTokenException e1) {
                            // Activity already dismissed - ignore.
                        }
                    });
                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }
}
