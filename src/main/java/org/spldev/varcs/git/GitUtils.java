package org.spldev.varcs.git;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.stream.*;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.spldev.varcs.structure.*;

import de.featjar.util.logging.Logger;

public class GitUtils {

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private final Git git;
	private final Repository repository;
	private final ObjectReader objectReader;

	public GitUtils(Git git) throws IOException {
		this.git = git;
		repository = git.getRepository();
		objectReader = repository.newObjectReader();
	}

	public Git getGit() {
		return git;
	}

	public Repository getRepository() {
		return repository;
	}

	public ObjectReader getObjectReader() {
		return objectReader;
	}

	public String getCommitString(CommitNode commitNode) {
		try {
			return getCommitString(getRepository().parseCommit(commitNode.getObjectId()));
		} catch (final IOException e) {
			Logger.logError(e);
			return "";
		}
	}

	public String getCommitString(RevCommit commit) {
		try {
			return objectReader.abbreviate(commit.toObjectId()).name() + ": "
				+ dateFormat.format(commit.getCommitterIdent().getWhen()) + " - " + commit.getShortMessage();
		} catch (final IOException e) {
			Logger.logError(e);
			return "";
		}
	}

	public Optional<String> getVariable(CommitNode curCommit) {
		try {
			return Optional.of(objectReader.abbreviate(curCommit.getObjectId()).name());
		} catch (final IOException e) {
			Logger.logError(e);
			return Optional.empty();
		}
	}

	public List<String> getLines(AbbreviatedObjectId id) throws MissingObjectException, IOException {
		if ((id != null) && id.isComplete()) {
			return getLines(id.toObjectId());
		} else {
			throw new IllegalStateException();
		}
	}

	public List<String> getLines(ObjectId objectId) throws MissingObjectException, IOException {
		final ObjectDatabase objectDatabase = getRepository().getObjectDatabase();
		if (objectDatabase.has(objectId)) {
			return getLines(objectDatabase.open(objectId).getBytes());
		} else {
			return Collections.emptyList();
		}
	}

	public List<String> getLines(byte[] bytes) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)))) {
			return reader.lines().collect(Collectors.toList());
		}
	}

	public byte[] getBytes(AbbreviatedObjectId id) throws MissingObjectException, IOException {
		if ((id != null) && id.isComplete()) {
			return getBytes(id.toObjectId());
		} else {
			throw new IllegalStateException();
		}
	}

	public byte[] getBytes(ObjectId objectId) throws MissingObjectException, IOException {
		final ObjectDatabase objectDatabase = getRepository().getObjectDatabase();
		if (objectDatabase.has(objectId)) {
			final ObjectStream objectStream = objectDatabase.open(objectId).openStream();
			final byte[] bytes = new byte[(int) objectStream.getSize()];
			objectStream.read(bytes);
			return bytes;
		}
		return null;
	}

	public boolean isBinary(byte[] bytes) {
		return RawText.isBinary(bytes);
	}

}
