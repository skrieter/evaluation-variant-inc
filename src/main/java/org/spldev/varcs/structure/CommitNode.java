package org.spldev.varcs.structure;

import java.util.*;

import org.eclipse.jgit.lib.ObjectId;
import de.featjar.util.tree.structure.Tree;

public class CommitNode implements Tree<CommitNode> {

	private final ObjectId objectId;

	private final LinkedHashSet<ObjectId> branches = new LinkedHashSet<>();

	private final LinkedHashSet<CommitNode> parents = new LinkedHashSet<>();

	private final LinkedHashSet<CommitNode> children = new LinkedHashSet<>();

	public CommitNode(ObjectId objectId) {
		this.objectId = objectId;
	}

	protected CommitNode(CommitNode oldNode) {
		objectId = oldNode.objectId;
		branches.addAll(oldNode.branches);
		parents.addAll(oldNode.parents);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(objectId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		return Objects.equals(objectId, ((CommitNode) obj).objectId);
	}

	public ObjectId getObjectId() {
		return objectId;
	}

	@Override
	public void setChildren(List<? extends CommitNode> children) {
		this.children.clear();
		this.children.addAll(children);
	}

	public void addChild(CommitNode child) {
		children.add(child);
	}

	public void addParent(CommitNode parent) {
		parents.add(parent);
	}

	public void addBranch(ObjectId id) {
		branches.add(id);
	}

	public LinkedHashSet<CommitNode> getParents() {
		return parents;
	}

	public LinkedHashSet<ObjectId> getBranches() {
		return branches;
	}

	@Override
	public List<CommitNode> getChildren() {
		return new ArrayList<>(children);
	}

	@Override
	public Tree<CommitNode> cloneNode() {
		return new CommitNode(this);
	}

}
