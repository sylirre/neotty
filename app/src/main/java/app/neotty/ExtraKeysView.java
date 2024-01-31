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
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.ToggleButton;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.neotty.termlib.TerminalSession;
import app.neotty.termview.TerminalView;

/**
 * A view showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboard.
 */
public final class ExtraKeysView extends GridLayout {

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BUTTON_COLOR = 0x00000000;
    private static final int BUTTON_PRESSED_COLOR = 0x7FFFFFFF;

    private ToggleButton mControlButton;
    private ToggleButton mAltButton;
    private ToggleButton mShiftButton;
    private ToggleButton mFnButton;
    private ScheduledExecutorService mScheduledExecutor;
    private PopupWindow mPopupWindow;
    private int mLongPressCount;

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        reload();
    }

    public boolean readSpecialButton(ToggleButton specialButton) {
        if (specialButton == null) {
            return false;
        }

	if (specialButton.isPressed()) {
            return true;
        }

        boolean result = specialButton.isChecked();
	if (result) {
            specialButton.setChecked(false);
            specialButton.setTextColor(TEXT_COLOR);
        }

        return result;
    }

    public boolean readShiftButton() {
        return readSpecialButton(mShiftButton);
    }

    public boolean readControlButton() {
        return readSpecialButton(mControlButton);
    }

    public boolean readAltButton() {
        return readSpecialButton(mAltButton);
    }

    public boolean readFnButton() {
        return readSpecialButton(mFnButton);
    }

    private void popup(View view, String text) {
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();

        Button button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        button.setText(text);
        button.setTextColor(TEXT_COLOR);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setWidth(width);
        button.setHeight(height);
        button.setBackgroundColor(BUTTON_PRESSED_COLOR);

        mPopupWindow = new PopupWindow(this);
        mPopupWindow.setWidth(LayoutParams.WRAP_CONTENT);
        mPopupWindow.setHeight(LayoutParams.WRAP_CONTENT);
        mPopupWindow.setContentView(button);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(false);
        mPopupWindow.showAsDropDown(view, 0, -2 * height);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void reload() {
        mAltButton = mControlButton = null;
        removeAllViews();

        String[][] buttons = {
            {"ESC", "INS", "―",    "|",   "HOME", "↑", "END", "PGUP"},
            {"TAB", "DEL", "CTRL", "ALT", "←",    "↓", "→",   "PGDN"}
	};

        final int rows = buttons.length;
        final int[] cols = {buttons[0].length, buttons[1].length};

        setRowCount(rows);
        setColumnCount(cols[0]);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols[row]; col++) {
                final String buttonText = buttons[row][col];
                Button button;

                switch (buttonText) {
                    case "SHFT":
                        button = mShiftButton = new ToggleButton(getContext(), null,
                            android.R.attr.buttonBarButtonStyle);
                        button.setClickable(true);
                        break;
                    case "CTRL":
                        button = mControlButton = new ToggleButton(getContext(), null,
                            android.R.attr.buttonBarButtonStyle);
                        button.setClickable(true);
                        break;
                    case "ALT":
                        button = mAltButton = new ToggleButton(getContext(), null,
                            android.R.attr.buttonBarButtonStyle);
                        button.setClickable(true);
                        break;
                    case "FN":
                        button = mFnButton = new ToggleButton(getContext(), null,
                            android.R.attr.buttonBarButtonStyle);
                        button.setClickable(true);
                        break;
                    default:
                        button = new Button(getContext(), null,
                            android.R.attr.buttonBarButtonStyle);
                        break;
                }

                button.setText(buttonText);
                button.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "console_font.ttf"));
                button.setTextColor(TEXT_COLOR);
                button.setPadding(0, 0, 0, 0);

                if ("↑←↓→".contains(buttonText)) {
                    button.setTextSize(17);
                }

                final Button finalButton = button;
                button.setOnClickListener(v -> {
                    // Use haptic feedback if possible.
                    if (Settings.System.getInt(getContext().getContentResolver(),
                        Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
                        finalButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }

                    View root = getRootView();

                    switch (buttonText) {
                        case "SHFT":
                        case "CTRL":
                        case "ALT":
                        case "FN":
                            ToggleButton self = (ToggleButton) finalButton;
                            self.setChecked(self.isChecked());
                            self.setTextColor(self.isChecked() ? 0xFF00CC66 : TEXT_COLOR);
                            break;
                        default:
                            sendKey(root, buttonText);
                            break;
                    }
                });

                button.setOnTouchListener((v, event) -> {
                    final View root = getRootView();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mLongPressCount = 0;
                            v.setBackgroundColor(BUTTON_PRESSED_COLOR);

                            if (mScheduledExecutor != null) {
                                mScheduledExecutor.shutdownNow();
                                mScheduledExecutor = null;
                            }

                            if (Arrays.asList("ESC", "INS", "TAB", "DEL",
                                    "HOME", "END", "PGDN", "PGUP",
                                    "↑", "↓", "←", "→").contains(buttonText)) {
                                mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
                                mScheduledExecutor.scheduleWithFixedDelay(() -> {
                                    mLongPressCount++;
                                    sendKey(root, buttonText);
                                }, 400, 80, TimeUnit.MILLISECONDS);
                            }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if ("―/|>".contains(buttonText)) {
                                if (mPopupWindow == null && event.getY() < 0) {
                                    v.setBackgroundColor(BUTTON_COLOR);

                                    switch (buttonText) {
                                        case "―":
                                            popup(v, "_");
                                            break;
                                        case "/":
                                            popup(v, "\\");
                                            break;
                                        case "|":
                                            popup(v, "&");
                                            break;
                                        case ">":
                                            popup(v, "<");
                                            break;
                                    }
                                }
                                if (mPopupWindow != null && event.getY() > 0) {
                                    v.setBackgroundColor(BUTTON_PRESSED_COLOR);
                                    mPopupWindow.dismiss();
                                    mPopupWindow = null;
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            v.setBackgroundColor(BUTTON_COLOR);
                            if (mScheduledExecutor != null) {
                                mScheduledExecutor.shutdownNow();
                                mScheduledExecutor = null;
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            v.setBackgroundColor(BUTTON_COLOR);
                            if (mScheduledExecutor != null) {
                                mScheduledExecutor.shutdownNow();
                                mScheduledExecutor = null;
                            }

                            if (mLongPressCount == 0) {
                                if (mPopupWindow != null && "―/|>".contains(buttonText)) {
                                    mPopupWindow.setContentView(null);
                                    mPopupWindow.dismiss();
                                    mPopupWindow = null;

                                    switch (buttonText) {
                                        case "―":
                                            sendKey(root, "_");
                                            break;
                                        case "/":
                                            sendKey(root, "\\");
                                            break;
                                        case "|":
                                            sendKey(root, "&");
                                            break;
                                        case ">":
                                            sendKey(root, "<");
                                            break;
                                    }
                                } else {
                                    v.performClick();
                                }
                            }
                            return true;
                        default:
                            return true;
                    }

                });

                LayoutParams param = new GridLayout.LayoutParams();
                param.width = 0;
                param.height = 0;

                param.setMargins(0, 0, 0, 0);
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.f);
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1.f);
                button.setLayoutParams(param);

                addView(button);
            }
        }
    }

    private static void sendKey(View view, String keyName) {
        int keyCode = 0;
        switch (keyName) {
            case "ESC":
                keyCode = KeyEvent.KEYCODE_ESCAPE;
                break;
            case "TAB":
                keyCode = KeyEvent.KEYCODE_TAB;
                break;
            case "DEL":
                keyCode = KeyEvent.KEYCODE_FORWARD_DEL;
                break;
            case "INS":
                keyCode = KeyEvent.KEYCODE_INSERT;
                break;
            case "HOME":
                keyCode = KeyEvent.KEYCODE_MOVE_HOME;
                break;
            case "END":
                keyCode = KeyEvent.KEYCODE_MOVE_END;
                break;
            case "PGUP":
                keyCode = KeyEvent.KEYCODE_PAGE_UP;
                break;
            case "PGDN":
                keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
                break;
            case "↑":
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case "←":
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case "→":
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case "↓":
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case "―":
                keyName = "-";
                break;
            default:
                break;
        }

        TerminalView terminalView = view.findViewById(R.id.terminal_view);
        if (keyCode > 0) {
            terminalView.onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            keyName.codePoints().forEach(codePoint -> terminalView.inputCodePoint(codePoint,
                false, false));
        }
    }
}
