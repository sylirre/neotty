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

import android.content.Context;

/**
 * Application build-time configuration entries.
 */
@SuppressWarnings("WeakerAccess")
public class Config {
    /**
     * Name of CD-ROM image file.
     * Must be a name of file located in assets directory.
     */
    public static final String CDROM_IMAGE_NAME = "system.iso";

    /**
     * Name of primary HDD image file.
     * Must be a name of file located in assets directory.
     */
    public static final String PRIMARY_HDD_IMAGE_NAME = "userdata-01.qcow2";

    /**
     * Name of secondary HDD image file.
     * Must be a name of file located in assets directory.
     */
    public static final String SECONDARY_HDD_IMAGE_NAME = "userdata-02.qcow2";

    /**
     * Upstream name server used by QEMU DNS resolver (IPv4).
     */
    public static final String QEMU_UPSTREAM_DNS_V4 = "1.1.1.1";

    /**
     * Upstream name server used by QEMU DNS resolver (IPv6).
     */
    public static final String QEMU_UPSTREAM_DNS_V6 = "2606:4700:4700::1111";

    /**
     * Minimal RAM allocation in MiB which guarantees that guest OS will
     * boot and work properly.
     */
    public static final int QEMU_MIN_SAFE_RAM = 256;

    /**
     * Max RAM allocation in MiB which is considered to be safe.
     */
    public static final int QEMU_MAX_SAFE_RAM = 2047;

    /**
     * Minimal size of TCG buffer in MiB that would not cause too many
     * flushes of generated code cache and significant performance
     * degradation.
     */
    public static final int QEMU_MIN_TCG_BUF = 64;

    /**
     * Maximal size in MiB of TCG buffer to prevent wasting of device
     * memory by keeping unnecessary code caches.
     */
    public static final int QEMU_MAX_TCG_BUF = 512;

    /**
     * A tag used for general logging.
     */
    public static final String APP_LOG_TAG = "neotty:app";

    /**
     * A tag used for input (ime) logging.
     */
    public static final String INPUT_LOG_TAG = "neotty:input";

    /**
     * A tag used for installer logging.
     */
    public static final String INSTALLER_LOG_TAG = "neotty:installer";

    /**
     * A tag used for wakelock logging.
     */
    public static final String WAKELOCK_LOG_TAG = "neotty:wakelock";

    /**
     * Returns path to runtime environment directory.
     */
    public static String getDataDirectory(final Context context) {
        return context.getFilesDir().getAbsolutePath();
    }
}
