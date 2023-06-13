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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.spldev.varcs.Main;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.CommitNode;

public class ChangePrinter implements TreeVisitor<Void, CommitNode> {

    private final GitUtils gitUtils;

    private boolean printChanges = false;
    private boolean printEdits = false;

    private final LinkedHashSet<CommitNode> nodeList = new LinkedHashSet<>();

    public ChangePrinter(GitUtils gitUtils) {
        this.gitUtils = gitUtils;
    }

    public boolean isPrintChanges() {
        return printChanges;
    }

    public void setPrintChanges(boolean printChanges) {
        this.printChanges = printChanges;
    }

    public boolean isPrintEdits() {
        return printEdits;
    }

    public void setPrintEdits(boolean printEdits) {
        this.printEdits = printEdits;
    }

    @Override
    public VisitorResult firstVisit(List<CommitNode> path) {
        try {
            final CommitNode currentCommit = TreeVisitor.getCurrentNode(path);
            final CommitNode parentCommit = TreeVisitor.getParentNode(path);

            if ((parentCommit != null)) {
                Main.tabFormatter.incTabLevel();
            }
            if (!nodeList.add(currentCommit)) {
                return VisitorResult.SkipChildren;
            }

            printChanges(parentCommit, currentCommit);
        } catch (final Exception e) {
            Logger.logError(e);
            return VisitorResult.SkipAll;
        }
        return VisitorResult.Continue;
    }

    @Override
    public VisitorResult lastVisit(List<CommitNode> path) {
        final CommitNode parentCommit = TreeVisitor.getParentNode(path);
        if ((parentCommit != null)) {
            Main.tabFormatter.decTabLevel();
        }
        return VisitorResult.Continue;
    }

    public void printChanges(CommitNode parentCommit, CommitNode curCommit) throws Exception {
        final String terminalMarker = curCommit.getChildren().isEmpty() ? "#" : "+";
        final RevCommit commit = gitUtils.getRepository().parseCommit(curCommit.getObjectId());
        Logger.logInfo(terminalMarker + " " + gitUtils.getCommitString(commit));

        if (printChanges) {
            Main.tabFormatter.incTabLevel();
            if (parentCommit == null) {
                // root commit
            } else {
                final ObjectReader reader = gitUtils.getRepository().newObjectReader();
                final ObjectId oldTree = gitUtils.getRepository()
                        .parseCommit(parentCommit.getObjectId())
                        .getTree()
                        .toObjectId();
                final ObjectId newTree = commit.getTree().toObjectId();

                final CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, oldTree);

                final CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, newTree);

                try (DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE)) {
                    df.setRepository(gitUtils.getRepository());
                    df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                    final List<DiffEntry> diffs = df.scan(oldTreeIter, newTreeIter);
                    Collections.sort(diffs, this::compareDiffs);
                    for (final DiffEntry diff : diffs) {
                        final ChangeType changeType = diff.getChangeType();
                        Logger.logInfo(changeType.toString());
                        switch (changeType) {
                            case ADD: {
                                final FileHeader fileHeader = df.toFileHeader(diff);
                                final String newPath = diff.getNewPath();
                                final List<String> newLines = gitUtils.getLines(diff.getNewId());
                                Logger.logInfo(newPath);

                                if (printEdits) {
                                    Main.tabFormatter.incTabLevel();
                                    for (final Edit edit : fileHeader.toEditList()) {
                                        Logger.logInfo(edit.toString());
                                        Main.tabFormatter.incTabLevel();
                                        final int beginB = edit.getBeginB();
                                        final int endB = edit.getEndB();
                                        final List<String> newEditedLines = newLines.subList(beginB, endB);
                                        for (final String line : newEditedLines) {
                                            Logger.logInfo("+ " + line.toString());
                                        }
                                        Main.tabFormatter.decTabLevel();
                                    }
                                    Main.tabFormatter.decTabLevel();
                                }
                                break;
                            }
                            case COPY: {
                                final FileHeader fileHeader = df.toFileHeader(diff);
                                final String newPath = diff.getNewPath();
                                final List<String> newLines = gitUtils.getLines(diff.getNewId());
                                Logger.logInfo(newPath);

                                if (printEdits) {
                                    Main.tabFormatter.incTabLevel();
                                    for (final Edit edit : fileHeader.toEditList()) {
                                        Logger.logInfo(edit.toString());
                                        Main.tabFormatter.incTabLevel();
                                        final int beginB = edit.getBeginB();
                                        final int endB = edit.getEndB();
                                        final List<String> newEditedLines = newLines.subList(beginB, endB);
                                        for (final String line : newEditedLines) {
                                            Logger.logInfo("+ " + line.toString());
                                        }
                                        Main.tabFormatter.decTabLevel();
                                    }
                                    Main.tabFormatter.decTabLevel();
                                }
                                break;
                            }
                            case DELETE: {
                                final FileHeader fileHeader = df.toFileHeader(diff);
                                final String oldPath = diff.getOldPath();
                                final List<String> oldLines = gitUtils.getLines(diff.getOldId());
                                Logger.logInfo(oldPath);

                                if (printEdits) {
                                    Main.tabFormatter.incTabLevel();
                                    for (final Edit edit : fileHeader.toEditList()) {
                                        Logger.logInfo(edit.toString());
                                        Main.tabFormatter.incTabLevel();
                                        final int beginA = edit.getBeginA();
                                        final int endA = edit.getEndA();
                                        final List<String> oldEditedLines = oldLines.subList(beginA, endA);
                                        for (final String line : oldEditedLines) {
                                            Logger.logInfo("- " + line.toString());
                                        }
                                        Main.tabFormatter.decTabLevel();
                                    }
                                    Main.tabFormatter.decTabLevel();
                                }
                                break;
                            }
                            case RENAME: {
                                final String oldPath = diff.getOldPath();
                                final String newPath = diff.getNewPath();
                                Logger.logInfo(oldPath + " -> " + newPath);
                                break;
                            }
                            case MODIFY: {
                                final FileHeader fileHeader = df.toFileHeader(diff);
                                final String oldPath = fileHeader.getOldPath();
                                Logger.logInfo(oldPath);

                                if (printEdits) {
                                    Main.tabFormatter.incTabLevel();
                                    final List<String> oldLines = gitUtils.getLines(diff.getOldId());
                                    final List<String> newLines = gitUtils.getLines(diff.getNewId());
                                    for (final Edit edit : fileHeader.toEditList()) {
                                        Logger.logInfo(edit.toString());
                                        Main.tabFormatter.incTabLevel();
                                        final int beginA = edit.getBeginA();
                                        final int endA = edit.getEndA();
                                        final int beginB = edit.getBeginB();
                                        final int endB = edit.getEndB();
                                        final List<String> oldEditedLines = oldLines.subList(beginA, endA);
                                        final List<String> newEditedLines = newLines.subList(beginB, endB);
                                        for (final String line : oldEditedLines) {
                                            Logger.logInfo("- " + line.toString());
                                        }
                                        for (final String line : newEditedLines) {
                                            Logger.logInfo("+ " + line.toString());
                                        }
                                        Main.tabFormatter.decTabLevel();
                                    }
                                    Main.tabFormatter.decTabLevel();
                                }
                                break;
                            }
                            default:
                                throw new IllegalAccessException(String.valueOf(changeType));
                        }
                    }
                }
            }
            Main.tabFormatter.decTabLevel();
        }
    }

    private String getRelevantPath(DiffEntry diff) {
        final ChangeType changeType = diff.getChangeType();
        switch (changeType) {
            case ADD:
            case COPY:
            case MODIFY:
                return diff.getNewPath();
            case DELETE:
            case RENAME:
                return diff.getOldPath();
            default:
                return null;
        }
    }

    private int compareDiffs(DiffEntry d1, DiffEntry d2) {
        final int compareResults = getRelevantPath(d1).compareTo(getRelevantPath(d2));
        if (compareResults != 0) {
            return compareResults;
        } else {
            return d2.getChangeType().toString().compareTo(d1.getChangeType().toString());
        }
    }
}
