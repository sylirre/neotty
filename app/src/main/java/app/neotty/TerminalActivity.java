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

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.neotty.termlib.TerminalSession;
import app.neotty.termlib.TerminalSession.SessionChangedCallback;
import app.neotty.termview.TerminalView;

public final class TerminalActivity extends Activity implements ServiceConnection {

    private static final int CONTEXTMENU_PASTE_ID = 1;
    private static final int CONTEXTMENU_OPEN_SSH = 2;
    private static final int CONTEXTMENU_OPEN_HTTP = 3;
    private static final int CONTEXTMENU_OPEN_HTTPS = 4;
    private static final int CONTEXTMENU_AUTOFILL_PW = 5;
    private static final int CONTEXTMENU_SELECT_URLS = 6;
    private static final int CONTEXTMENU_RESET_TERMINAL_ID = 7;
    private static final int CONTEXTMEMU_SHUTDOWN = 8;
    private static final int CONTEXTMENU_TOGGLE_IGNORE_BELL = 9;
    private static final int CONTEXTMENU_TOGGLE_AUTO_SCROLL = 10;

    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 1000;

    private final int MAX_FONTSIZE = 256;
    private int MIN_FONTSIZE;
    private static int currentFontSize = -1;

    /**
     * Global state of application settings.
     */
    TerminalPreferences mSettings;

    /**
     * The main view of the activity showing the terminal. Initialized in onCreate().
     */
    TerminalView mTerminalView;

    /**
     * The view of Extra Keys Row. Initialized in onCreate() and used by InputDispatcher.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The connection to the {@link TerminalService}. Requested in {@link #onCreate(Bundle)}
     * with a call to {@link #bindService(Intent, ServiceConnection, int)}, obtained and stored
     * in {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TerminalService mTermService;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of
     * the terminal view at the time, so if the session causing a change is not in the foreground
     * it should probably be treated as background.
     */
    private boolean mIsVisible;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);

        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setOnKeyListener(new InputDispatcher(this));
        mTerminalView.setKeepScreenOn(true);
        mTerminalView.requestFocus();
        setupTerminalStyle();
        registerForContextMenu(mTerminalView);

        mSettings = new TerminalPreferences(this);
        mExtraKeysView = findViewById(R.id.extra_keys);
        if (mSettings.isExtraKeysEnabled()) {
            mExtraKeysView.setVisibility(View.VISIBLE);
        }

        // Start the service and make it run regardless of who is bound to it:
        Intent serviceIntent = new Intent(this, TerminalService.class);
        startService(serviceIntent);
        if (!bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }

        // On Android 13+ it is not possible to display service notification
        // without granting relevant permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISSION_REQUEST_CODE_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATIONS) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                if (mTermService != null) {
                    mTermService.updateNotification();
                }
            }
        }
    }

    /**
     * Reset terminal font size to the optimal value and set custom text font.
     */
    private void setupTerminalStyle() {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
            getResources().getDisplayMetrics());
        int defaultFontSize = Math.round(7.5f * dipInPixels);

        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        if (TerminalActivity.currentFontSize == -1) {
            TerminalActivity.currentFontSize = defaultFontSize;
        }

        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum
        // font size to prevent invisible text due to zoom be mistake:
        MIN_FONTSIZE = (int) (4f * dipInPixels);

        TerminalActivity.currentFontSize = Math.max(MIN_FONTSIZE,
            Math.min(TerminalActivity.currentFontSize, MAX_FONTSIZE));
        mTerminalView.setTextSize(TerminalActivity.currentFontSize);

        // Use bundled in app monospace font.
        mTerminalView.setTypeface(Typeface.createFromAsset(getAssets(), "console_font.ttf"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsVisible = true;

        if (mTermService != null) {
            TerminalSession session = mTermService.getSession();
            if (session != null) {
                mTerminalView.attachSession(session);
            }
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal:
        mTerminalView.onScreenUpdated();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsVisible = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTermService != null) {
            // Do not leave service with references to activity.
            mTermService.mSessionChangeCallback = null;
            mTermService = null;
            unbindService(this);
        }
    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which
     * will cause a call to this callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mTermService = ((TerminalService.LocalBinder) service).service;

        mTermService.mSessionChangeCallback = new SessionChangedCallback() {
            @Override
            public void onTextChanged(TerminalSession changedSession) {
                if (!mIsVisible) return;
                if (mTerminalView.getCurrentSession() == changedSession) {
                    mTerminalView.onScreenUpdated();
                }
            }

            @Override
            public void onTitleChanged(TerminalSession updatedSession) {
                return;
            }

            @Override
            public void onSessionFinished(final TerminalSession finishedSession) {
                // Needed for resetting font size on next application launch
                // otherwise it will be reset only after force-closing.
                TerminalActivity.currentFontSize = -1;

                // Do not immediately terminate service in debug builds.
                if (!BuildConfig.DEBUG) {
                    if (mTermService.mWantsToStop) {
                        // The service wants to stop as soon as possible.
                        if (!TerminalActivity.this.isFinishing()) {
                            finish();
                        }
                        return;
                    }
                    mTermService.terminateService();
                }
            }

            @Override
            public void onClipboardText(TerminalSession session, String text) {
                if (!mIsVisible) return;
                ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(new ClipData(null,
                        new String[]{"text/plain"}, new ClipData.Item(text)));
                }
            }

            @Override
            public void onBell(TerminalSession session) {
                if (!mIsVisible || mSettings.isBellIgnored()) {
                    return;
                }

                Bell.getInstance(TerminalActivity.this).doBell();
            }
        };

        if (mTermService.getSession() == null) {
            if (mIsVisible) {
                Installer.setupIfNeeded(TerminalActivity.this, () -> {
                    if (mTermService == null) return; // Activity might have been destroyed.

                    try {
                        TerminalSession session = startQemu();
                        mTerminalView.attachSession(session);
                        mTermService.setSession(session);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                if (!TerminalActivity.this.isFinishing()) {
                    finish();
                }
            }
        } else {
            mTerminalView.attachSession(mTermService.getSession());
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Respect being stopped from the TerminalService notification action.
        if (!TerminalActivity.this.isFinishing()) {
            finish();
        }
    }

    /**
     * Get a random free high tcp port which later will be used in startQemu().
     *
     * @param desiredPort  Integer value that specifies desired port.
     *
     * @param minPort      Minimal port that can be selected randomly.
     *
     * @param maxPort      Maximal port that can be selected randomly.
     *
     * @return Integer value specified by desiredPort if the given TCP port is
     *         available, otherwise value in range (minPort, maxPort). On
     *         failure -1 will be returned instead.
     */
    private int getFreePort(int desiredPort, int minPort, int maxPort) {
        Random rnd = new Random();
        int port = -1;

        // Check whether desired port is available.
        try (ServerSocket sock = new ServerSocket(desiredPort)) {
            sock.setReuseAddress(true);
            port = sock.getLocalPort();
        } catch (Exception e) {
            Log.w(Config.APP_LOG_TAG, "cannot acquire tcp port", e);
        }
        if (port != -1) return port;

        // Otherwise try to get a random port.
        for (int i=0; i<32; i++) {
            try (ServerSocket sock = new ServerSocket(minPort + rnd.nextInt(maxPort - minPort + 1))) {
                sock.setReuseAddress(true);
                port = sock.getLocalPort();
                break;
            } catch (Exception e) {
                Log.w(Config.APP_LOG_TAG, "cannot acquire tcp port", e);
            }
        }

        return port;
    }

    /**
     * Determine a safe amount of memory which could be allocated by QEMU.
     * @return Array containing 2 integers, [tcg, vm_ram];
     */
    private int[] getSafeMem() {
        Context appContext = this;
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

        if (am == null) {
            return new int[]{Config.QEMU_MIN_TCG_BUF, Config.QEMU_MIN_SAFE_RAM};
        }

        am.getMemoryInfo(memInfo);

        // Log memory information for troubleshooting purposes.
        Log.i(Config.APP_LOG_TAG, "memory: " + memInfo.totalMem + " total, "
            + memInfo.availMem + " avail, " + memInfo.threshold + " oom threshold");
        Log.i(Config.APP_LOG_TAG, "system low on memory: " + memInfo.lowMemory);

        // Unconditionally reserve 20% + oom threshold for system to ensure that
        // application won't be killed by Android unless really necessary.
        int safeMem = (int) ((memInfo.availMem * 0.8 - memInfo.threshold) / 1048576);

        // Ensure that neither tcg or ram buffer size is below minimum.
        // TCG will consume 12% of available safe-for-use memory.
        int tcgAlloc = Math.min(Config.QEMU_MAX_TCG_BUF,
            Math.max(Config.QEMU_MIN_TCG_BUF, (int) (safeMem * 0.12)));
        int ramAlloc = Math.min(Config.QEMU_MAX_SAFE_RAM,
            Math.max(Config.QEMU_MIN_SAFE_RAM, (int) (safeMem - safeMem * 0.12)));

        Log.i(Config.APP_LOG_TAG, "calculated safe mem (tcg, ram): [" + tcgAlloc + ", " + ramAlloc + "]");

        return new int[]{tcgAlloc, ramAlloc};
    }

    /**
     * Create a terminal session running QEMU.
     * @return TerminalSession instance.
     */
    private TerminalSession startQemu() {
        ArrayList<String> environment = new ArrayList<>();
        Context appContext = this;

        String runtimeDataPath = Config.getDataDirectory(appContext);

        environment.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        environment.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        environment.add("APP_RUNTIME_DIR=" + runtimeDataPath);
        environment.add("LANG=en_US.UTF-8");
        environment.add("HOME=" + runtimeDataPath);
        environment.add("PATH=/system/bin");
        environment.add("TMPDIR=" + appContext.getCacheDir().getAbsolutePath());

        // Used by QEMU internal DNS.
        environment.add("CONFIG_QEMU_DNS=" + Config.QEMU_UPSTREAM_DNS_V4);
        environment.add("CONFIG_QEMU_DNS6=" + Config.QEMU_UPSTREAM_DNS_V6);

        // Variables present on Android 10 or higher.
        String[] androidExtra = {
            "ANDROID_ART_ROOT",
            "ANDROID_I18N_ROOT",
            "ANDROID_RUNTIME_ROOT",
            "ANDROID_TZDATA_ROOT"
        };
        for (String var : androidExtra) {
            String value = System.getenv(var);
            if (value != null) {
                environment.add(var + "=" + value);
            }
        }

        // QEMU is loaded as shared library, however options are being provided as
        // command line arguments.
        ArrayList<String> processArgs = new ArrayList<>();

        // Fake argument to provide argv[0].
        processArgs.add("QEMU");

        // Path to directory with firmware & keymap files.
        processArgs.addAll(Arrays.asList("-L", runtimeDataPath));

        // Emulate CPU with max feature set.
        processArgs.addAll(Arrays.asList("-cpu", "max"));

        // Emulate SMP.
        processArgs.addAll(Arrays.asList("-smp", "cpus=4,cores=1,threads=1"));

        // Use information about available free memory reported by Android OS to
        // choose appropriate values.
        // mem[0] - tcg buffer size, mem[1] - vm ram buffer size.
        int[] mem = getSafeMem();
        processArgs.addAll(Arrays.asList("-accel", "tcg,tb-size=" + mem[0], "-m", String.valueOf(mem[1])));

        // Do not create default devices.
        processArgs.add("-nodefaults");

        // SCSI CD-ROM(s) and HDD(s).
        processArgs.addAll(Arrays.asList("-drive", "file=" + runtimeDataPath + "/"
            + Config.CDROM_IMAGE_NAME + ",if=none,media=cdrom,index=0,id=cd0"));
        processArgs.addAll(Arrays.asList("-drive", "file=" + runtimeDataPath + "/"
            + Config.PRIMARY_HDD_IMAGE_NAME
            + ",if=none,index=1,discard=unmap,detect-zeroes=unmap,cache=writeback,id=hd0"));
        processArgs.addAll(Arrays.asList("-drive", "file=" + runtimeDataPath + "/"
            + Config.SECONDARY_HDD_IMAGE_NAME
            + ",if=none,index=2,discard=unmap,detect-zeroes=unmap,cache=writeback,id=hd1"));
        processArgs.addAll(Arrays.asList("-device", "virtio-scsi-pci,id=virtio-scsi-pci0"));
        processArgs.addAll(Arrays.asList("-device",
            "scsi-cd,bus=virtio-scsi-pci0.0,id=scsi-cd0,drive=cd0"));
        processArgs.addAll(Arrays.asList("-device",
            "scsi-hd,bus=virtio-scsi-pci0.0,id=scsi-hd0,drive=hd0"));
        processArgs.addAll(Arrays.asList("-device",
            "scsi-hd,bus=virtio-scsi-pci0.0,id=scsi-hd1,drive=hd1"));

        // Try to boot from HDD.
        processArgs.addAll(Arrays.asList("-boot", "c,menu=on"));

        // Setup random number generator.
        processArgs.addAll(Arrays.asList("-object", "rng-random,filename=/dev/urandom,id=rng0"));
        processArgs.addAll(Arrays.asList("-device", "virtio-rng-pci,rng=rng0,id=virtio-rng-pci0"));

        // Networking.
        String vmnicArgs = "user,id=vmnic0";

        // Get a free high port for SSH forwarding.
        // This port will be exposed to external network. User should take care about security.
        int sshPort = getFreePort(16022, 20000, 24999);
        if (sshPort != -1) {
            mTermService.SSH_PORT = sshPort;
            vmnicArgs = vmnicArgs + ",hostfwd=tcp::" + sshPort + "-:22";
        }

        // Get a free high port for Web forwarding.
        // This port will be exposed to external network. User should take care about security.
        int httpPort = getFreePort(16080, 25000, 29999);
        if (httpPort != -1) {
            mTermService.HTTP_PORT = httpPort;
            vmnicArgs = vmnicArgs + ",hostfwd=tcp::" + httpPort + "-:80";
        }
        int httpsPort = getFreePort(16443, 30000, 34999);
        if (httpsPort != -1) {
            mTermService.HTTPS_PORT = httpsPort;
            vmnicArgs = vmnicArgs + ",hostfwd=tcp::" + httpsPort + "-:443";
        }

        processArgs.addAll(Arrays.asList("-netdev", vmnicArgs));
        processArgs.addAll(Arrays.asList("-device", "virtio-net-pci,netdev=vmnic0,id=virtio-net-pci0"));

        boolean hasStorage = false;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // On Android 11 we need to deal with MANAGE_EXTERNAL_STORAGE permission to overcome
                // the scoped storage restrictions.
                // Ref: https://developer.android.com/about/versions/11/privacy/storage#all-files-access
                // Ref: https://developer.android.com/training/data-storage/manage-all-files
                if (Environment.isExternalStorageManager()) {
                    hasStorage = true;
                }
            } else {
                // Otherwise use a regular permission WRITE_EXTERNAL_STORAGE.
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                    hasStorage = true;
                }
            }
        }

        if (hasStorage) {
            String sharedStoragePath = null;

            if (new File("/storage/self/primary").listFiles() != null) {
                sharedStoragePath = "/storage/self/primary";
                Log.i(Config.APP_LOG_TAG, "using /storage/self/primary as shared storage path");
            } else {
                Log.w(Config.APP_LOG_TAG, "unable to read /storage/self/primary, using fallback method of determining shared storage path");

                // Determine storage directory under /storage/emulated.
                UserManager userManager = (UserManager) appContext.getSystemService(Context.USER_SERVICE);
                if (userManager != null) {
                    long auid = userManager.getSerialNumberForUser(UserHandle.getUserHandleForUid(appContext.getApplicationInfo().uid));
                    if (new File("/storage/emulated/" + auid).listFiles() != null) {
                        sharedStoragePath = "/storage/emulated/" + auid;
                        Log.i(Config.APP_LOG_TAG, "using /storage/emulated/" + auid + " as shared storage path");
                    } else {
                        Log.e(Config.APP_LOG_TAG, "unable to read /storage/emulated/" + auid + ", shared storage will be unavailable");
                    }
                } else {
                    Log.e(Config.APP_LOG_TAG, "userManager is null, unable to set up access to shared storage");
                }
            }

            if (sharedStoragePath != null) {
                processArgs.addAll(Arrays.asList("-fsdev",
                    "local,security_model=none,id=fsdev0,multidevs=remap,path=" + sharedStoragePath));
                processArgs.addAll(Arrays.asList("-device",
                    "virtio-9p-pci,fsdev=fsdev0,mount_tag=shared_storage,id=virtio-9p-pci0"));
            }
        }

        // Provide dummy graphics but don't output anything.
        processArgs.add("-nographic");
        processArgs.addAll(Arrays.asList("-device", "VGA,id=vga-pci0,vgamem_mb=16"));
        //processArgs.addAll(Arrays.asList("-vnc", "127.0.0.1:1" + ",password=off"));

        // Disable parallel port.
        processArgs.addAll(Arrays.asList("-parallel", "none"));

        // Serial console.
        processArgs.addAll(Arrays.asList("-chardev", "stdio,id=serial0,mux=off,signal=off"));
        processArgs.addAll(Arrays.asList("-serial", "chardev:serial0"));

        Log.i(Config.APP_LOG_TAG, "initiating QEMU session with following arguments: "
            + processArgs.toString());

        return new TerminalSession(processArgs.toArray(new String[0]),
            environment.toArray(new String[0]), Config.getDataDirectory(appContext), mTermService);
    }

    /**
     * Hook system menu to show context menu instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (mTermService != null) {
            if (mTermService.SSH_PORT != -1) {
                menu.add(Menu.NONE, CONTEXTMENU_OPEN_SSH, Menu.NONE, getResources().getString(R.string.menu_open_ssh, "0.0.0.0:" + mTermService.SSH_PORT));
            }

            if (mTermService.HTTP_PORT != -1) {
                menu.add(Menu.NONE, CONTEXTMENU_OPEN_HTTP, Menu.NONE, getResources().getString(R.string.menu_open_http, "0.0.0.0:" + mTermService.HTTP_PORT));
            }

            if (mTermService.HTTPS_PORT != -1) {
                menu.add(Menu.NONE, CONTEXTMENU_OPEN_HTTPS, Menu.NONE, getResources().getString(R.string.menu_open_https, "0.0.0.0:" + mTermService.HTTPS_PORT));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.isEnabled()) {
                menu.add(Menu.NONE, CONTEXTMENU_AUTOFILL_PW, Menu.NONE, R.string.menu_autofill_pw);
            }
        }
        menu.add(Menu.NONE, CONTEXTMENU_SELECT_URLS, Menu.NONE, R.string.menu_select_urls);
        menu.add(Menu.NONE, CONTEXTMENU_RESET_TERMINAL_ID, Menu.NONE, R.string.menu_reset_terminal);
        menu.add(Menu.NONE, CONTEXTMEMU_SHUTDOWN, Menu.NONE, R.string.menu_shutdown);
        menu.add(Menu.NONE, CONTEXTMENU_TOGGLE_IGNORE_BELL, Menu.NONE, R.string.menu_toggle_ignore_bell)
            .setCheckable(true).setChecked(mSettings.isBellIgnored());
        menu.add(Menu.NONE, CONTEXTMENU_TOGGLE_AUTO_SCROLL, Menu.NONE, R.string.menu_toggle_scrolling)
            .setCheckable(true).setChecked(mSettings.isAutoScrollDisabled());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CONTEXTMENU_PASTE_ID:
                doPaste();
                return true;
            case CONTEXTMENU_OPEN_SSH:
                if (mTermService == null) {
                    return false;
                }

                if (mTermService.SSH_PORT != -1) {
                    AlertDialog.Builder prompt = new AlertDialog.Builder(this);
                    EditText userNameInput = new EditText(this);
                    userNameInput.setText(mSettings.getDefaultSshUser());
                    prompt.setTitle(R.string.dialog_set_ssh_user_title);
                    prompt.setView(userNameInput);

                    prompt.setPositiveButton(R.string.ok_label, (dialog, which) -> {
                        String userName = userNameInput.getText().toString();

                        if (!userName.matches("[a-z_][a-z0-9_-]{0,31}")) {
                            dialog.dismiss();
                            Toast.makeText(this, R.string.dialog_set_ssh_user_invalid_name, Toast.LENGTH_LONG).show();
                            return;
                        } else {
                            mSettings.setDefaultSshUser(this, userName);
                        }

                        // Such URLs handled by applications like ConnectBot.
                        String address = "ssh://" + userName + "@127.0.0.1:" + mTermService.SSH_PORT + "/#NeoTTY";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.toast_open_ssh_intent_failure, Toast.LENGTH_LONG).show();
                            Log.e(Config.APP_LOG_TAG, "failed to start intent", e);
                        }
                        dialog.dismiss();
                    }).setNegativeButton(R.string.cancel_label, ((dialog, which) -> dialog.dismiss())).show();
                } else {
                    Toast.makeText(this, R.string.toast_port_fwd_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            case CONTEXTMENU_OPEN_HTTP:
                int httpPort = -1;

                if (mTermService != null) {
                    httpPort = mTermService.HTTP_PORT;
                }

                if (httpPort != -1) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:" + httpPort));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.toast_open_web_intent_failure, Toast.LENGTH_LONG).show();
                        Log.e(Config.APP_LOG_TAG, "failed to start intent", e);
                    }
                } else {
                    Toast.makeText(this, R.string.toast_port_fwd_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            case CONTEXTMENU_OPEN_HTTPS:
                int httpsPort = -1;

                if (mTermService != null) {
                    httpsPort = mTermService.HTTPS_PORT;
                }

                if (httpsPort != -1) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://127.0.0.1:" + httpsPort));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.toast_open_web_intent_failure, Toast.LENGTH_LONG).show();
                        Log.e(Config.APP_LOG_TAG, "failed to start intent", e);
                    }
                } else {
                    Toast.makeText(this, R.string.toast_port_fwd_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            case CONTEXTMENU_AUTOFILL_PW:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AutofillManager autofillManager = getSystemService(AutofillManager.class);
                    if (autofillManager != null && autofillManager.isEnabled()) {
                        autofillManager.requestAutofill(mTerminalView);
                    }
                }
                return true;
            case CONTEXTMENU_SELECT_URLS:
                showUrlSelection();
                return true;
            case CONTEXTMENU_RESET_TERMINAL_ID: {
                TerminalSession session = mTerminalView.getCurrentSession();
                if (session != null) {
                    session.reset(true);
                    Toast.makeText(this, R.string.toast_reset_terminal,
                        Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case CONTEXTMEMU_SHUTDOWN:
                if (mTermService != null) {
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_shut_down_title)
                        .setMessage(R.string.dialog_shut_down_desc)
                        .setPositiveButton(R.string.dialog_shut_down_yes_btn, (dialog, which) -> {
                            dialog.dismiss();
                            mTermService.terminateService();
                        }).setNegativeButton(R.string.cancel_label,
                        ((dialog, which) -> dialog.dismiss())).show();
                }
                return true;
            case CONTEXTMENU_TOGGLE_IGNORE_BELL:
                boolean bellIgnored = mSettings.isBellIgnored();
                mSettings.setIgnoreBellCharacter(this, !bellIgnored);
                if (!bellIgnored) {
                    Toast.makeText(this, R.string.toast_bell_char_ignored, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.toast_bell_char_processed, Toast.LENGTH_LONG).show();
                }
                return true;
            case CONTEXTMENU_TOGGLE_AUTO_SCROLL:
                if (mTermService != null) {
                    TerminalSession session = mTermService.getSession();
                    if (session != null) {
                        boolean disableScroll = mSettings.isAutoScrollDisabled();
                        session.getEmulator().disableAutoScroll(!disableScroll);
                        mSettings.setDisableAutoScroll(this, !disableScroll);

                        if (!disableScroll) {
                            Toast.makeText(this, R.string.toast_terminal_scrolling_disabled, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, R.string.toast_terminal_scrolling_enabled, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                }
                return false;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Paste text from clipboard.
     */
    public void doPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            ClipData clipData = clipboard.getPrimaryClip();

            if (clipData == null) {
                return;
            }

            CharSequence paste = clipData.getItemAt(0).coerceToText(this);
            if (!TextUtils.isEmpty(paste)) {
                TerminalSession currentSession = mTerminalView.getCurrentSession();

                if (currentSession != null) {
                    currentSession.getEmulator().paste(paste.toString());
                }
            }
        }
    }

    /**
     * Extract URLs from the current transcript and show them in dialog.
     */
    public void showUrlSelection() {
        TerminalSession currentSession = mTerminalView.getCurrentSession();

        if (currentSession == null) {
            return;
        }

        String text = currentSession.getEmulator().getScreen().getTranscriptText();
        LinkedHashSet<CharSequence> urlSet = extractUrls(text);

        if (urlSet.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_urls_found, Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        Collections.reverse(Arrays.asList(urls)); // Latest first.

        // Click to copy url to clipboard:
        final AlertDialog dialog = new AlertDialog.Builder(TerminalActivity.this)
            .setItems(urls, (di, which) -> {
                String url = (String) urls[which];
                ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"},
                        new ClipData.Item(url)));
                    Toast.makeText(this, R.string.toast_url_copied,
                        Toast.LENGTH_SHORT).show();
                }
        }).setTitle(R.string.select_url_dialog_title).create();

        // Long press to open URL:
        dialog.setOnShowListener(di -> {
            ListView lv = dialog.getListView(); // this is a ListView with your "buds" in it
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];

                // Disable handling of 'file://' urls since this may
                // produce android.os.FileUriExposedException.
                if (!url.startsWith("file://")) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try {
                        startActivity(i, null);
                    } catch (ActivityNotFoundException e) {
                        // If no applications match, Android displays a system message.
                        startActivity(Intent.createChooser(i, null));
                    }
                } else {
                    Toast.makeText(this, R.string.toast_bad_url, Toast.LENGTH_SHORT).show();
                }

                return true;
            });
        });

        dialog.show();
    }

    /**
     * Extract URLs from the given text.
     */
    @SuppressWarnings("StringBufferReplaceableByString")
    private static LinkedHashSet<CharSequence> extractUrls(String text) {
        StringBuilder regex_sb = new StringBuilder();

        regex_sb.append("(");                       // Begin first matching group.
        regex_sb.append("(?:");                     // Begin scheme group.
        regex_sb.append("dav|");                    // The DAV proto.
        regex_sb.append("dict|");                   // The DICT proto.
        regex_sb.append("dns|");                    // The DNS proto.
        regex_sb.append("file|");                   // File path.
        regex_sb.append("finger|");                 // The Finger proto.
        regex_sb.append("ftp(?:s?)|");              // The FTP proto.
        regex_sb.append("git|");                    // The Git proto.
        regex_sb.append("gemini|");                 // The Gemini proto.
        regex_sb.append("gopher|");                 // The Gopher proto.
        regex_sb.append("http(?:s?)|");             // The HTTP proto.
        regex_sb.append("imap(?:s?)|");             // The IMAP proto.
        regex_sb.append("irc(?:[6s]?)|");           // The IRC proto.
        regex_sb.append("ip[fn]s|");                // The IPFS proto.
        regex_sb.append("ldap(?:s?)|");             // The LDAP proto.
        regex_sb.append("pop3(?:s?)|");             // The POP3 proto.
        regex_sb.append("redis(?:s?)|");            // The Redis proto.
        regex_sb.append("rsync|");                  // The Rsync proto.
        regex_sb.append("rtsp(?:[su]?)|");          // The RTSP proto.
        regex_sb.append("sftp|");                   // The SFTP proto.
        regex_sb.append("smb(?:s?)|");              // The SAMBA proto.
        regex_sb.append("smtp(?:s?)|");             // The SMTP proto.
        regex_sb.append("svn(?:(?:\\+ssh)?)|");     // The Subversion proto.
        regex_sb.append("tcp|");                    // The TCP proto.
        regex_sb.append("telnet|");                 // The Telnet proto.
        regex_sb.append("tftp|");                   // The TFTP proto.
        regex_sb.append("udp|");                    // The UDP proto.
        regex_sb.append("vnc|");                    // The VNC proto.
        regex_sb.append("ws(?:s?)");                // The Websocket proto.
        regex_sb.append(")://");                    // End scheme group.
        regex_sb.append(")");                       // End first matching group.

        // Begin second matching group.
        regex_sb.append("(");

        // User name and/or password in format 'user:pass@'.
        regex_sb.append("(?:\\S+(?::\\S*)?@)?");

        // Begin host group.
        regex_sb.append("(?:");

        // IP address (from http://www.regular-expressions.info/examples.html).
        regex_sb.append("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|");

        // Host name or domain.
        regex_sb.append("(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,})))?|");

        // Just path. Used in case of 'file://' scheme.
        regex_sb.append("/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)");

        // End host group.
        regex_sb.append(")");

        // Port number.
        regex_sb.append("(?::\\d{1,5})?");

        // Resource path with optional query string.
        regex_sb.append("(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");

        // Fragment.
        regex_sb.append("(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");

        // End second matching group.
        regex_sb.append(")");

        final Pattern urlPattern = Pattern.compile(
            regex_sb.toString(),
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

        LinkedHashSet<CharSequence> urlSet = new LinkedHashSet<>();
        Matcher matcher = urlPattern.matcher(text);

        while (matcher.find()) {
            int matchStart = matcher.start(1);
            int matchEnd = matcher.end();
            String url = text.substring(matchStart, matchEnd);
            urlSet.add(url);
        }

        return urlSet;
    }

    /**
     * Change terminal font size.
     */
    public void changeFontSize(boolean increase) {
        TerminalActivity.currentFontSize += (increase ? 1 : -1) * 2;
        TerminalActivity.currentFontSize = Math.max(MIN_FONTSIZE,
            Math.min(TerminalActivity.currentFontSize, MAX_FONTSIZE));
        mTerminalView.setTextSize(TerminalActivity.currentFontSize);
    }

    /**
     * Toggle extra keys layout.
     */
    public void toggleShowExtraKeys() {
        View extraKeys = findViewById(R.id.extra_keys);
        boolean showNow = mSettings.toggleShowExtraKeys(TerminalActivity.this);
        extraKeys.setVisibility(showNow ? View.VISIBLE : View.GONE);
    }
}
