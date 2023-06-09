package org.spldev.varcs.visitors;

import java.util.LinkedHashSet;
import java.util.List;

import org.spldev.varcs.structure.CommitNode;

import de.featjar.util.tree.visitor.TreeVisitor;

public class NodeCollector implements TreeVisitor<Void, CommitNode> {

	private final LinkedHashSet<CommitNode> nodeList = new LinkedHashSet<>();

	public LinkedHashSet<CommitNode> getNodeList() {
		return nodeList;
	}

	@Override
	public void reset() {
		nodeList.clear();
	}

	@Override
	public VisitorResult firstVisit(List<CommitNode> path) {
		if (nodeList.add(TreeVisitor.getCurrentNode(path))) {
			return VisitorResult.Continue;
		} else {
			return VisitorResult.SkipChildren;
		}
	}

}
