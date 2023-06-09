package org.spldev.varcs.visitors;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;
import org.spldev.varcs.FileMap;
import org.spldev.varcs.NodeDictionary;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.BinaryFileNode;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.DataNode;
import org.spldev.varcs.structure.LineNode;
import org.spldev.varcs.structure.TextFileNode;

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.visitor.TreeVisitor;

public class LineExtractor implements TreeVisitor<Void, CommitNode> {

	private static final DiffAlgorithm algorithm = DiffAlgorithm.getAlgorithm(SupportedAlgorithm.HISTOGRAM);

	private final GitUtils gitUtils;

	private final FileMap fileMap;
	private final NodeDictionary conditionDictionary;

	private final Set<Literal> literals;

	private final HashMap<CommitNode, Map<Object, Boolean>> visitedNodes = new HashMap<>();

	public LineExtractor(GitUtils gitUtils, FileMap fileMap, Node formula) {
		this.gitUtils = gitUtils;
		this.fileMap = fileMap;
		literals = formula.getUniqueLiterals();
		conditionDictionary = fileMap.getConditionDictionary();
	}

	@Override
	public VisitorResult firstVisit(List<CommitNode> path) {
		final CommitNode currentCommit = TreeVisitor.getCurrentNode(path);
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
			visitedNodes.put(currentCommit, assignment);
			processMerge(currentCommit, assignment);
			return VisitorResult.Continue;
		} catch (final Exception e) {
			Logger.logError(e);
			return VisitorResult.SkipAll;
		}
	}

	private void processMerge(CommitNode curCommit, Map<Object, Boolean> assignment) throws Exception {
		try (TreeWalk treeWalk = new TreeWalk(gitUtils.getRepository())) {
			treeWalk.addTree(gitUtils.getRepository().parseCommit(curCommit.getObjectId()).getTree());
			treeWalk.setRecursive(true);
			final String curCommitId = gitUtils.getVariable(curCommit).orElseThrow(NullPointerException::new);

			final HashSet<String> fileNodeSet = new HashSet<>();
			final Set<String> syncedfileNodeSet = Collections.synchronizedSet(fileNodeSet);

			final int threadCount = Runtime.getRuntime().availableProcessors() - 1;
			final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			for (int i = 0; i < threadCount; i++) {
				executor.submit(new CommitAnalyzer(curCommitId, assignment, syncedfileNodeSet, treeWalk));
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

			final Literal negCommitLiteral = new Literal(curCommitId, false);

			for (final TextFileNode fileNode : fileMap.getTextFileMap().values()) {
				if (!fileNodeSet.contains(fileNode.getPath()) && fileNode.isActive(assignment, conditionDictionary)) {
					deleteTextNode(assignment, negCommitLiteral, fileNode);
				}
			}
			for (final BinaryFileNode fileNode : fileMap.getBinaryFileMap().values()) {
				if (!fileNodeSet.contains(fileNode.getPath()) && fileNode.isActive(assignment, conditionDictionary)) {
					deleteBinaryNode(assignment, negCommitLiteral, fileNode);
				}
			}
		}
	}

	private void deleteBinaryNode(Map<Object, Boolean> assignment, final Node negCommitLiteral,
		BinaryFileNode binaryNode) {
		final Node condition = new And(conditionDictionary.getCondition(binaryNode.getCondition()), negCommitLiteral);
		final int conditionIndex = conditionDictionary.getIndexSynced(condition);
		binaryNode.setCondition(conditionIndex);
	}

	private void deleteTextNode(Map<Object, Boolean> assignment, final Node negCommitLiteral, TextFileNode textNode) {
		final Node condition = new And(conditionDictionary.getCondition(textNode.getCondition()), negCommitLiteral);
		final int conditionIndex = conditionDictionary.getIndexSynced(condition);
		textNode.setCondition(conditionIndex);
	}

	private class CommitAnalyzer implements Callable<Void> {

		private final Map<Object, Boolean> assignment;
		private final Node commitLiteral;
		private final Node negCommitLiteral;
		private final int commitLiteralIndex;
		private final TreeWalk treeWalk;
		private final Set<String> fileNodeSet;

		public CommitAnalyzer(String curCommitId, Map<Object, Boolean> assignment, Set<String> fileNodeSet,
			TreeWalk treeWalk) {
			this.assignment = assignment;
			this.treeWalk = treeWalk;
			this.fileNodeSet = fileNodeSet;

			final Literal tempCommitLiteral = new Literal(curCommitId, true);
			final Literal tempNegCommitLiteral = new Literal(curCommitId, false);
			conditionDictionary.putConditionSynced(tempCommitLiteral);
			conditionDictionary.putConditionSynced(tempNegCommitLiteral);

			commitLiteralIndex = conditionDictionary.getIndex(tempCommitLiteral);
			commitLiteral = conditionDictionary.getCondition(commitLiteralIndex);
			negCommitLiteral = conditionDictionary.getCondition(tempNegCommitLiteral);
		}

		@Override
		public Void call() throws Exception {
			while (true) {
				byte[] newBytes;
				final String pathString;
				synchronized (treeWalk) {
					if (treeWalk.next()) {
						newBytes = gitUtils.getBytes(treeWalk.getObjectId(0));
						pathString = treeWalk.getPathString();
					} else {
						break;
					}
				}

				analyze(newBytes, pathString);
			}
			return null;
		}

		private void analyze(byte[] newBytes, final String pathString) throws IOException {
			TextFileNode textNode = fileMap.getTextFileNode(pathString);
			BinaryFileNode binaryNode = fileMap.getBinaryFileNode(pathString);

			assert (((textNode == null) || !textNode.isActive(assignment, conditionDictionary))
				|| ((binaryNode == null) || !binaryNode.isActive(assignment, conditionDictionary)));

			fileNodeSet.add(pathString);
			if (newBytes == null) {
				if ((textNode != null) && textNode.isActive(assignment, conditionDictionary)) {
					deleteTextNode(assignment, negCommitLiteral, textNode);
				} else if ((binaryNode != null) && binaryNode.isActive(assignment, conditionDictionary)) {
					deleteBinaryNode(assignment, negCommitLiteral, binaryNode);
				}
			} else {
				if (gitUtils.isBinary(newBytes)) {
					if ((textNode != null) && textNode.isActive(assignment, conditionDictionary)) {
						deleteTextNode(assignment, negCommitLiteral, textNode);
					}
					if (binaryNode == null) {
						binaryNode = new BinaryFileNode(pathString);
						binaryNode.setCondition(commitLiteralIndex);
						fileMap.addBinaryNode(binaryNode);
						binaryNode.getDataNodes().add(new DataNode<>(newBytes, commitLiteralIndex));
					} else if (!binaryNode.isActive(assignment, conditionDictionary)) {
						final Or condition = new Or(conditionDictionary.getCondition(binaryNode.getCondition()),
							commitLiteral);
						final int conditionIndex = conditionDictionary.getIndexSynced(condition);
						binaryNode.setCondition(conditionIndex);
						replaceBinaryData(newBytes, binaryNode);
					} else {
						replaceBinaryData(newBytes, binaryNode);
					}
				} else {
					if ((binaryNode != null) && binaryNode.isActive(assignment, conditionDictionary)) {
						deleteBinaryNode(assignment, negCommitLiteral, binaryNode);
					}
					if (textNode == null) {
						textNode = new TextFileNode(pathString);
						textNode.setCondition(commitLiteralIndex);
						fileMap.addTextNode(textNode);
						for (final String line : gitUtils.getLines(newBytes)) {
							textNode.getDataNodes().add(new LineNode(line, commitLiteralIndex));
						}
					} else if (!textNode.isActive(assignment, conditionDictionary)) {
						final Or condition = new Or(conditionDictionary.getCondition(textNode.getCondition()),
							commitLiteral);
						final int conditionIndex = conditionDictionary.getIndexSynced(condition);
						textNode.setCondition(conditionIndex);
						replaceTextData(newBytes, textNode);
					} else {
						replaceTextData(newBytes, textNode);
					}
				}
			}
		}

		private void replaceTextData(final byte[] newBytes, TextFileNode textNode) throws IOException {
			final List<String> newLines = gitUtils.getLines(newBytes);
			final List<String> oldLines = textNode.getActiveData(assignment, conditionDictionary).map(DataNode::getData)
				.collect(Collectors.toList());

			final RawText newText = new RawText(getBytesFromLines(newLines));
			final RawText oldText = new RawText(getBytesFromLines(oldLines));

			final EditList editList = algorithm.diff(RawTextComparator.DEFAULT, oldText, newText);
			if (!editList.isEmpty()) {
				Collections.sort(editList, this::compareEditsA);

				final TreeSet<Integer> linesToDelete = new TreeSet<>();
				for (final Edit edit : editList) {
					final Type type = edit.getType();
					switch (type) {
					case REPLACE:
					case DELETE:
						final int beginA = edit.getBeginA();
						final int endA = edit.getEndA();
						for (int i = beginA; i < endA; i++) {
							linesToDelete.add(i);
						}
						break;
					case EMPTY:
					case INSERT:
						break;
					default:
						throw new IllegalStateException(String.valueOf(type));
					}
				}
				ListIterator<LineNode> lineIterator = textNode.getDataNodes().listIterator();
				int lineNumber = 0;
				int cachedOldCondition = -1;
				int cachedNegatedCondition = -1;
				deleteLoop: for (final Integer lineNumberToDelete : linesToDelete) {
					while (lineIterator.hasNext()) {
						final DataNode<String> lineNode = lineIterator.next();
						if (lineNode.isActive(assignment, conditionDictionary)
							&& (lineNumber++ == lineNumberToDelete)) {
							final int oldCondition = lineNode.getCondition();
							if (oldCondition != cachedOldCondition) {
								cachedOldCondition = oldCondition;
								final And condition = new And(conditionDictionary.getCondition(oldCondition),
									negCommitLiteral);
								cachedNegatedCondition = conditionDictionary.getIndexSynced(condition);
							}
							lineNode.setCondition(cachedNegatedCondition);
							continue deleteLoop;
						}
					}
				}

				lineIterator = textNode.getDataNodes().listIterator();
				lineNumber = 1;
				Collections.sort(editList, this::compareEditsB);

				for (final Edit edit : editList) {
					final Type type = edit.getType();
					switch (type) {
					case DELETE:
					case EMPTY:
						break;
					case INSERT:
					case REPLACE:
						final int beginB = edit.getBeginB();
						final int endB = edit.getEndB();
						final List<String> newEditedLines = newLines.subList(beginB, endB);
						if (beginB == 0) {
							lineIterator = textNode.getDataNodes().listIterator();
							addLines(newEditedLines, lineIterator);
						} else {
							lineIterator = textNode.getDataNodes().listIterator();
							lineNumber = 1;
							while (lineIterator.hasNext()) {
								if (lineIterator.next().isActive(assignment, conditionDictionary)
									&& (lineNumber++ == beginB)) {
									addLines(newEditedLines, lineIterator);
									break;
								}
							}
						}
						break;
					default:
						throw new IllegalStateException(String.valueOf(type));
					}
				}
			}
		}

		private void replaceBinaryData(final byte[] newBytes, BinaryFileNode binaryNode) {
			final Optional<DataNode<byte[]>> binaryData = binaryNode.getActiveData(assignment, conditionDictionary)
				.findAny();
			if (binaryData.isPresent() && !Objects.equals(binaryData.get().getData(), newBytes)) {
				binaryNode.getActiveData(assignment, conditionDictionary).forEach(dataNode -> {
					final And condition = new And(conditionDictionary.getCondition(dataNode.getCondition()),
						negCommitLiteral);
					final int conditionIndex = conditionDictionary.getIndexSynced(condition);
					dataNode.setCondition(conditionIndex);
				});
				binaryNode.getDataNodes().add(new DataNode<>(newBytes, commitLiteralIndex));
			}
		}

		private byte[] getBytesFromLines(List<String> newLines) {
			final StringBuilder sb = new StringBuilder();
			for (final String line : newLines) {
				sb.append(line);
				sb.append('\n');
			}
			final byte[] bytes = sb.toString().getBytes();
			return bytes;
		}

		private int compareEditsA(Edit edit1, Edit edit2) {
			return edit2.getBeginA() - edit1.getBeginA();
		}

		private int compareEditsB(Edit edit1, Edit edit2) {
			return edit1.getBeginB() - edit2.getBeginB();
		}

		private void addLines(List<String> newEditedLines, ListIterator<LineNode> iterator) {
			for (final String line : newEditedLines) {
				iterator.add(new LineNode(line, commitLiteralIndex));
			}
		}

	}

}
