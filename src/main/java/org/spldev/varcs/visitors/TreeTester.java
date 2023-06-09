package org.spldev.varcs.visitors;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.spldev.varcs.structure.CommitNode;

import de.featjar.util.tree.visitor.TreeVisitor;

public class TreeTester implements TreeVisitor<Void, CommitNode> {

	private HashSet<CommitNode> visited = new HashSet<>();

	@Override
	public void reset() {
		visited.clear();
	}

	@Override
	public VisitorResult firstVisit(List<CommitNode> path) {
		final CommitNode currentCommit = TreeVisitor.getCurrentNode(path);

		final List<CommitNode> children = currentCommit.getChildren();
		for (final CommitNode child : children) {
			if (!child.getParents().contains(currentCommit)) {
				System.err.println(child.getObjectId().getName().substring(0, 6));
				throw new RuntimeException("inconsistent tree");
			}
		}

		final LinkedHashSet<CommitNode> parents = currentCommit.getParents();
		for (final CommitNode parent : parents) {
			if (!parent.getChildren().contains(currentCommit)) {
				System.err.println(parent.getObjectId().getName().substring(0, 6));
				throw new RuntimeException("inconsistent tree");
			}
		}

		boolean allParentsVisited = true;
		for (final CommitNode commitNode : currentCommit.getParents()) {
			if (!visited.contains(commitNode)) {
				allParentsVisited = false;
				break;
			}
		}
		if (allParentsVisited) {
			visited.add(currentCommit);
			return VisitorResult.Continue;
		} else {
			return VisitorResult.SkipChildren;
		}

	}

}
