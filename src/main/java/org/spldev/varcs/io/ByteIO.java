/*
 * Copyright (C) 2023 Sebastian Krieter
 *
 * This file is part of evaluation-variant-inc.
 *
 * evaluation-variant-inc is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation-variant-inc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation-variant-inc. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <> for further information.
 */
package org.spldev.varcs.io;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

public abstract class ByteIO<T> {

    private static final int BUFFER_SIZE = 16_777_216;

    protected OutputStream out;
    protected InputStream in;

    protected final byte[] integerBytes = new byte[Integer.BYTES];

    protected long readBytes = 0;
    protected long totalBytes = 0;

    protected void writeFile(T object, Path file) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(
                Files.newOutputStream(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING),
                BUFFER_SIZE)) {
            this.out = out;
            write(object);
            out.flush();
        }
    }

    protected T readFile(Path file) throws IOException {
        try (InputStream in =
                new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ), BUFFER_SIZE)) {
            totalBytes = file.toFile().length();
            this.in = in;
            return read();
        }
    }

    protected abstract void write(T object) throws IOException;

    protected abstract T read() throws IOException;

    protected void writeBytes(byte[] bytes) throws IOException {
        writeInt(bytes.length);
        out.write(bytes);
    }

    protected void writeString(String string) throws IOException {
        writeBytes(string.getBytes(StandardCharsets.UTF_8));
    }

    protected byte[] readBytes() throws IOException {
        final byte[] bytes = new byte[readInt()];
        final int byteCount = in.read(bytes, 0, bytes.length);
        if (byteCount != bytes.length) {
            throw new IOException();
        }
        readBytes += byteCount;
        return bytes;
    }

    protected String readString() throws IOException {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    protected void writeInt(int value) throws IOException {
        integerBytes[0] = (byte) ((value >>> 24) & 0xff);
        integerBytes[1] = (byte) ((value >>> 16) & 0xff);
        integerBytes[2] = (byte) ((value >>> 8) & 0xff);
        integerBytes[3] = (byte) (value & 0xff);
        out.write(integerBytes);
    }

    protected void writeByte(byte value) throws IOException {
        out.write(value);
    }

    protected void writeBool(boolean value) throws IOException {
        out.write(value ? 1 : 0);
    }

    protected int readInt() throws IOException {
        final int byteCount = in.read(integerBytes, 0, integerBytes.length);
        if (byteCount != integerBytes.length) {
            throw new IOException();
        }
        readBytes += 4;
        return ((integerBytes[0] & 0xff) << 24)
                | ((integerBytes[1] & 0xff) << 16)
                | ((integerBytes[2] & 0xff) << 8)
                | ((integerBytes[3] & 0xff));
    }

    protected byte readByte() throws IOException {
        final int readByte = in.read();
        if (readByte < 0) {
            throw new IOException();
        }
        readBytes += 1;
        return (byte) readByte;
    }

    protected boolean readBool() throws IOException {
        final int boolByte = in.read();
        if (boolByte < 0) {
            throw new IOException();
        }
        readBytes += 1;
        return boolByte == 1;
    }
}
