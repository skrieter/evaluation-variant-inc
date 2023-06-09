package org.varcs;

import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.Test;
import org.spldev.varcs.Extractor;
import org.spldev.varcs.Main;
import org.spldev.varcs.structure.CommitNode;
import org.varcs.visitors.CommitTester;

import de.featjar.util.logging.Logger;
import de.featjar.util.tree.Trees;

public class TestExtraction {

	@Test
	public void test() {
		try {
			Main.installLogger();
			Main.createDirectories();
		} catch (final Exception e) {
			e.printStackTrace();
			fail();
		}
		final Main main = new Main();
		main.readSystemNames(getResource("systems_test.list"));
		main.parseRepositorySources();
		main.initializeRepositories();
		for (final String system : main.getSystemNames()) {
			try {
				final Optional<Extractor> extractor = main.analyzeRepository(system);
				if (extractor.isPresent()) {
					testLines(extractor.get());
				}
			} catch (final Exception e) {
				e.printStackTrace();
				fail();
			}
		}
	}

	private Path getResource(String resourceName) {
		try {
			return Paths.get(ClassLoader.getSystemClassLoader().getResource("systems_test.list").toURI());
		} catch (final URISyntaxException e) {
			e.printStackTrace();
			fail();
			return null;
		}
	}

	private void testLines(final Extractor extractor) {
		Logger.logInfo("Testing lines");
		Main.tabFormatter.incTabLevel();
		CommitNode commitTree = extractor.getCommitTree();
		CommitTester visitor = new CommitTester(extractor.getGitUtils(), extractor.getFileMap(),
			extractor.getFormula());
		Trees.traverse(commitTree, visitor);
		Main.tabFormatter.decTabLevel();
	}

}
