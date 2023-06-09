package org.spldev.varcs.structure;

public class DataNode<T> extends ConditionalNode {

	private final T data;

	public DataNode(T data, int conditionIndex) {
		super();
		this.conditionIndex = conditionIndex;
		this.data = data;
	}

	public T getData() {
		return data;
	}

	@Override
	public String toString() {
		return String.valueOf(data);
	}

}
