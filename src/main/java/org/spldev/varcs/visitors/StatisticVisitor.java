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

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.visitor.TreeVisitor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.spldev.varcs.FileMap;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.BinaryFileNode;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.TextFileNode;

public class StatisticVisitor implements TreeVisitor<HashMap<CommitNode, StatisticVisitor.Statistic>, CommitNode> {

    public static class Statistic {
        public long activeTextFiles, activeBinaryFiles, loc;
    }

    private final GitUtils gitUtils;

    private FileMap fileMap;

    private final Set<Literal> literals;

    private final HashMap<CommitNode, Map<Object, Boolean>> visitedNodes = new HashMap<>();
    private final HashMap<CommitNode, Statistic> map = new HashMap<>();

    public StatisticVisitor(GitUtils gitUtils, FileMap fileMap, Node formula) {
        this.gitUtils = gitUtils;
        this.fileMap = fileMap;
        literals = formula.getUniqueLiterals();
    }

    @Override
    public Optional<HashMap<CommitNode, Statistic>> getResult() {
        return Optional.of(map);
    }

    @Override
    public void reset() {
        map.clear();
    }

    @Override
    public VisitorResult firstVisit(List<CommitNode> path) {
        final CommitNode currentCommit = TreeVisitor.getCurrentNode(path);

        for (final CommitNode parent : currentCommit.getParents()) {
            if (visitedNodes.get(parent) == null) {
                return VisitorResult.SkipChildren;
            }
        }

        final Map<Object, Boolean> assignment = new HashMap<>();
        for (final Literal literal : literals) {
            assignment.put(literal.var, Boolean.FALSE);
        }
        for (final CommitNode parent : currentCommit.getParents()) {
            final Map<Object, Boolean> parentAssignment = visitedNodes.get(parent);
            for (final Entry<Object, Boolean> entry : parentAssignment.entrySet()) {
                if (entry.getValue()) {
                    assignment.put(entry.getKey(), entry.getValue());
                }
            }
        }
        final String curCommitId = gitUtils.getVariable(currentCommit).orElseThrow(NullPointerException::new);
        assignment.put(curCommitId, Boolean.TRUE);
        Logger.logProgress("Inspect " + visitedNodes.size() + ": " + curCommitId);
        visitedNodes.put(currentCommit, assignment);
        test(currentCommit, assignment);
        return VisitorResult.Continue;
    }

    private void test(CommitNode curCommit, Map<Object, Boolean> assignment) {
        final Statistic statistic = new Statistic();
        map.put(curCommit, statistic);

        final List<BinaryFileNode> binaryFiles = fileMap.getActiveBinaryFileNodes(assignment);
        final List<TextFileNode> textFiles = fileMap.getActiveTextFileNodes(assignment);
        statistic.activeTextFiles = textFiles.size();
        statistic.activeBinaryFiles = binaryFiles.size();

        for (final TextFileNode textFileNode : textFiles) {
            statistic.loc += textFileNode
                    .getActiveData(assignment, fileMap.getConditionDictionary())
                    .collect(Collectors.toList())
                    .size();
        }
    }
}
