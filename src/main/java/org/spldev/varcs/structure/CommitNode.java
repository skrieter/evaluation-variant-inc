/*
 * Copyright (C) 2023 Sebastian Krieter
 *
 * This file is part of evaluation-variant-inc.
 *
 * evaluation-variant-inc is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation-variant-inc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation-variant-inc. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <> for further information.
 */
package org.spldev.varcs.structure;

import de.featjar.util.tree.structure.Tree;
import java.util.*;
import org.eclipse.jgit.lib.ObjectId;

public class CommitNode implements Tree<CommitNode> {

    public static class HashList<T> extends LinkedHashSet<T> implements List<T> {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T get(int index) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException();
            }
            Iterator<T> iterator = iterator();
            for (int i = 0; i < index; i++) {
                iterator.next();
            }
            return iterator.next();
        }

        @Override
        public T set(int index, T element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int index, T element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T remove(int index) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException();
            }
            Iterator<T> iterator = iterator();
            for (int i = 0; i < index; i++) {
                iterator.next();
            }
            T element = iterator.next();
            iterator.remove();
            return element;
        }

        @Override
        public int indexOf(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int lastIndexOf(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<T> listIterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<T> listIterator(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<T> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }
    }

    private final ObjectId objectId;

    private final LinkedHashSet<ObjectId> branches = new LinkedHashSet<>();

    private final LinkedHashSet<CommitNode> parents = new LinkedHashSet<>();

    private final HashList<CommitNode> children = new HashList<>();

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
        return children;
    }

    public LinkedHashSet<CommitNode> getChildNodes() {
        return children;
    }

    @Override
    public Tree<CommitNode> cloneNode() {
        return new CommitNode(this);
    }
}
