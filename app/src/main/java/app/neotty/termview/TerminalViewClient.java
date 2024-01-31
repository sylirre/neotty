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

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import app.neotty.termlib.TerminalSession;

/**
 * Input and scale listener which may be set on a {@link TerminalView} through
 * {@link TerminalView#setOnKeyListener(TerminalViewClient)}.
 * <p/>
 */
public interface TerminalViewClient {
    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);

    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean readControlKey();

    boolean readAltKey();

    boolean readShiftKey();

    boolean readFnKey();

    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

    boolean onLongPress(MotionEvent event);
}
