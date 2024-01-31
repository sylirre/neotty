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
 * "\033P" is a device control string.
 */
public class DeviceControlStringTest extends TerminalTestCase {

	private static String hexEncode(String s) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
			result.append(String.format("%02X", (int) s.charAt(i)));
		return result.toString();
	}

	private void assertCapabilityResponse(String cap, String expectedResponse) {
		String input = "\033P+q" + hexEncode(cap) + "\033\\";
		assertEnteringStringGivesResponse(input, "\033P1+r" + hexEncode(cap) + "=" + hexEncode(expectedResponse) + "\033\\");
	}

	public void testReportColorsAndName() {
		// Request Termcap/Terminfo String. The string following the "q" is a list of names encoded in
		// hexadecimal (2 digits per character) separated by ; which correspond to termcap or terminfo key
		// names.
		// Two special features are also recognized, which are not key names: Co for termcap colors (or colors
		// for terminfo colors), and TN for termcap name (or name for terminfo name).
		// xterm responds with DCS 1 + r P t ST for valid requests, adding to P t an = , and the value of the
		// corresponding string that xterm would send, or DCS 0 + r P t ST for invalid requests. The strings are
		// encoded in hexadecimal (2 digits per character).
		withTerminalSized(3, 3).enterString("A");
		assertCapabilityResponse("Co", "256");
		assertCapabilityResponse("colors", "256");
		assertCapabilityResponse("TN", "xterm");
		assertCapabilityResponse("name", "xterm");
		enterString("B").assertLinesAre("AB ", "   ", "   ");
	}

	public void testReportKeys() {
		withTerminalSized(3, 3);
		assertCapabilityResponse("kB", "\033[Z");
	}

	public void testReallyLongDeviceControlString() {
		withTerminalSized(3, 3).enterString("\033P");
		for (int i = 0; i < 10000; i++) {
			enterString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
		}
		// The terminal should ignore the overlong DCS sequence and continue printing "aaa." and fill at least the first two lines with
		// them:
		assertLineIs(0, "aaa");
		assertLineIs(1, "aaa");
	}

}
