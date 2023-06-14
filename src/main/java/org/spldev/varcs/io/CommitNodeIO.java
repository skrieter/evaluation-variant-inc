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
package org.spldev.varcs.io;

import de.featjar.util.tree.Trees;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.lib.*;
import org.spldev.varcs.structure.*;

public class CommitNodeIO extends ByteIO<CommitNode> {

    public static void write(CommitNode root, Path path) throws IOException {
        new CommitNodeIO().writeFile(root, path);
    }

    public static CommitNode read(Path path) throws IOException {
        return new CommitNodeIO().readFile(path);
    }

    @Override
    protected void write(CommitNode root) throws IOException {
        final List<CommitNode> nodes = Trees.getPreOrderList(root);
        writeInt(nodes.size());

        final int[] idSegments = new int[5];
        for (final CommitNode treeNode : nodes) {
            treeNode.getObjectId().copyRawTo(idSegments, 0);
            writeObjectId(idSegments);
        }

        for (final CommitNode treeNode : nodes) {
            final CommitNode node = treeNode;
            final LinkedHashSet<ObjectId> branches = node.getBranches();
            final LinkedHashSet<CommitNode> parents = node.getParents();
            final LinkedHashSet<CommitNode> children = node.getChildNodes();

            writeInt(branches.size());
            for (final ObjectId id : branches) {
                id.copyRawTo(idSegments, 0);
                writeObjectId(idSegments);
            }

            writeInt(parents.size());
            for (final CommitNode parent : parents) {
                parent.getObjectId().copyRawTo(idSegments, 0);
                writeObjectId(idSegments);
            }

            writeInt(children.size());
            for (final CommitNode child : children) {
                child.getObjectId().copyRawTo(idSegments, 0);
                writeObjectId(idSegments);
            }
        }
    }

    @Override
    protected CommitNode read() throws IOException {
        final HashMap<ObjectId, CommitNode> map = new HashMap<>();
        final ArrayList<CommitNode> list = new ArrayList<>();

        final int numberOfNodes = readInt();

        for (int i = 0; i < numberOfNodes; i++) {
            final ObjectId id = readObjectId();
            final CommitNode commitNode = new CommitNode(id);
            map.put(id, commitNode);
            list.add(commitNode);
        }

        for (final CommitNode commitNode : list) {
            final int numberOfBranches = readInt();
            for (int j = 0; j < numberOfBranches; j++) {
                final ObjectId id = readObjectId();
                commitNode.addBranch(id);
            }

            final int numberOfParents = readInt();
            for (int j = 0; j < numberOfParents; j++) {
                final ObjectId id = readObjectId();
                commitNode.addParent(map.get(id));
            }

            final int numberOfChildren = readInt();
            for (int j = 0; j < numberOfChildren; j++) {
                final ObjectId id = readObjectId();
                commitNode.addChild(map.get(id));
            }
        }

        for (final CommitNode commitNode : list) {
            if (commitNode.getParents().isEmpty()) {
                return commitNode;
            }
        }
        return null;
    }

    protected void writeObjectId(int[] idSegments) throws IOException {
        final byte[] bytes = new byte[idSegments.length * Integer.BYTES];
        for (int i = 0; i < idSegments.length; i++) {
            final int offset = Integer.BYTES * i;
            final int idSegment = idSegments[i];
            for (int j = 0; j < Integer.BYTES; j++) {
                bytes[offset + j] = (byte) ((idSegment >>> ((Integer.BYTES - (j + 1)) * Byte.SIZE)) & 0xff);
            }
        }
        out.write(bytes);
    }

    protected ObjectId readObjectId() throws IOException {
        final int w1 = readInt();
        final int w2 = readInt();
        final int w3 = readInt();
        final int w4 = readInt();
        final int w5 = readInt();
        return new ObjectId(w1, w2, w3, w4, w5);
    }
}
