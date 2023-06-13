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
