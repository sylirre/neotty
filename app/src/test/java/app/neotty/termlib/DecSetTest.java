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
package app.neotty.termlib;

/**
 * <pre>
 * "CSI ? Pm h", DEC Private Mode Set (DECSET)
 * </pre>
 * <p/>
 * and
 * <p/>
 * <pre>
 * "CSI ? Pm l", DEC Private Mode Reset (DECRST)
 * </pre>
 * <p/>
 * controls various aspects of the terminal
 */
public class DecSetTest extends TerminalTestCase {

	/** DECSET 25, DECTCEM, controls visibility of the cursor. */
	public void testEnableDisableCursor() {
		withTerminalSized(3, 3);
		assertTrue("Initially the cursor should be enabled", mTerminal.isShowingCursor());
		enterString("\033[?25l"); // Disable Cursor (DECTCEM).
		assertFalse(mTerminal.isShowingCursor());
		enterString("\033[?25h"); // Enable Cursor (DECTCEM).
		assertTrue(mTerminal.isShowingCursor());

		enterString("\033[?25l"); // Disable Cursor (DECTCEM), again.
		assertFalse(mTerminal.isShowingCursor());
		mTerminal.reset(false);
		assertTrue("Resetting the terminal should enable the cursor", mTerminal.isShowingCursor());

		enterString("\033[?25l");
		assertFalse(mTerminal.isShowingCursor());
		enterString("\033c"); // RIS resetting should enabled cursor.
		assertTrue(mTerminal.isShowingCursor());
	}

	/** DECSET 2004, controls bracketed paste mode. */
	public void testBracketedPasteMode() {
		withTerminalSized(3, 3);

		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		enterString("\033[?2004l"); // Disable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode, again.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode again should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		mTerminal.paste("\033ab\033cd\033");
		assertEquals("Pasting an escape character should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());
		mTerminal.paste("\u0081ab\u0081cd\u009F");
		assertEquals("Pasting C1 control codes should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());

		mTerminal.reset(false);
		mTerminal.paste("a");
		assertEquals("Terminal reset(false) should disable bracketed paste mode", "a", mOutput.getOutputAndClear());
	}

	/** DECSET 7, DECAWM, controls wraparound mode. */
	public void testWrapAroundMode() {
		// Default with wraparound:
		withTerminalSized(3, 3).enterString("abcd").assertLinesAre("abc", "d  ", "   ");
		// With wraparound disabled:
		withTerminalSized(3, 3).enterString("\033[?7labcd").assertLinesAre("abd", "   ", "   ");
		enterString("efg").assertLinesAre("abg", "   ", "   ");
		// Re-enabling wraparound:
		enterString("\033[?7hhij").assertLinesAre("abh", "ij ", "   ");
	}

}
