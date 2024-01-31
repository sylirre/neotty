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

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;

import java.util.Objects;

import app.neotty.termlib.KeyHandler;
import app.neotty.termlib.TerminalEmulator;
import app.neotty.termlib.TerminalSession;
import app.neotty.termview.TerminalViewClient;

@SuppressWarnings("WeakerAccess")
public final class InputDispatcher implements TerminalViewClient {

    private final TerminalActivity mActivity;

    /**
     * Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and
     * other hardware keys.
     */
    private boolean mVirtualControlKeyDown, mVirtualFnKeyDown;

    public InputDispatcher(TerminalActivity activity) {
        this.mActivity = activity;
    }

    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            mActivity.changeFontSize(increase);
            return 1.0f;
        }

        return scale;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        InputMethodManager mgr = (InputMethodManager)
            mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mgr != null) {
            mgr.showSoftInput(mActivity.mTerminalView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (handleVirtualKeys(keyCode, e, true)) return true;

        if (e.isCtrlPressed() && e.isAltPressed()) {
            // Get the unmodified code point:
            int unicodeChar = e.getUnicodeChar(0);

            if (unicodeChar == 'k'/* keyboard */) {
                InputMethodManager imm = (InputMethodManager)
                    mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            } else if (unicodeChar == 'm'/* menu */) {
                mActivity.mTerminalView.showContextMenu();
            } else if (unicodeChar == 'u' /* urls */) {
                mActivity.showUrlSelection();
            } else if (unicodeChar == 'v' /* paste clipboard */) {
                mActivity.doPaste();
            } else if (unicodeChar == '+' || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+') {
                // We also check for the shifted char here since shift may be required to
                // produce '+'.
                mActivity.changeFontSize(true);
            } else if (unicodeChar == '-') {
                mActivity.changeFontSize(false);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return handleVirtualKeys(keyCode, e, false);
    }

    @Override
    public boolean readControlKey() {
        return (mActivity.mExtraKeysView != null &&
            mActivity.mExtraKeysView.readControlButton()) || mVirtualControlKeyDown;
    }

    @Override
    public boolean readAltKey() {
        return (mActivity.mExtraKeysView != null && mActivity.mExtraKeysView.readAltButton());
    }

    @Override
    public boolean readShiftKey() {
        return (mActivity.mExtraKeysView != null && mActivity.mExtraKeysView.readShiftButton());
    }

    @Override
    public boolean readFnKey() {
        return (mActivity.mExtraKeysView != null && mActivity.mExtraKeysView.readFnButton());
    }

    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (mVirtualFnKeyDown) {
            int resultingKeyCode = -1;
            int resultingCodePoint = -1;
            boolean altDown = false;

            int lowerCase = Character.toLowerCase(codePoint);

            switch (lowerCase) {
                // Arrow keys.
                case 'w':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP;
                    break;
                case 'a':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                    break;
                case 's':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                    break;
                case 'd':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                    break;

                // Page up and down.
                case 'p':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP;
                    break;
                case 'n':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN;
                    break;

                // Some special keys:
                case 't':
                    resultingKeyCode = KeyEvent.KEYCODE_TAB;
                    break;
                case 'i':
                    resultingKeyCode = KeyEvent.KEYCODE_INSERT;
                    break;
                case 'h':
                    resultingCodePoint = '~';
                    break;

                // Special characters to input.
                case 'u':
                    resultingCodePoint = '_';
                    break;
                case 'l':
                    resultingCodePoint = '|';
                    break;

                // Function keys.
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    resultingKeyCode = (codePoint - '1') + KeyEvent.KEYCODE_F1;
                    break;
                case '0':
                    resultingKeyCode = KeyEvent.KEYCODE_F10;
                    break;

                // Other special keys.
                case 'e':
                    resultingCodePoint = /*Escape*/ 27;
                    break;
                case '.':
                    resultingCodePoint = /*^.*/ 28;
                    break;

                case 'b': // alt+b, jumping backward in readline.
                case 'f': // alf+f, jumping forward in readline.
                case 'x': // alt+x, common in emacs.
                    resultingCodePoint = lowerCase;
                    altDown = true;
                    break;

                // Volume control.
                case 'v':
                    resultingCodePoint = -1;
                    AudioManager audio = (AudioManager)
                        mActivity.getSystemService(Context.AUDIO_SERVICE);
                    if (audio != null) {
                        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    }
                    break;

                // Extra Keys toggle.
                case 'k':
                    mActivity.toggleShowExtraKeys();
                    mVirtualFnKeyDown = false;
                    break;
            }

            if (resultingKeyCode != -1) {
                TerminalEmulator term = session.getEmulator();
                session.write(Objects.requireNonNull(KeyHandler.getCode(resultingKeyCode, 0,
                    term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode())));
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    /** Handle dedicated volume buttons as virtual keys if applicable. */
    private boolean handleVirtualKeys(int keyCode, KeyEvent event, boolean down) {
        InputDevice inputDevice = event.getDevice();

        if (inputDevice != null &&
            inputDevice.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            // Do not steal dedicated buttons from a full external keyboard.
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVirtualControlKeyDown = down;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mVirtualFnKeyDown = down;
            return true;
        }

        return false;
    }
}
