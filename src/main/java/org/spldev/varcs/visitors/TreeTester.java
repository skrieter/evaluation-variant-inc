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
package org.spldev.varcs.visitors;

import de.featjar.util.tree.visitor.TreeVisitor;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.spldev.varcs.structure.CommitNode;

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
