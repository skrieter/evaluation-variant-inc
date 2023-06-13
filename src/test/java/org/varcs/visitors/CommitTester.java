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
package org.varcs.visitors;

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.visitor.TreeVisitor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Assert;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.spldev.varcs.FileMap;
import org.spldev.varcs.NodeDictionary;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.BinaryFileNode;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.DataNode;
import org.spldev.varcs.structure.LineNode;
import org.spldev.varcs.structure.TextFileNode;
import org.spldev.varcs.visitors.AnnotationExtractor;

public class CommitTester implements TreeVisitor<Void, CommitNode> {

    private final GitUtils gitUtils;

    private final FileMap orgFileMap;
    private FileMap fileMap;
    private NodeDictionary nodeDictionary;

    private final Set<Literal> literals;

    private final HashMap<CommitNode, Map<Object, Boolean>> visitedNodes = new HashMap<>();

    public CommitTester(GitUtils gitUtils, FileMap fileMap, Node formula) {
        this.gitUtils = gitUtils;
        orgFileMap = fileMap;
        literals = formula.getUniqueLiterals();
        nodeDictionary = orgFileMap.getConditionDictionary();
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
        try {
            final String curCommitId = gitUtils.getVariable(currentCommit).orElseThrow(NullPointerException::new);
            assignment.put(curCommitId, Boolean.TRUE);
            Logger.logProgress(visitedNodes.size() + ": " + curCommitId);
            visitedNodes.put(currentCommit, assignment);
            return test(currentCommit, assignment);
        } catch (final IOException e) {
            Logger.logError(e);
            return VisitorResult.SkipAll;
        }
    }

    private VisitorResult test(CommitNode curCommit, Map<Object, Boolean> assignment) throws IOException {
        fileMap = new FileMap();
        fileMap.getBinaryFileMap().putAll(orgFileMap.getBinaryFileMap());
        fileMap.getTextFileMap().putAll(orgFileMap.getTextFileMap());

        final VisitorResult result = VisitorResult.Continue;
        try (TreeWalk treeWalk = new TreeWalk(gitUtils.getRepository())) {
            treeWalk.addTree(gitUtils.getRepository()
                    .parseCommit(curCommit.getObjectId())
                    .getTree());
            treeWalk.setRecursive(true);

            int fileCount = 0;
            while (treeWalk.next()) {
                final ObjectId objectId = treeWalk.getObjectId(0);
                final String pathString = treeWalk.getPathString();

                final BinaryFileNode binaryFileNode = fileMap.getBinaryFileMap().remove(pathString);
                final TextFileNode textFileNode = fileMap.getTextFileMap().remove(pathString);

                final byte[] bytes = gitUtils.getBytes(objectId);
                if (bytes == null) {
                    if ((binaryFileNode != null) && binaryFileNode.isActive(assignment, nodeDictionary)) {
                        Logger.logInfo("Should be inactive or null: ");
                        Logger.logInfo(assignment.toString());
                        Logger.logInfo(pathString);
                        Logger.logInfo(nodeDictionary
                                .getCondition(binaryFileNode.getCondition())
                                .toString());
                        Logger.logInfo("");
                        Assert.fail();
                        continue;
                    }
                    if ((textFileNode != null) && textFileNode.isActive(assignment, nodeDictionary)) {
                        Logger.logInfo("Should be inactive or null: ");
                        Logger.logInfo(assignment.toString());
                        Logger.logInfo(pathString);
                        Logger.logInfo(nodeDictionary
                                .getCondition(textFileNode.getCondition())
                                .toString());
                        Logger.logInfo("");
                        Assert.fail();
                        continue;
                    }
                } else {
                    final boolean binary = gitUtils.isBinary(bytes);
                    if (binary) {
                        if (binaryFileNode == null) {
                            Logger.logInfo("Should not be null: ");
                            Logger.logInfo(assignment.toString());
                            Logger.logInfo(pathString);
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                        if (!binaryFileNode.isActive(assignment, nodeDictionary)) {
                            Logger.logInfo("Should be active: ");
                            Logger.logInfo(assignment.toString());
                            Logger.logInfo(pathString);
                            Logger.logInfo(nodeDictionary
                                    .getCondition(binaryFileNode.getCondition())
                                    .toString());
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                        if ((textFileNode != null) && textFileNode.isActive(assignment, nodeDictionary)) {
                            Logger.logInfo("Should be a binary file: ");
                            Logger.logInfo(pathString);
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                    } else {
                        if (textFileNode == null) {
                            Logger.logInfo("Should not be null: ");
                            Logger.logInfo(assignment.toString());
                            Logger.logInfo(pathString);
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                        if (!textFileNode.isActive(assignment, nodeDictionary)) {
                            Logger.logInfo("Should be active: ");
                            Logger.logInfo(assignment.toString());
                            Logger.logInfo(pathString);
                            Logger.logInfo(nodeDictionary
                                    .getCondition(textFileNode.getCondition())
                                    .toString());
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                        if ((binaryFileNode != null) && binaryFileNode.isActive(assignment, nodeDictionary)) {
                            Logger.logInfo("Should be a text file: ");
                            Logger.logInfo(pathString);
                            Logger.logInfo("");
                            Assert.fail();
                            continue;
                        }
                        final List<LineNode> lineNodeList = textFileNode.getDataNodes();

                        final List<String> lines1 = textFileNode
                                .getActiveData(assignment, nodeDictionary) //
                                .map(DataNode::getData) //
                                .collect(Collectors.toList());
                        final List<String> lines2 = gitUtils.getLines(bytes);

                        if (!compareFile(lines1, lines2, lineNodeList, assignment, pathString)) {
                            fileCount++;
                            Assert.fail();
                        }

                        //						if (!comparePCs(assignment, pathString, textFileNode, lineNodeList, lines2)) {
                        //							fileCount++;
                        //							Assert.fail();
                        //						}
                    }
                }
            }
            if (fileCount > 0) {
                Logger.logInfo("");
                Logger.logInfo("Number of differing files: " + fileCount);
            }
        }

        for (final Entry<String, BinaryFileNode> entry :
                fileMap.getBinaryFileMap().entrySet()) {
            if (entry.getValue().isActive(assignment, nodeDictionary)) {
                Logger.logInfo("");
                Logger.logInfo("Should not be active: ");
                Logger.logInfo("\t" + assignment);
                Logger.logInfo("\t" + entry.getKey());
                Logger.logInfo("\t" + entry.getValue().getCondition());
                Assert.fail();
                break;
            }
        }

        for (final Entry<String, TextFileNode> entry : fileMap.getTextFileMap().entrySet()) {
            if (entry.getValue().isActive(assignment, nodeDictionary)) {
                Logger.logInfo("");
                Logger.logInfo("Should not be active: ");
                Logger.logInfo("\t" + assignment);
                Logger.logInfo("\t" + entry.getKey());
                Logger.logInfo("\t" + entry.getValue().getCondition());
                Assert.fail();
                break;
            }
        }

        return result;
    }

    @SuppressWarnings("unused")
    private boolean comparePCs(
            Map<Object, Boolean> assignment,
            final String pathString,
            final TextFileNode textFileNode,
            List<LineNode> lineNodeList,
            final List<String> lines2) {
        if (pathString.matches(AnnotationExtractor.CFileRegex)) {
            lineNodeList =
                    textFileNode.getActiveData(assignment, nodeDictionary).collect(Collectors.toList());
            final List<Node> pcList2 = AnnotationExtractor.extractPresenceConditions(lines2);
            if (pcList2 != null) {
                int lineNumber = 0;
                final Iterator<Node> pcIt2 = pcList2.iterator();
                final Iterator<LineNode> it3 = lineNodeList.iterator();
                while (pcIt2.hasNext()) {
                    lineNumber++;
                    final Node pc2 = pcIt2.next();
                    final int pc3 = it3.next().getPresenceCondition();
                    if ((pc2 != null) && (pc3 == -1)) {
                        Logger.logInfo(pathString);
                        Logger.logInfo("Line number:" + lineNumber);
                        Logger.logInfo("PC:" + pc2);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean compareFile(
            List<String> lines1,
            List<String> lines2,
            List<LineNode> lineNodeList,
            Map<Object, Boolean> assignment,
            String pathString) {
        final Iterator<String> it1 = lines1.iterator();
        final Iterator<String> it2 = lines2.iterator();
        final Iterator<LineNode> it3 = lineNodeList.iterator();

        int lineNumber = 1;
        while (true) {
            final String line1 = it1.hasNext() ? it1.next() : null;
            final String line2 = it2.hasNext() ? it2.next() : null;
            DataNode<String> lineNode;
            if (line1 != null) {
                do {
                    lineNode = it3.next();
                } while (!lineNode.isActive(assignment, nodeDictionary));
            } else {
                lineNode = it3.hasNext() ? it3.next() : null;
            }
            if ((line1 == null) && (line2 == null)) {
                break;
            }
            if (!Objects.equals(line1, line2)) {
                Logger.logInfo(pathString);
                Logger.logInfo("\t" + lineNumber);
                Logger.logInfo("\t\t<" + line1);
                Logger.logInfo("\t\t>" + line2);
                if (lineNode != null) {
                    Logger.logInfo("\t\t\t: " + lineNode.getCondition());
                } else {
                    Logger.logInfo("\t\t\t: null");
                }
                return false;
            }
            lineNumber++;
        }
        return true;
    }
}
