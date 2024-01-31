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

import junit.framework.TestCase;

public class WcWidthTest extends TestCase {

	private static void assertWidthIs(int expectedWidth, int codePoint) {
		int wcWidth = WcWidth.width(codePoint);
		assertEquals(expectedWidth, wcWidth);
	}

	public void testPrintableAscii() {
		for (int i = 0x20; i <= 0x7E; i++) {
			assertWidthIs(1, i);
		}
	}

	public void testSomeWidthOne() {
		assertWidthIs(1, 'å');
		assertWidthIs(1, 'ä');
		assertWidthIs(1, 'ö');
		assertWidthIs(1, 0x23F2);
	}

	public void testSomeWide() {
		assertWidthIs(2, 'Ａ');
		assertWidthIs(2, 'Ｂ');
		assertWidthIs(2, 'Ｃ');
		assertWidthIs(2, '中');
		assertWidthIs(2, '文');

		assertWidthIs(2, 0x679C);
		assertWidthIs(2, 0x679D);

		assertWidthIs(2, 0x2070E);
		assertWidthIs(2, 0x20731);

		assertWidthIs(1, 0x1F781);
	}

	public void testSomeNonWide() {
		assertWidthIs(1, 0x1D11E);
		assertWidthIs(1, 0x1D11F);
	}

	public void testCombining() {
		assertWidthIs(0, 0x0302);
		assertWidthIs(0, 0x0308);
		assertWidthIs(0, 0xFE0F);
	}

	public void testWordJoiner() {
		// https://en.wikipedia.org/wiki/Word_joiner
		// The word joiner (WJ) is a code point in Unicode used to separate words when using scripts
		// that do not use explicit spacing. It is encoded since Unicode version 3.2
		// (released in 2002) as U+2060 WORD JOINER (HTML &#8288;).
		// The word joiner does not produce any space, and prohibits a line break at its position.
		assertWidthIs(0, 0x2060);
	}

	public void testSofthyphen() {
		// http://osdir.com/ml/internationalization.linux/2003-05/msg00006.html:
		// "Existing implementation practice in terminals is that the SOFT HYPHEN is
		// a spacing graphical character, and the purpose of my wcwidth() was to
		// predict the advancement of the cursor position after a string is sent to
		// a terminal. Hence, I have no choice but to keep wcwidth(SOFT HYPHEN) = 1.
		// VT100-style terminals do not hyphenate."
		assertWidthIs(1, 0x00AD);
	}

	public void testHangul() {
		assertWidthIs(1, 0x11A3);
	}

	public void testEmojis() {
		assertWidthIs(2, 0x1F428); // KOALA.
		assertWidthIs(2, 0x231a);  // WATCH.
		assertWidthIs(2, 0x1F643); // UPSIDE-DOWN FACE (Unicode 8).
	}

}
