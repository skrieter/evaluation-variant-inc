package org.spldev.varcs;

import java.util.*;
import java.util.stream.*;

import org.spldev.varcs.structure.*;

public class FileMap {

	private NodeDictionary nodeDictionary = new NodeDictionary();

	private final HashMap<String, TextFileNode> textFileMap = new HashMap<>();
	private final HashMap<String, BinaryFileNode> binaryFileMap = new HashMap<>();

	public NodeDictionary getConditionDictionary() {
		return nodeDictionary;
	}

	public void setConditionDictionary(NodeDictionary nodeDictionary) {
		this.nodeDictionary = nodeDictionary;
	}

	public HashMap<String, BinaryFileNode> getBinaryFileMap() {
		return binaryFileMap;
	}

	public HashMap<String, TextFileNode> getTextFileMap() {
		return textFileMap;
	}

	public void refreshNodeDictionary() {
		final NodeDictionary newNodeDictionary = new NodeDictionary();
		final int[] newIndex = new int[nodeDictionary.getConditions().size()];
		Arrays.fill(newIndex, -1);

		for (final TextFileNode fileNode : textFileMap.values()) {
			fileNode.setCondition(getNewIndex(newNodeDictionary, newIndex, fileNode.getCondition()));
			for (final LineNode lineNode : fileNode.getDataNodes()) {
				lineNode.setCondition(getNewIndex(newNodeDictionary, newIndex, lineNode.getCondition()));
				final int presenceCondition = lineNode.getPresenceCondition();
				if (presenceCondition >= 0) {
					lineNode.setPresenceCondition(getNewIndex(newNodeDictionary, newIndex, presenceCondition));
				}
			}
		}
		for (final BinaryFileNode fileNode : binaryFileMap.values()) {
			fileNode.setCondition(getNewIndex(newNodeDictionary, newIndex, fileNode.getCondition()));
			for (final DataNode<byte[]> dataNode : fileNode.getDataNodes()) {
				dataNode.setCondition(getNewIndex(newNodeDictionary, newIndex, dataNode.getCondition()));
			}
		}
		nodeDictionary = newNodeDictionary;
	}

	private int getNewIndex(NodeDictionary newNodeDictionary, int[] newIndex, int oldConditionIndex) {
		int newConditionIndex = newIndex[oldConditionIndex];
		if (newConditionIndex < 0) {
			newConditionIndex = newNodeDictionary.putIndex(nodeDictionary.getCondition(oldConditionIndex));
			newIndex[oldConditionIndex] = newConditionIndex;
		}
		return newConditionIndex;
	}

	public List<BinaryFileNode> getActiveBinaryFileNodes(Map<Object, Boolean> assignment) {
		return binaryFileMap.values().stream().filter(f -> f.isActive(assignment, nodeDictionary)).collect(Collectors
			.toList());
	}

	public List<TextFileNode> getActiveTextFileNodes(Map<Object, Boolean> assignment) {
		return textFileMap.values().stream().filter(f -> f.isActive(assignment, nodeDictionary)).collect(Collectors
			.toList());
	}

	public Optional<FileNode<?>> getFileNode(String path, Map<Object, Boolean> assignment) {
		FileNode<?> fileNode = textFileMap.get(path);
		if ((fileNode != null) && fileNode.isActive(assignment, nodeDictionary)) {
			return Optional.of(fileNode);
		}
		fileNode = binaryFileMap.get(path);
		if ((fileNode != null) && fileNode.isActive(assignment, nodeDictionary)) {
			return Optional.of(fileNode);
		}
		return Optional.empty();
	}

	public TextFileNode getTextFileNode(String path) {
		synchronized (textFileMap) {
			return textFileMap.get(path);
		}
	}

	public BinaryFileNode getBinaryFileNode(String path) {
		synchronized (binaryFileMap) {
			return binaryFileMap.get(path);
		}
	}

	public void addTextNode(TextFileNode textNode) {
		synchronized (textFileMap) {
			textFileMap.put(textNode.getPath(), textNode);
		}
	}

	public void addBinaryNode(BinaryFileNode binaryNode) {
		synchronized (binaryFileMap) {
			binaryFileMap.put(binaryNode.getPath(), binaryNode);
		}
	}

}
