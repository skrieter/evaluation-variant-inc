package org.spldev.varcs.io;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import org.spldev.varcs.structure.*;

public class TextFileIO extends ByteIO<TextFileNode> {

	public static void write(TextFileNode fileMap, Path path) throws IOException {
		new TextFileIO().writeFile(fileMap, path);
	}

	public static TextFileNode read(Path path) throws IOException {
		return new TextFileIO().readFile(path);
	}

	@Override
	protected void write(TextFileNode fileNode) throws IOException {
		writeString(fileNode.getPath());
		writeInt(fileNode.getCondition());
		writeInt(fileNode.getDataNodes().size());

		writeConditions(fileNode);
		writePresenceConditions(fileNode);
		writeLines(fileNode.getDataNodes());
	}

	private void writeConditions(TextFileNode fileNode) throws IOException {
		int lastCondition = -2;
		int countCondition = -1;
		for (final LineNode lineNode : fileNode.getDataNodes()) {
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

	private void writePresenceConditions(TextFileNode fileNode) throws IOException {
		int lastCondition = -2;
		int countCondition = -1;
		for (final LineNode lineNode : fileNode.getDataNodes()) {
			final int condition = lineNode.getPresenceCondition();
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

	private void writeLines(List<LineNode> lineNodes) throws IOException {
		final byte[] newLine = "\n".getBytes(StandardCharsets.UTF_8);
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try (DeflaterOutputStream dos = new DeflaterOutputStream(os)) {
			for (final LineNode lineNode : lineNodes) {
				dos.write(lineNode.getData().getBytes(StandardCharsets.UTF_8));
				dos.write(newLine);
			}
		}
		writeBytes(os.toByteArray());
	}

	@Override
	protected TextFileNode read() throws IOException {
		final String path = readString();
		final TextFileNode fileNode = new TextFileNode(path);
		fileNode.setCondition(readInt());
		final int numberOfData = readInt();

		final int[] conditions = readConditions(numberOfData);
		final int[] presenceConditions = readConditions(numberOfData);
		final String[] lines = readLines();

		for (int j = 0; j < numberOfData; j++) {
			final LineNode lineNode = new LineNode(lines[j], conditions[j]);
			lineNode.setPresenceCondition(presenceConditions[j]);
			fileNode.getDataNodes().add(lineNode);
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

	private String[] readLines() throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try (InflaterOutputStream dos = new InflaterOutputStream(os)) {
			dos.write(readBytes());
		}
		final String string = new String(os.toByteArray(), StandardCharsets.UTF_8);
		return string.split("\\n", -1);
	}

}
