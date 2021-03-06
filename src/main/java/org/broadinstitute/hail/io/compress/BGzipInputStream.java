package org.broadinstitute.hail.io.compress;

import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.io.compress.SplitCompressionInputStream;
import org.apache.hadoop.io.compress.SplittableCompressionCodec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

class BGzipInputStream extends SplitCompressionInputStream {
    private static final int BGZF_MAX_BLOCK_SIZE = 64 * 1024;
    private static final int INPUT_BUFFER_CAPACITY = 2 * BGZF_MAX_BLOCK_SIZE;
    private static final int OUTPUT_BUFFER_CAPACITY = BGZF_MAX_BLOCK_SIZE;

    private class BGzipHeader {
        /* `bsize' is the size of the current BGZF block.
           It is the `BSIZE' entry of the BGZF extra subfield + 1.  */
        int bsize = 0;

        int isize = 0;

        public BGzipHeader(byte[] buf, int off, int bufSize) throws ZipException {
            if (off + 26 > bufSize)
                throw new ZipException();

            if ((buf[off] & 0xff) != 31
                    || (buf[off + 1] & 0xff) != 139
                    || (buf[off + 2] & 0xff) != 8)
                throw new ZipException();

            // FEXTRA set
            int flg = (buf[off + 3] & 0xff);
            if ((flg & 4) != 4)
                throw new ZipException();

            int xlen = (buf[off + 10] & 0xff) | ((buf[off + 11] & 0xff) << 8);
            if (xlen < 6
                || off + 12 + xlen > bufSize)
                throw new ZipException();

            boolean foundBGZFExtraField = false;
            int i = off + 12;
            for (; i < off + 12 + xlen;) {
                if (i + 4 > bufSize)
                    throw new ZipException();

                int extraFieldLen = (buf[i + 2] & 0xff) | ((buf[i + 3] & 0xff) << 8);
                if (i + 4 + extraFieldLen > bufSize)
                    throw new ZipException();

                if ((buf[i] & 0xff) == 66 && (buf[i + 1] & 0xff) == 67) {
                    if (extraFieldLen != 2)
                        throw new ZipException();
                    foundBGZFExtraField = true;
                    bsize = ((buf[i + 4] & 0xff) | ((buf[i + 5] & 0xff) << 8)) + 1;
                }

                i += 4 + extraFieldLen;
            }
            if (i != off + 12 + xlen)
                throw new ZipException();
            if (!foundBGZFExtraField
                    || bsize > BGZF_MAX_BLOCK_SIZE)
                throw new ZipException();
            if (off + bsize > bufSize)
                throw new ZipException();

            isize = ((buf[off + bsize - 4] & 0xff)
                    | ((buf[off + bsize - 3] & 0xff) << 8)
                    | ((buf[off + bsize - 2] & 0xff) << 16)
                    | ((buf[off + bsize - 1] & 0xff) << 24));
            if (isize > BGZF_MAX_BLOCK_SIZE)
                throw new ZipException();
        }
    }

    BGzipHeader bgzipHeader;

    final byte[] inputBuffer = new byte[INPUT_BUFFER_CAPACITY];
    int inputBufferSize = 0;
    int inputBufferPos = 0;

    /* `inputBufferInPos' is the position in the compressed input stream corresponding to `inputBuffer[0]'. */
    long inputBufferInPos = 0;

    final byte[] outputBuffer = new byte[OUTPUT_BUFFER_CAPACITY];
    int outputBufferSize = 0;
    int outputBufferPos = 0;

    public BGzipInputStream(InputStream in, long start, long end, SplittableCompressionCodec.READ_MODE readMode) throws IOException {
        super(in, start, end);

        assert (readMode == SplittableCompressionCodec.READ_MODE.BYBLOCK);
        ((Seekable) in).seek(start);
        inputBufferInPos = start;
        resetState();

        // FIXME adjust start and end
    }

    @Override
    public long getPos() {
        return inputBufferInPos;
    }

    public BGzipInputStream(InputStream in) throws IOException {
        this(in, 0L, Long.MAX_VALUE, SplittableCompressionCodec.READ_MODE.BYBLOCK);
    }

    private void fillInputBuffer() throws IOException {
        int newSize = inputBufferSize - inputBufferPos;

        System.arraycopy(inputBuffer, inputBufferPos, inputBuffer, 0, newSize);
        inputBufferInPos += inputBufferPos;
        inputBufferSize = newSize;
        inputBufferPos = 0;

        int needed = inputBuffer.length - inputBufferSize;
        while (needed > 0) {
            int result = in.read(inputBuffer, inputBufferSize, needed);
            if (result < 0)
                break;
            inputBufferSize += result;
            needed = inputBuffer.length - inputBufferSize;
        }
    }

    private void decompressNextBlock() throws IOException {
        outputBufferSize = 0;
        outputBufferPos = 0;

        fillInputBuffer();
        assert (inputBufferPos == 0);
        if (inputBufferSize != 0
            // && inputBufferInPos <= getAdjustedEnd()
                ) {
            bgzipHeader = new BGzipHeader(inputBuffer, inputBufferPos, inputBufferSize);
        } else {
            bgzipHeader = null;
            return;
        }

        int bsize = bgzipHeader.bsize,
                isize = bgzipHeader.isize;

        inputBufferPos += bsize;
        if (isize == 0) {
            decompressNextBlock();
            return;
        }

        // FIXME tune buffer
        InputStream decompIS
                = new GZIPInputStream(new ByteArrayInputStream(inputBuffer, 0, bsize));

        while (outputBufferSize < isize) {
            int result = decompIS.read(outputBuffer, outputBufferSize, isize - outputBufferSize);
            if (result < 0)
                throw new ZipException();
            outputBufferSize += result;
        }

        decompIS.close();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (outputBufferPos == outputBufferSize)
            decompressNextBlock();
        if (outputBufferSize == 0)
            return -1;  // EOF
        assert(outputBufferPos != outputBufferSize);

        int toCopy = Math.min(len, outputBufferSize - outputBufferPos);
        System.arraycopy(outputBuffer, outputBufferPos, b, off, toCopy);
        outputBufferPos += toCopy;

        return toCopy;
    }

    public int read() throws IOException {
        byte b[] = new byte[1];
        int result = this.read(b, 0, 1);
        return (result < 0) ? result : (b[0] & 0xff);
    }

    public void resetState() throws IOException {
        inputBufferSize = 0;
        inputBufferPos = 0;
        inputBufferInPos = ((Seekable) in).getPos();

        outputBufferSize = 0;
        outputBufferPos = 0;

        // find first block
        fillInputBuffer();
        boolean foundBlock = false;
        for (int i = 0; i < inputBufferSize - 1; ++i) {
            if ((inputBuffer[i] & 0xff) == 31
                    && (inputBuffer[i + 1] & 0xff) == 139) {
                try {
                    new BGzipHeader(inputBuffer, i, inputBufferSize);

                    inputBufferPos = i;
                    foundBlock = true;
                    break;
                } catch (ZipException e) {

                }
            }
        }

        if (!foundBlock) {
            assert (inputBufferSize < BGZF_MAX_BLOCK_SIZE);
            inputBufferPos = inputBufferSize;
        }
    }
}
