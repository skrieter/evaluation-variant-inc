package org.spldev.varcs.io;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import org.spldev.varcs.structure.*;

public class BinaryFileIO extends ByteIO<BinaryFileNode> {

	public static void write(BinaryFileNode fileMap, Path path) throws IOException {
		new BinaryFileIO().writeFile(fileMap, path);
	}

	public static BinaryFileNode read(Path path) throws IOException {
		return new BinaryFileIO().readFile(path);
	}

	@Override
	protected void write(BinaryFileNode fileNode) throws IOException {
		writeString(fileNode.getPath());
		writeInt(fileNode.getCondition());
		writeInt(fileNode.getDataNodes().size());

		writeConditions(fileNode);
		writeData(fileNode.getDataNodes());
	}

	private void writeConditions(BinaryFileNode fileNode) throws IOException {
		int lastCondition = -2;
		int countCondition = -1;
		for (final DataNode<byte[]> lineNode : fileNode.getDataNodes()) {
			final int condition = lineNode.getCondition();
			if (lastCondition == condition) {
				countCondition--;
			} else {
				if (countCondition < -1) {
					writeInt(countCondition);
					countCondition = -1;
				}
				lastCondition = condition;
				writeInt(condition);
			}
		}
		if (countCondition < -1) {
			writeInt(countCondition);
			countCondition = -1;
		}
	}

	private void writeData(List<DataNode<byte[]>> lineNodes) throws IOException {
		for (final DataNode<byte[]> lineNode : lineNodes) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			try (DeflaterOutputStream dos = new DeflaterOutputStream(os)) {
				dos.write(lineNode.getData());
			}
			writeBytes(os.toByteArray());
		}
	}

	@Override
	protected BinaryFileNode read() throws IOException {
		final String path = readString();
		final BinaryFileNode fileNode = new BinaryFileNode(path);
		fileNode.setCondition(readInt());
		final int numberOfData = readInt();

		final int[] conditions = readConditions(numberOfData);
		final byte[][] binaryData = readBinaryData(numberOfData);

		for (int j = 0; j < numberOfData; j++) {
			final DataNode<byte[]> dataNode = new DataNode<>(binaryData[j], conditions[j]);
			fileNode.getDataNodes().add(dataNode);
		}

		return fileNode;
	}

	private int[] readConditions(final int numberOfData) throws IOException {
		final int[] conditions = new int[numberOfData];
		for (int j = 0; j < numberOfData; j++) {
			final int condition = readInt();
			if (condition >= -1) {
				conditions[j] = condition;
			} else {
				final int c = conditions[j - 1];
				final int conditionCount = (-condition) - 1;
				for (int i = 0; i < conditionCount; i++) {
					conditions[j + i] = c;
				}
				j += conditionCount - 1;
			}
		}
		return conditions;
	}

	private byte[][] readBinaryData(int numberOfData) throws IOException {
		final byte[][] binaryData = new byte[numberOfData][];
		for (int j = 0; j < numberOfData; j++) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			try (InflaterOutputStream dos = new InflaterOutputStream(os)) {
				dos.write(readBytes());
			}
			binaryData[j] = os.toByteArray();
		}
		return binaryData;
	}

}
