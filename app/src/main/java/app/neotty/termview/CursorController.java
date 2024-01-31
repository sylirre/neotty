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

import android.view.MotionEvent;
import android.view.ViewTreeObserver;

/**
 * A CursorController instance can be used to control cursors in the text.
 * It is not used outside of {@link TerminalView}.
 */
public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
    /**
     * Show the cursors on screen. Will be drawn by {@link #render()} by a call during onDraw.
     * See also {@link #hide()}.
     */
    void show(MotionEvent event);

    /**
     * Hide the cursors from screen.
     * See also {@link #show(MotionEvent event)}.
     */
    boolean hide();

    /**
     * Render the cursors.
     */
    void render();

    /**
     * Update the cursor positions.
     */
    void updatePosition(TextSelectionHandleView handle, int x, int y);

    /**
     * This method is called by {@link #onTouchEvent(MotionEvent)} and gives the cursors
     * a chance to become active and/or visible.
     *
     * @param event The touch event
     */
    boolean onTouchEvent(MotionEvent event);

    /**
     * Called when the view is detached from window. Perform house keeping task, such as
     * stopping Runnable thread that would otherwise keep a reference on the context, thus
     * preventing the activity to be recycled.
     */
    void onDetached();

    /**
     * @return true if the cursors are currently active.
     */
    boolean isActive();

}
