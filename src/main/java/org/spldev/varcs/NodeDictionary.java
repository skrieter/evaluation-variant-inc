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
package org.spldev.varcs;

import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import org.prop4j.*;

public class NodeDictionary {

    private final Object2IntOpenHashMap<Node> nodeToIndex = new Object2IntOpenHashMap<>();
    private final ArrayList<Node> indexToNode = new ArrayList<>();

    private final Object2IntOpenHashMap<String> variableToIndex = new Object2IntOpenHashMap<>();
    private final ArrayList<String> indexToVariable = new ArrayList<>();

    public void refreshVariableIndex() {
        variableToIndex.clear();
        int literalIndex = 0;
        for (final Node node : indexToNode) {
            for (final Literal literal : node.getUniqueLiterals()) {
                final String var = String.valueOf(literal.var);
                if (!variableToIndex.containsKey(var)) {
                    variableToIndex.put(var, literalIndex++);
                    indexToVariable.add(var);
                }
            }
        }
    }

    public NodeDictionary() {
        nodeToIndex.defaultReturnValue(-1);
    }

    public void clear() {
        nodeToIndex.clear();
        indexToNode.clear();
    }

    public ArrayList<Node> getConditions() {
        return indexToNode;
    }

    public Node getCondition(int index) {
        return indexToNode.get(index);
    }

    public ArrayList<String> getVariables() {
        return indexToVariable;
    }

    public String getVariable(int index) {
        return indexToVariable.get(index);
    }

    public int getVariableIndex(String variableName) {
        return variableToIndex.getInt(variableName);
    }

    public int getIndexSynced(Node node) {
        if (node == null) {
            return -1;
        }
        int index;
        synchronized (nodeToIndex) {
            index = nodeToIndex.getInt(node);
            if (index < 0) {
                index = addNode(node);
            }
        }
        return index;
    }

    public int getIndex(Node node) {
        if (node == null) {
            return -1;
        }
        int index = nodeToIndex.getInt(node);
        if (index < 0) {
            index = addNode(node);
        }
        return index;
    }

    public int putIndex(Node node) {
        return addNode(node);
    }

    @SuppressWarnings("unchecked")
    public <T extends Node> T getCondition(T node) {
        if (node != null) {
            final int index = nodeToIndex.getInt(node);
            if (index >= 0) {
                return (T) indexToNode.get(index);
            }
        }
        return null;
    }

    public void putConditionSynced(Node node) {
        if (node != null) {
            synchronized (nodeToIndex) {
                if (!nodeToIndex.containsKey(node)) {
                    addNode(node);
                }
            }
        }
    }

    public void putCondition(Node node) {
        if ((node != null) && !nodeToIndex.containsKey(node)) {
            addNode(node);
        }
    }

    private int addNode(Node node) {
        final int index = indexToNode.size();
        nodeToIndex.put(node, index);
        indexToNode.add(node);
        return index;
    }

    private static class StackEntry {
        private Node node;
        private int index = 0;

        public StackEntry(Node node) {
            this.node = node;
        }
    }

    @SuppressWarnings("unused")
    private int addNodeTree(Node node) {
        final LinkedList<StackEntry> stack = new LinkedList<>();
        final ArrayList<Node> newNodes = new ArrayList<>();
        stack.push(new StackEntry(node));
        int index = -1;

        while (!stack.isEmpty()) {
            final StackEntry entry = stack.peek();
            Node child = entry.node;
            if ((child.getChildren() != null) && (entry.index < child.getChildren().length)) {
                stack.push(new StackEntry(child.getChildren()[entry.index++]));
            } else {
                if (child.getChildren() != null) {
                    final List<Node> newChildren =
                            newNodes.subList(newNodes.size() - child.getChildren().length, newNodes.size());
                    child.setChildren(newChildren.toArray(new Node[0]));
                    newChildren.clear();
                }
                index = nodeToIndex.getInt(child);
                if (index < 0) {
                    index = indexToNode.size();
                    nodeToIndex.put(child, index);
                    indexToNode.add(child);
                } else {
                    child = indexToNode.get(index);
                }
                newNodes.add(child);
                stack.pop();
            }
        }
        return index;
    }
}
