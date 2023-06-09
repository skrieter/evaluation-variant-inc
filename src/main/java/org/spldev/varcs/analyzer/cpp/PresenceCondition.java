package org.spldev.varcs.analyzer.cpp;

import java.util.*;

import org.prop4j.*;

public class PresenceCondition {

	private Node node;

	public PresenceCondition(Node node) {
		this.node = node;
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(node);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		return Objects.equals(node, ((PresenceCondition) obj).node);
	}

	@Override
	public String toString() {
		return "PresenceCondition [node=" + node + "]";
	}

}
