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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.spldev.varcs.FileMap;
import org.spldev.varcs.Main;
import org.spldev.varcs.NodeDictionary;
import org.spldev.varcs.analyzer.cpp.PresenceConditionReader;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.DataNode;
import org.spldev.varcs.structure.LineNode;
import org.spldev.varcs.structure.TextFileNode;

public class AnnotationExtractor implements TreeVisitor<Void, CommitNode>, Consumer<CommitNode> {

    public static final String CFileRegex = ".+[.](c|h|cxx|hxx|cpp|hpp)\\Z";

    private final GitUtils gitUtils;

    private final FileMap fileMap;
    private final NodeDictionary conditionDictionary;
    private final Set<Literal> literals;
    private final HashMap<CommitNode, Map<Object, Boolean>> visitedNodes = new HashMap<>();

    public AnnotationExtractor(GitUtils gitUtils, FileMap fileMap, Node formula) {
        this.gitUtils = gitUtils;
        this.fileMap = fileMap;
        literals = formula.getUniqueLiterals();
        conditionDictionary = fileMap.getConditionDictionary();
    }

    @Override
    public VisitorResult firstVisit(List<CommitNode> path) {
        return extract(TreeVisitor.getCurrentNode(path));
    }

    @Override
    public void accept(CommitNode currentCommit) {
        extract(currentCommit);
    }

    public VisitorResult extract(CommitNode currentCommit) {
        for (final CommitNode parent : currentCommit.getParents()) {
            final Map<Object, Boolean> parentAssignment = visitedNodes.get(parent);
            if (parentAssignment == null) {
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
        try {
            Logger.logProgress(visitedNodes.size() + ": " + gitUtils.getCommitString(currentCommit));
            final String curCommitId = gitUtils.getVariable(currentCommit).orElseThrow(NullPointerException::new);
            assignment.put(curCommitId, Boolean.TRUE);
            if (visitedNodes.putIfAbsent(currentCommit, assignment) == null) {
                extractAnnotations(currentCommit, assignment);
            }
            return VisitorResult.Continue;
        } catch (final Exception e) {
            Logger.logError(e);
            return VisitorResult.SkipAll;
        }
    }

    public void extractAnnotations(CommitNode curCommit, Map<Object, Boolean> assignment) {
        Main.tabFormatter.incTabLevel();

        final Literal commitLiteral =
                new Literal(gitUtils.getVariable(curCommit).orElseThrow(NullPointerException::new), true);

        final ArrayList<TextFileNode> values =
                new ArrayList<>(fileMap.getTextFileMap().values());
        values.parallelStream()
                .filter(f -> Paths.get(f.getPath()).getFileName().toString().matches(CFileRegex)
                        && f.isActive(assignment, conditionDictionary))
                .forEach(fileNode -> {
                    final List<LineNode> lineNodes = fileNode.getActiveData(assignment, conditionDictionary)
                            .collect(Collectors.toList());
                    final List<String> lines =
                            lineNodes.stream().map(DataNode::getData).collect(Collectors.toList());
                    final List<Node> pcList = extractPresenceConditions(lines);

                    if (pcList != null) {
                        Node chachedPCNode = null;
                        Node cachedCondition = null;
                        Node cachedExtendedCondition = null;
                        Node cachedLastCondition = null;
                        final Iterator<Node> iterator = pcList.iterator();
                        for (final LineNode lineNode : lineNodes) {
                            final Node pcNode = iterator.next();
                            if (pcNode != null) {
                                if (chachedPCNode != pcNode) {
                                    chachedPCNode = pcNode;

                                    cachedCondition = new And(chachedPCNode, commitLiteral);
                                }
                                List<Node> ppConditions = lineNode.getPPConditions();
                                if (ppConditions == null) {
                                    ppConditions = new ArrayList<>(1);
                                    ppConditions.add(cachedCondition);
                                    lineNode.setPPConditions(ppConditions);
                                } else {
                                    final Node lastCondition = ppConditions.get(ppConditions.size() - 1);
                                    if (lastCondition.getChildren()[0] == chachedPCNode) {
                                        if (cachedLastCondition != lastCondition) {
                                            cachedLastCondition = lastCondition;
                                            final int oldLength = lastCondition.getChildren().length;
                                            final Node[] newChildren =
                                                    Arrays.copyOf(lastCondition.getChildren(), oldLength + 1);
                                            newChildren[oldLength] = commitLiteral;
                                            cachedExtendedCondition = new And(newChildren);
                                        }
                                        ppConditions.set(ppConditions.size() - 1, cachedExtendedCondition);
                                    } else {
                                        ppConditions.add(cachedCondition);
                                    }
                                }
                            }
                        }
                    }
                });
        Main.tabFormatter.decTabLevel();
    }

    public static List<Node> extractPresenceConditions(final List<String> lines) {
        List<Node> pcList = null;
        try {
            pcList = new PresenceConditionReader().extractPresenceConditions(lines);
        } catch (final Exception e) {
            Logger.logError(e);
        }
        return pcList;
    }
}
