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
import de.featjar.util.tree.structure.Tree;
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
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

    private static class PreOrderSpliterator<T extends X, X extends Tree<X>> implements Spliterator<X> {

        final LinkedList<X> stack = new LinkedList<>();
        final HashSet<X> visited = new HashSet<>();

        public PreOrderSpliterator(T node) {
            if (node != null) {
                stack.addFirst(node);
            }
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.IMMUTABLE;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean tryAdvance(Consumer<? super X> consumer) {
            if (stack.isEmpty()) {
                return false;
            } else {
                final X node = stack.removeFirst();
                if (visited.add(node)) {
                    stack.addAll(0, node.getChildren());
                    consumer.accept(node);
                }
                return true;
            }
        }

        @Override
        public Spliterator<X> trySplit() {
            return null;
        }
    }

    private static class StackEntry<T> {
        private T node;
        private List<T> remainingChildren;

        public StackEntry(T node) {
            this.node = node;
        }
    }

    private static class PostOrderSpliterator<T extends Tree<T>> implements Spliterator<T> {

        final LinkedList<StackEntry<T>> stack = new LinkedList<>();
        final HashSet<T> visited = new HashSet<>();

        public PostOrderSpliterator(T node) {
            if (node != null) {
                stack.push(new StackEntry<>(node));
                visited.add(node);
            }
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.IMMUTABLE;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            if (stack.isEmpty()) {
                return false;
            }
            while (!stack.isEmpty()) {
                final StackEntry<T> entry = stack.peek();
                if (entry.remainingChildren == null) {
                    entry.remainingChildren = new LinkedList<>(entry.node.getChildren());
                }
                if (!entry.remainingChildren.isEmpty()) {
                    boolean added = false;
                    while (!entry.remainingChildren.isEmpty()) {
                        T child = entry.remainingChildren.remove(0);
                        if (visited.add(child)) {
                            stack.push(new StackEntry<>(child));
                            added = true;
                            break;
                        }
                    }
                    if (!added) {
                        consumer.accept(entry.node);
                        stack.pop();
                    }
                } else {
                    consumer.accept(entry.node);
                    stack.pop();
                }
            }
            return true;
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }
    }

    private static class LevelOrderSpliterator<T extends X, X extends Tree<X>> implements Spliterator<X> {

        final LinkedList<X> queue = new LinkedList<>();
        final HashSet<X> visited = new HashSet<>();

        public LevelOrderSpliterator(T node) {
            if (node != null) {
                queue.addFirst(node);
            }
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.IMMUTABLE;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean tryAdvance(Consumer<? super X> consumer) {
            if (queue.isEmpty()) {
                return false;
            } else {
                final X node = queue.removeFirst();
                if (visited.add(node)) {
                    consumer.accept(node);
                    queue.addAll(node.getChildren());
                }
                return true;
            }
        }

        @Override
        public Spliterator<X> trySplit() {
            return null;
        }
    }

    public static <T extends X, X extends Tree<X>> Stream<X> preOrderStream(T node) {
        return StreamSupport.stream(new PreOrderSpliterator<>(node), false);
    }

    public static <T extends Tree<T>> Stream<T> postOrderStream(T node) {
        return StreamSupport.stream(new PostOrderSpliterator<>(node), false);
    }

    public static <T extends Tree<T>> Stream<T> levelOrderStream(T node) {
        return StreamSupport.stream(new LevelOrderSpliterator<>(node), false);
    }

    private final GitUtils gitUtils;

    private LinkedHashMap<ObjectId, List<Ref>> variantRefMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Ref> refMap = new LinkedHashMap<>();

    private ObjectId head;
    private CommitNode commitTree;
    private LinkedHashSet<CommitNode> commits;

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
        LinkedHashMap<ObjectId, CommitContainer> commitMap = new LinkedHashMap<>();
        commits = new LinkedHashSet<>();

        Logger.logInfo("Getting commits...");
        Main.tabFormatter.incTabLevel();

        final Iterable<RevCommit> allCommits = gitUtils.getGit().log().all().call();
        for (final RevCommit revCommit : allCommits) {
            if (!commitMap.containsKey(revCommit.toObjectId())) {
                CommitContainer container =
                        new CommitContainer(gitUtils.getRepository().parseCommit(revCommit));
                commitMap.put(revCommit.toObjectId(), container);
                commits.add(container.commitNode);
            }
        }
        Main.tabFormatter.decTabLevel();

        Logger.logInfo("Finding merge base...");
        Main.tabFormatter.incTabLevel();

        final List<ObjectId> variantRefList = new ArrayList<>(variantRefMap.keySet());
        Collections.sort(variantRefList, this::compareRefTime);
        Collections.reverse(variantRefList);

        commitTree = commitMap.get(getHeadMergeBase(variantRefList)).commitNode;

        startPoints = new LinkedHashSet<>();

        for (final CommitContainer commitContainer : commitMap.values()) {
            final CommitNode commitNode = commitContainer.commitNode;
            for (final RevCommit parentCommit : commitContainer.revCommit.getParents()) {
                final CommitContainer parent = commitMap.get(parentCommit.getId());
                if (parent != null) {
                    parent.commitNode.addChild(commitNode);
                    commitNode.addParent(parent.commitNode);
                }
            }
        }
        for (final CommitContainer commitContainer : commitMap.values()) {
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
        commits.stream().filter(node -> node.getChildNodes().isEmpty()).forEach(node -> endPoints.add(node));
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
                    for (final CommitNode child : commitNode.getChildNodes()) {
                        child.getParents().remove(commitNode);
                    }
                    newPotentialOrphans.addAll(commitNode.getChildNodes());
                    commitNode.getChildNodes().clear();
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

    private static class Pruner {
        private boolean changed;

        public void prune(CommitNode commitTree) {
            do {
                changed = false;
                postOrderStream(commitTree).forEach(commitNode -> {
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
                            parent.getChildNodes().remove(commitNode);
                            commitNode.getParents().remove(parent);
                            changed = true;
                        }
                    }
                });

                postOrderStream(commitTree).forEach(currentCommit -> {
                    if (currentCommit.getChildNodes().size() == 1) {
                        final CommitNode child =
                                currentCommit.getChildNodes().iterator().next();
                        child.getParents().remove(currentCommit);
                        for (final CommitNode parent : currentCommit.getParents()) {
                            parent.getChildNodes().remove(currentCommit);
                            parent.getChildNodes().add(child);
                            child.getParents().add(parent);
                        }
                        changed = true;
                    }
                });
            } while (changed);
        }
    }

    public void pruneCommitTree() throws Exception {
        Logger.logInfo("Prune commit tree...");
        new Pruner().prune(commitTree);
    }

    public void sortCommitTree() throws Exception {
        Logger.logInfo("Sort commit tree...");
        Main.tabFormatter.incTabLevel();

        postOrderStream(commitTree).forEach(commitNode -> {
            final ArrayList<CommitNode> sortedChildren = new ArrayList<>(commitNode.getChildNodes());
            Collections.sort(
                    sortedChildren,
                    (c1, c2) -> c1.getChildNodes().size() - c2.getChildNodes().size());
            commitNode.getChildNodes().clear();
            commitNode.getChildNodes().addAll(sortedChildren);
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

    public LinkedHashSet<CommitNode> getCommits() {
        return commits;
    }
}
