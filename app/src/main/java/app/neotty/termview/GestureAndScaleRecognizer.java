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
package app.neotty.termview;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * A combination of {@link GestureDetector} and {@link ScaleGestureDetector}.
 */
@SuppressWarnings("WeakerAccess")
final class GestureAndScaleRecognizer {

    @SuppressWarnings("UnusedReturnValue")
    public interface Listener {
        boolean onSingleTapUp(MotionEvent e);

        boolean onDoubleTap(MotionEvent e);

        boolean onScroll(MotionEvent e2, float dx, float dy);

        boolean onFling(MotionEvent e, float velocityX, float velocityY);

        boolean onScale(float focusX, float focusY, float scale);

        boolean onDown(float x, float y);

        boolean onUp(MotionEvent e);

        void onLongPress(MotionEvent e);
    }

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    private final Listener mListener;
    private boolean isAfterLongPress;

    public GestureAndScaleRecognizer(Context context, Listener listener) {
        mListener = listener;

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                return mListener.onScroll(e2, dx, dy);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return mListener.onFling(e2, velocityX, velocityY);
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return mListener.onDown(e.getX(), e.getY());
            }

            @Override
            public void onLongPress(MotionEvent e) {
                mListener.onLongPress(e);
                isAfterLongPress = true;
            }
        }, null, true /* ignoreMultitouch */);

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return mListener.onSingleTapUp(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return mListener.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }
        });

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                return mListener.onScale(detector.getFocusX(), detector.getFocusY(), detector.getScaleFactor());
            }
        });
        mScaleDetector.setQuickScaleEnabled(false);
    }

    public void onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isAfterLongPress = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!isAfterLongPress) {
                    // This behaviour is desired when in e.g. vim with mouse events, where we do not
                    // want to move the cursor when lifting finger after a long press.
                    mListener.onUp(event);
                }
                break;
        }
    }

    public boolean isInProgress() {
        return mScaleDetector.isInProgress();
    }
}
