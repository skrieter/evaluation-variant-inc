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

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.CommitNode;

public class CommitTree {

    private static class CommitContainer {
        private RevCommit revCommit;
        private CommitNode commitNode;

        public CommitContainer(RevCommit revCommit) {
            this.revCommit = revCommit;
            commitNode = new CommitNode(revCommit.toObjectId());
        }
    }

    private final GitUtils gitUtils;

    private LinkedHashMap<ObjectId, List<Ref>> variantRefMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Ref> refMap = new LinkedHashMap<>();

    private ObjectId head;
    private CommitNode commitTree;

    private LinkedHashSet<CommitNode> startPoints;
    private int removedOrphans = 0;

    public CommitTree(Git git) throws IOException {
        gitUtils = new GitUtils(git);
    }

    public void identifyVariants() throws Exception {
        identifyVariants(false, true, false, null, "master");
    }

    public void identifyVariants(
            boolean localBranches, boolean remoteBranches, boolean tags, Collection<String> names, String masterBranch)
            throws Exception {
        final List<Ref> branchRefs;
        if (localBranches) {
            if (remoteBranches) {
                branchRefs =
                        gitUtils.getGit().branchList().setListMode(ListMode.ALL).call();
            } else {
                branchRefs = gitUtils.getGit().branchList().call();
            }
        } else if (remoteBranches) {
            branchRefs =
                    gitUtils.getGit().branchList().setListMode(ListMode.REMOTE).call();
        } else {
            branchRefs = Collections.emptyList();
        }

        final List<Ref> tagRefs;
        if (tags) {
            tagRefs = gitUtils.getGit().tagList().call();
        } else {
            tagRefs = Collections.emptyList();
        }

        final LinkedHashMap<String, Ref> refs = new LinkedHashMap<>();
        branchRefs.forEach(ref -> refs.put(ref.getName(), ref));
        tagRefs.forEach(ref -> refs.put(ref.getName(), ref));

        variantRefMap.clear();

        final HashSet<String> nameSet = names == null ? null : new HashSet<>(names);
        for (final Ref ref : refs.values()) {
            if ((nameSet == null) || nameSet.add(ref.getName())) {
                final ObjectId id = ref.getObjectId();
                List<Ref> refList = variantRefMap.get(id);
                if (refList == null) {
                    refList = new ArrayList<>();
                    variantRefMap.put(id, refList);
                }
                refList.add(ref);
                refMap.put(ref.getName(), ref);
            }
        }

        head = refMap.get("refs/remotes/origin/" + masterBranch).getObjectId();
    }

    private int compareRefTime(ObjectId o1, ObjectId o2) {
        try {
            final Date when1 =
                    gitUtils.getRepository().parseCommit(o1).getAuthorIdent().getWhen();
            final Date when2 =
                    gitUtils.getRepository().parseCommit(o2).getAuthorIdent().getWhen();
            return when1.compareTo(when2);
        } catch (final Exception e) {
            Logger.logError(e);
            return 0;
        }
    }

    public void removeDuplicateVariants(boolean completeRefCompare) throws Exception {
        Logger.logInfo("Identifying subsumed branches...");
        Main.tabFormatter.incTabLevel();

        List<ObjectId> variantRefList = new ArrayList<>(variantRefMap.keySet());
        Collections.sort(variantRefList, this::compareRefTime);
        Collections.reverse(variantRefList);
        int count = 0;
        for (final ObjectId until : variantRefList) {
            Logger.logProgress(
                    "quick test subsume: " + ++count + "/" + variantRefList.size() + ": " + variantRefMap.size());
            if (variantRefMap.keySet().parallelStream()
                    .filter(since -> compareRefTime(since, until) >= 0)
                    .sorted(this::compareRefTime)
                    .limit(20)
                    .anyMatch(since -> {
                        try {
                            return (until != since)
                                    && !gitUtils.getGit()
                                            .log() //
                                            .setMaxCount(1) //
                                            .addRange(since, until) //
                                            .call()
                                            .iterator()
                                            .hasNext();
                        } catch (final Exception e) {
                            return false;
                        }
                    })) {
                variantRefMap.remove(until);
            }
        }

        if (completeRefCompare) {
            variantRefList = new ArrayList<>(variantRefMap.keySet());
            Collections.sort(variantRefList, this::compareRefTime);
            Collections.reverse(variantRefList);
            count = 0;
            for (final ObjectId until : variantRefList) {
                Logger.logProgress("complete test subsume: " + ++count + "/" + variantRefList.size() + ": "
                        + variantRefMap.size());
                if (variantRefMap.keySet().parallelStream()
                        .sorted(this::compareRefTime)
                        .anyMatch(since -> {
                            try {
                                return (until != since)
                                        && !gitUtils.getGit()
                                                .log() //
                                                .setMaxCount(1) //
                                                .addRange(since, until) //
                                                .call()
                                                .iterator()
                                                .hasNext();
                            } catch (final Exception e) {
                                return false;
                            }
                        })) {
                    variantRefMap.remove(until);
                }
            }
        }
        Main.tabFormatter.decTabLevel();
    }

    public void buildCommitTree() throws Exception {
        final LinkedHashMap<ObjectId, CommitContainer> commits = new LinkedHashMap<>();

        Logger.logInfo("Getting commits...");
        Main.tabFormatter.incTabLevel();

        final Iterable<RevCommit> allCommits = gitUtils.getGit().log().all().call();
        for (final RevCommit revCommit : allCommits) {
            commits.putIfAbsent(
                    revCommit.toObjectId(),
                    new CommitContainer(gitUtils.getRepository().parseCommit(revCommit)));
        }
        Main.tabFormatter.decTabLevel();

        Logger.logInfo("Finding merge base...");
        Main.tabFormatter.incTabLevel();

        final List<ObjectId> variantRefList = new ArrayList<>(variantRefMap.keySet());
        Collections.sort(variantRefList, this::compareRefTime);
        Collections.reverse(variantRefList);

        commitTree = commits.get(getHeadMergeBase(variantRefList)).commitNode;

        startPoints = new LinkedHashSet<>();

        for (final CommitContainer commitContainer : commits.values()) {
            final CommitNode commitNode = commitContainer.commitNode;
            for (final RevCommit parentCommit : commitContainer.revCommit.getParents()) {
                final CommitContainer parent = commits.get(parentCommit.getId());
                if (parent != null) {
                    parent.commitNode.addChild(commitNode);
                    commitNode.addParent(parent.commitNode);
                }
            }
        }
        for (final CommitContainer commitContainer : commits.values()) {
            final CommitNode commitNode = commitContainer.commitNode;
            if (commitNode.getParents().isEmpty()) {
                startPoints.add(commitNode);
            }
        }
        Main.tabFormatter.decTabLevel();
    }

    public Map<String, Ref> getRefMap() {
        return Collections.unmodifiableMap(refMap);
    }

    public int getNumberOfVariants() {
        final LinkedHashSet<CommitNode> endPoints = new LinkedHashSet<>();
        Trees.preOrderStream(commitTree)
                .filter(node -> node.getChildren().isEmpty())
                .forEach(node -> endPoints.add(node));
        return endPoints.size();
    }

    public int removeOrphans() throws Exception {
        Logger.logInfo("Remove commit tree orphans...");
        Main.tabFormatter.incTabLevel();

        removedOrphans = 0;
        final LinkedHashSet<CommitNode> newPotentialOrphans = new LinkedHashSet<>();
        while (!startPoints.isEmpty()) {
            for (final CommitNode commitNode : startPoints) {
                if ((commitNode != commitTree) && commitNode.getParents().isEmpty()) {
                    for (final CommitNode child : commitNode.getChildren()) {
                        child.getParents().remove(commitNode);
                    }
                    newPotentialOrphans.addAll(commitNode.getChildren());
                    commitNode.getChildren().clear();
                    removedOrphans++;
                }
            }
            startPoints.clear();
            startPoints.addAll(newPotentialOrphans);
            newPotentialOrphans.clear();
        }
        startPoints.add(commitTree);

        Main.tabFormatter.decTabLevel();
        return removedOrphans;
    }

    public void pruneCommitTree() throws Exception {
        Logger.logInfo("Prune commit tree...");
        Main.tabFormatter.incTabLevel();

        final List<CommitNode> list =
                Trees.preOrderStream(commitTree).distinct().collect(Collectors.toList());
        boolean changed;
        do {
            changed = false;
            for (final CommitNode commitNode : list) {
                final ArrayList<CommitNode> parents = new ArrayList<>(commitNode.getParents());
                final HashSet<CommitNode> transitiveParents = new HashSet<>();
                final LinkedList<CommitNode> newTransitiveParents = new LinkedList<>();
                for (final CommitNode parent : parents) {
                    newTransitiveParents.add(parent);
                    while (!newTransitiveParents.isEmpty()) {
                        final CommitNode parent2 = newTransitiveParents.poll();
                        for (final CommitNode transitiveParent : parent2.getParents()) {
                            if (transitiveParents.add(transitiveParent)) {
                                newTransitiveParents.offer(transitiveParent);
                            }
                        }
                    }
                }
                for (final CommitNode parent : parents) {
                    if (transitiveParents.contains(parent)) {
                        parent.getChildren().remove(commitNode);
                        commitNode.getParents().remove(parent);
                        changed = true;
                    }
                }
            }

            for (final Iterator<CommitNode> iterator = list.iterator(); iterator.hasNext(); ) {
                final CommitNode currentCommit = iterator.next();
                if (currentCommit.getChildren().size() == 1) {
                    final CommitNode child =
                            currentCommit.getChildren().iterator().next();
                    child.getParents().remove(currentCommit);
                    for (final CommitNode parent : currentCommit.getParents()) {
                        parent.getChildren().remove(currentCommit);
                        parent.getChildren().add(child);
                        child.getParents().add(parent);
                    }
                    changed = true;
                    iterator.remove();
                }
            }
        } while (changed);

        Main.tabFormatter.decTabLevel();
    }

    public void sortCommitTree() throws Exception {
        Logger.logInfo("Sort commit tree...");
        Main.tabFormatter.incTabLevel();

        Trees.postOrderStream(commitTree).forEach(commitNode -> {
            final ArrayList<CommitNode> sortedChildren = new ArrayList<>(commitNode.getChildren());
            Collections.sort(
                    sortedChildren,
                    (c1, c2) -> c1.getChildren().size() - c2.getChildren().size());
            commitNode.getChildren().clear();
            commitNode.getChildren().addAll(sortedChildren);
        });

        Main.tabFormatter.decTabLevel();
    }

    public void collectParents(CommitNode commitNode, HashSet<CommitNode> parents) throws Exception {
        for (final CommitNode parent : commitNode.getParents()) {
            if (parents.add(parent)) {
                collectParents(parent, parents);
            }
        }
    }

    private ObjectId getHeadMergeBase(Collection<ObjectId> heads) throws Exception {
        ObjectId mergeBase = head;

        int count = 0;
        final int size = heads.size();
        for (final Iterator<ObjectId> iterator = heads.iterator(); iterator.hasNext(); ) {
            Logger.logProgress(++count + "/" + size);
            final RevCommit next = getMergeBase(mergeBase, iterator.next());
            if (next != null) {
                mergeBase = next.toObjectId();
            } else {
                iterator.remove();
            }
        }
        return mergeBase;
    }

    private RevCommit getMergeBase(ObjectId c1, ObjectId c2) throws Exception {
        try (RevWalk revWalk = new RevWalk(gitUtils.getRepository())) {
            try {
                revWalk.setRevFilter(RevFilter.MERGE_BASE);
                revWalk.markStart(revWalk.parseCommit(c1));
                revWalk.markStart(revWalk.parseCommit(c2));
                return revWalk.next();
            } catch (final Exception e) {
                throw e;
            } finally {
                revWalk.dispose();
            }
        }
    }

    public void printVariants() throws Exception {
        for (final Entry<ObjectId, List<Ref>> entry : variantRefMap.entrySet()) {
            final StringBuilder sb = new StringBuilder();
            for (final Ref ref : entry.getValue()) {
                sb.append(ref.getName());
                sb.append(", ");
            }
            sb.replace(sb.length() - 2, sb.length() - 1, ":");

            sb.append(gitUtils.getObjectReader().abbreviate(entry.getKey()).name());
            Logger.logInfo(sb.toString());
        }
    }

    public CommitNode getRoot() {
        return commitTree;
    }
}
