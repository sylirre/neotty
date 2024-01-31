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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;

/**
 * Class used to perform vibration once terminal encountered bell character.
 * Contains measures against bell character spamming to prevent lags:
 */
@SuppressWarnings("WeakerAccess")
public class Bell {

    private static final long DURATION = 50;
    private static final long MIN_PAUSE = 3 * DURATION;
    private static final Object LOCK = new Object();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable bellRunnable;

    private static Bell sInstance = null;

    private long mLastBell = 0;

    private Bell(final Vibrator vibrator) {
        bellRunnable = () -> {
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(DURATION, 10));
                } else {
                    vibrator.vibrate(DURATION);
                }
            }
        };
    }

    public static Bell getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new Bell((Vibrator) context.getApplicationContext()
                        .getSystemService(Context.VIBRATOR_SERVICE));
                }
            }
        }

        return sInstance;
    }

    public synchronized void doBell() {
        long now = SystemClock.uptimeMillis();
        long timeSinceLastBell = now - mLastBell;

        if (timeSinceLastBell > 0) {
            if (timeSinceLastBell < MIN_PAUSE) {
                // there was a bell recently, scheudle the next one
                handler.postDelayed(bellRunnable, MIN_PAUSE - timeSinceLastBell);
                mLastBell = mLastBell + MIN_PAUSE;
            } else {
                // the last bell was long ago, do it now
                bellRunnable.run();
                mLastBell = now;
            }
        }
    }
}
