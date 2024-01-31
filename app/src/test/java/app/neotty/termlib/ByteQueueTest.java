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

public class ByteQueueTest extends TestCase {

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		if (expected.length != actual.length) {
			fail("Difference array length");
		}
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] != actual[i]) {
				fail("Inequals at index=" + i + ", expected=" + (int) expected[i] + ", actual=" + (int) actual[i]);
			}
		}
	}

	public void testCompleteWrites() throws Exception {
		ByteQueue q = new ByteQueue(10);
		assertTrue(q.write(new byte[]{1, 2, 3}, 0, 3));

		byte[] arr = new byte[10];
		assertEquals(3, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3}, new byte[]{arr[0], arr[1], arr[2]});

		assertTrue(q.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10));
		assertEquals(10, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, arr);
	}

	public void testQueueWraparound() throws Exception {
		ByteQueue q = new ByteQueue(10);

		byte[] origArray = new byte[]{1, 2, 3, 4, 5, 6};
		byte[] readArray = new byte[origArray.length];
		for (int i = 0; i < 20; i++) {
			q.write(origArray, 0, origArray.length);
			assertEquals(origArray.length, q.read(readArray, true));
			assertArrayEquals(origArray, readArray);
		}
	}

	public void testWriteNotesClosing() throws Exception {
		ByteQueue q = new ByteQueue(10);
		q.close();
		assertFalse(q.write(new byte[]{1, 2, 3}, 0, 3));
	}

	public void testReadNonBlocking() throws Exception {
		ByteQueue q = new ByteQueue(10);
		assertEquals(0, q.read(new byte[128], false));
	}

}
