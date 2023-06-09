package org.spldev.varcs.structure;

import java.util.*;
import java.util.stream.*;

import org.spldev.varcs.*;

public abstract class FileNode<T extends DataNode<?>> extends ConditionalNode {

	protected final List<T> data = new ArrayList<>();
	protected final String path;

	public FileNode(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

	public List<T> getDataNodes() {
		return data;
	}

	public Stream<T> getActiveData(Map<Object, Boolean> assignment, NodeDictionary nodeDictionary) {
		return data.stream().filter(node -> node.isActive(assignment, nodeDictionary));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(path);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		return Objects.equals(path, ((FileNode<?>) obj).path);
	}

}
