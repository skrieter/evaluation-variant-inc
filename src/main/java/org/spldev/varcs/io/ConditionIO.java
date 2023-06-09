package org.spldev.varcs.io;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.prop4j.*;
import org.spldev.varcs.*;

import de.featjar.util.logging.Logger;

public class ConditionIO extends ByteIO<NodeDictionary> {

	public static void write(NodeDictionary nodeDictionary, Path path) throws IOException {
		new ConditionIO().writeFile(nodeDictionary, path);
	}

	public static NodeDictionary read(Path path) throws IOException {
		return new ConditionIO().readFile(path);
	}

	protected static final byte BYTE_POSITIVE_LITERAL = 1;
	protected static final byte BYTE_NEGATIVE_LITERAL = 2;
	protected static final byte BYTE_NOT = 3;
	protected static final byte BYTE_AND = 4;
	protected static final byte BYTE_OR = 5;
	protected static final byte BYTE_IMPLIES = 6;
	protected static final byte BYTE_EQUALS = 7;
	protected static final byte BYTE_BEGIN = 8;
	protected static final byte BYTE_END = 9;

	protected Literal[] literals;
	protected HashMap<Node, Node> nodeMap;
	protected ArrayList<Node> nodeStack;
	protected int numberOfVariables = 0;

	private NodeDictionary nodes;

	@Override
	public void write(NodeDictionary nodes) throws IOException {
		this.nodes = nodes;
		nodes.refreshVariableIndex();

		writeInt(nodes.getVariables().size());
		for (final String variableName : nodes.getVariables()) {
			writeString(variableName);
		}

		writeInt(nodes.getConditions().size());
		for (final Node node : nodes.getConditions()) {
			writeNode(node);
		}
	}

	@Override
	public NodeDictionary read() throws IOException {
		nodes = new NodeDictionary();
		numberOfVariables = readInt();
		literals = new Literal[2 * numberOfVariables];
		for (int i = 0; i < numberOfVariables; i++) {
			final String literalVar = readString();
			literals[i] = new Literal(literalVar, true);
			literals[i + numberOfVariables] = new Literal(literalVar, false);
		}

		nodeMap = new HashMap<>();
		nodeStack = new ArrayList<>();

		final int numberOfConditions = readInt();
		final int percent = numberOfConditions >= 100 ? numberOfConditions / 100 : 1;
		int progress = 0;
		for (int i = 0; i < numberOfConditions; i++) {
			if (i > progress) {
				Logger.logProgress(
					"Condition " + i + "/" + numberOfConditions + " (" + readBytes + "/" + totalBytes + ")");
				progress += percent;
			}
			nodes.putIndex(readNode());
		}
		return nodes;
	}

	protected void writeNode(Node node) throws IOException {
		if (node != null) {
			writeNodeRec(node);
		}
		writeByte(BYTE_END);
	}

	protected void writeNodeRec(Node node) throws IOException {
		if (node instanceof Literal) {
			final Literal literal = (Literal) node;
			writeByte(literal.positive ? BYTE_POSITIVE_LITERAL : BYTE_NEGATIVE_LITERAL);
			writeInt(nodes.getVariableIndex(String.valueOf(literal.var)));
		} else if (node instanceof And) {
			writeByte(BYTE_BEGIN);
			for (final Node child : node.getChildren()) {
				writeNodeRec(child);
			}
			writeByte(BYTE_AND);
		} else if (node instanceof Or) {
			writeByte(BYTE_BEGIN);
			for (final Node child : node.getChildren()) {
				writeNodeRec(child);
			}
			writeByte(BYTE_OR);
		} else {
			for (final Node child : node.getChildren()) {
				writeNodeRec(child);
			}
			if (node instanceof Implies) {
				writeByte(BYTE_IMPLIES);
			} else if (node instanceof Equals) {
				writeByte(BYTE_EQUALS);
			} else if (node instanceof Not) {
				writeByte(BYTE_NOT);
			} else {
				throw new IOException();
			}
		}
	}

	protected Node readNode() throws IOException {
		nodeStack.clear();
		readLoop: while (true) {
			final byte readByte = readByte();
			switch (readByte) {
			case BYTE_POSITIVE_LITERAL:
				nodeStack.add(literals[readInt()]);
				break;
			case BYTE_NEGATIVE_LITERAL:
				nodeStack.add(literals[readInt() + numberOfVariables]);
				break;
			case BYTE_BEGIN:
				nodeStack.add(null);
				break;
			case BYTE_END:
				break readLoop;
			case BYTE_AND: {
				final int size = nodeStack.size();
				int count = size;
				final ListIterator<Node> it = nodeStack.listIterator(size);
				while (it.previous() != null) {
					count--;
				}
				final List<Node> subList = nodeStack.subList(count, size);
				nodeStack.set(count - 1, new And(subList));
				subList.clear();
				break;
			}
			case BYTE_OR: {
				final int size = nodeStack.size();
				int count = size;
				final ListIterator<Node> it = nodeStack.listIterator(size);
				while (it.previous() != null) {
					count--;
				}
				final List<Node> subList = nodeStack.subList(count, size);
				nodeStack.set(count - 1, new Or(subList));
				subList.clear();
				break;
			}
			case BYTE_NOT:
				nodeStack.add(new Not(nodeStack.remove(nodeStack.size() - 1)));
				break;
			case BYTE_IMPLIES:
				nodeStack.add(
					new Implies(nodeStack.remove(nodeStack.size() - 1), nodeStack.remove(nodeStack.size() - 1)));
				break;
			case BYTE_EQUALS:
				nodeStack.add(
					new Equals(nodeStack.remove(nodeStack.size() - 1), nodeStack.remove(nodeStack.size() - 1)));
				break;
			default:
				throw new IOException();
			}
		}
		return refineNode(nodeStack.remove(nodeStack.size() - 1));
	}

	private Node refineNode(Node node) {
		final Node[] children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				final Node child = children[i];
				final Node cachedNode = nodeMap.get(child);
				if (cachedNode == null) {
					nodeMap.put(child, child);
				} else {
					children[i] = cachedNode;
				}
			}
		}
		return node;
	}

}
