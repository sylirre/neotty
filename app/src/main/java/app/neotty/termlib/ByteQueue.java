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

/** A circular byte buffer allowing one producer and one consumer thread. */
final class ByteQueue {

    private final byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
    private boolean mOpen = true;

    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public synchronized void close() {
        mOpen = false;
        notify();
    }

    public synchronized int read(byte[] buffer, boolean block) {
        while (mStoredBytes == 0 && mOpen) {
            if (block) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            } else {
                return 0;
            }
        }
        if (!mOpen) return -1;

        int totalRead = 0;
        int bufferLength = mBuffer.length;
        boolean wasFull = bufferLength == mStoredBytes;
        int length = buffer.length;
        int offset = 0;
        while (length > 0 && mStoredBytes > 0) {
            int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
            int bytesToCopy = Math.min(length, oneRun);
            System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
            mHead += bytesToCopy;
            if (mHead >= bufferLength) mHead = 0;
            mStoredBytes -= bytesToCopy;
            length -= bytesToCopy;
            offset += bytesToCopy;
            totalRead += bytesToCopy;
        }
        if (wasFull) notify();
        return totalRead;
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     * <p/>
     * Returns whether the output was totally written, false if it was closed before.
     */
    public boolean write(byte[] buffer, int offset, int lengthToWrite) {
        if (lengthToWrite + offset > buffer.length) {
            throw new IllegalArgumentException("length + offset > buffer.length");
        } else if (lengthToWrite <= 0) {
            throw new IllegalArgumentException("length <= 0");
        }

        final int bufferLength = mBuffer.length;

        synchronized (this) {
            while (lengthToWrite > 0) {
                while (bufferLength == mStoredBytes && mOpen) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
                if (!mOpen) return false;
                final boolean wasEmpty = mStoredBytes == 0;
                int bytesToWriteBeforeWaiting = Math.min(lengthToWrite, bufferLength - mStoredBytes);
                lengthToWrite -= bytesToWriteBeforeWaiting;

                while (bytesToWriteBeforeWaiting > 0) {
                    int tail = mHead + mStoredBytes;
                    int oneRun;
                    if (tail >= bufferLength) {
                        // Buffer: [.............]
                        // ________________H_______T
                        // =>
                        // Buffer: [.............]
                        // ___________T____H
                        // onRun= _____----_
                        tail = tail - bufferLength;
                        oneRun = mHead - tail;
                    } else {
                        oneRun = bufferLength - tail;
                    }
                    int bytesToCopy = Math.min(oneRun, bytesToWriteBeforeWaiting);
                    System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
                    offset += bytesToCopy;
                    bytesToWriteBeforeWaiting -= bytesToCopy;
                    mStoredBytes += bytesToCopy;
                }
                if (wasEmpty) notify();
            }
        }
        return true;
    }
}
