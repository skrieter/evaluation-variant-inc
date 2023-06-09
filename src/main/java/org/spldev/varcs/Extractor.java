package org.spldev.varcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.prop4j.And;
import org.prop4j.Implies;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;
import org.spldev.varcs.git.GitUtils;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.LineNode;
import org.spldev.varcs.structure.TextFileNode;
import org.spldev.varcs.visitors.AnnotationExtractor;
import org.spldev.varcs.visitors.ChangePrinter;
import org.spldev.varcs.visitors.LineExtractor;

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.Trees;

public class Extractor {

	private final GitUtils gitUtils;

	private CommitNode commitTree;
	private Node formula;

	private FileMap fileMap = new FileMap();

	public Extractor(Git git) throws IOException {
		gitUtils = new GitUtils(git);
	}

	public GitUtils getGitUtils() {
		return gitUtils;
	}

	public CommitNode getCommitTree() {
		return commitTree;
	}

	public FileMap getFileMap() {
		return fileMap;
	}

	public void setCommitTree(CommitNode commitTree) {
		this.commitTree = commitTree;
	}

	public void testTreeConsistency() throws Exception {
		boolean problem = false;
		final List<CommitNode> list = Trees.getPreOrderList(commitTree);
		for (final CommitNode commitNode : list) {
			final List<CommitNode> children = commitNode.getChildren();
			for (final CommitNode child : children) {
				if (!child.getParents().contains(commitNode)) {
					problem = true;
					System.out.println(commitNode.toString());
				}
			}
		}
		if (problem) {
			throw new RuntimeException("inconsistent tree");
		}

		for (final CommitNode commitNode : list) {
			final LinkedHashSet<CommitNode> parents = commitNode.getParents();
			for (final CommitNode parent : parents) {
				if (!parent.getChildren().contains(commitNode)) {
					problem = true;
					System.out.println(commitNode.toString());
				}
			}
		}
		if (problem) {
			throw new RuntimeException("inconsistent tree");
		}

		System.out.println(list.size());
		for (final CommitNode commitNode : list) {
			if (commitNode.getParents().isEmpty() && (commitNode != commitTree)) {
				throw new RuntimeException("No parents: " + commitNode.toString());
			}
		}
		for (final CommitNode commitNode : list) {
			if (commitNode.getChildren().size() == 1) {
				throw new RuntimeException("One child: " + commitNode.toString());
			}
		}
	}

	public Node buildCommitFormula() throws Exception {
		final List<CommitNode> list = Trees.getPreOrderList(commitTree);
		final List<Node> propNodes = new ArrayList<>();
		for (final CommitNode curCommit : list) {
			final Literal curLiteral = new Literal(gitUtils.getVariable(curCommit).orElseThrow(
				NullPointerException::new), true);
			if (curCommit.getParents().isEmpty()) {
				propNodes.add(curLiteral);
			} else {
				final List<Literal> parentLiterals = new ArrayList<>();
				for (final CommitNode parent : curCommit.getParents()) {
					final Literal parentLiteral = new Literal(gitUtils.getVariable(parent).orElseThrow(
						NullPointerException::new), true);
					parentLiterals.add(parentLiteral);
				}
				final Implies implies = new Implies(curLiteral, new And(parentLiterals));
				propNodes.add(implies);
			}
		}
		formula = new And(propNodes);
		return formula;
	}

	public void extractLines() throws Exception {
		Trees.traverse(commitTree, new LineExtractor(gitUtils, fileMap, formula));
	}

	private int count = 0;

	public void extractAnnotations() {
		Main.tabFormatter.incTabLevel();
		Logger.logInfo("Extracting...");
		Trees.traverse(commitTree, new AnnotationExtractor(gitUtils, fileMap, formula));
		Logger.logInfo("Converting...");
		Main.tabFormatter.incTabLevel();
		final Collection<TextFileNode> values = fileMap.getTextFileMap().values();
		count = 0;
		values.forEach(textFile -> {
			Logger.logProgress("File " + ++count + "/" + values.size());
			List<Node> lastPPConditions = Collections.emptyList();
			int pc = -1;
			for (final LineNode l : textFile.getDataNodes()) {
				final List<Node> ppConditions = l.getPPConditions();
				if (ppConditions != null) {
					if (!compare(lastPPConditions, ppConditions)) {
						lastPPConditions = ppConditions;
						pc = fileMap.getConditionDictionary().getIndexSynced(new Or(ppConditions).simplifyTree());
					}
					l.setPresenceCondition(pc);
					l.setPPConditions(null);
				}
			}
		});
		Main.tabFormatter.decTabLevel();
		Main.tabFormatter.decTabLevel();
	}

	private boolean compare(List<Node> lastPPConditions, List<Node> ppConditions) {
		final Iterator<Node> lastIt = lastPPConditions.iterator();
		final Iterator<Node> curIt = ppConditions.iterator();
		while (lastIt.hasNext() && curIt.hasNext()) {
			if (lastIt.next() != curIt.next()) {
				return false;
			}
		}
		return lastIt.hasNext() == curIt.hasNext();
	}

	public void printChanges(boolean printChanges, boolean printEdits) throws Exception {
		final ChangePrinter visitor = new ChangePrinter(gitUtils);
		visitor.setPrintChanges(printChanges);
		visitor.setPrintEdits(printEdits);
		Trees.traverse(commitTree, visitor);
	}

	public void printFormula() {
		Logger.logInfo(formula.toString());
	}

	public Node getFormula() {
		return formula;
	}

	public void setFormula(Node formula) {
		this.formula = formula;
	}

	public void setFileMap(FileMap fileMap) {
		this.fileMap = fileMap;
	}

}
