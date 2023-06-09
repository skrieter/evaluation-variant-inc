package org.spldev.varcs.git;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import org.spldev.varcs.*;

import de.featjar.util.logging.Logger;

public class LocalRepositoryConverter implements RepositoryConverter {

	private final Path propertiesFile;

	public LocalRepositoryConverter(Path file) {
		propertiesFile = file;
	}

	@Override
	public RepositoryProperties getRepository() {
		final Properties properties = new Properties();
		try (BufferedReader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
			Logger.logInfo("Reading properties...");
			properties.load(reader);
			final String url = readProperty(properties, "location");
			return getRepository(url);
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public RepositoryProperties getRepository(String url) throws Exception {
		final RepositoryProperties repo = new RepositoryProperties();
		repo.setLocation(url);
		final String[] split = url.split("[/\\\\]");
		repo.setName(split[split.length - 1]);
		repo.setForkName("origin");
		repo.setMasterBranchName("master");
		repo.setForks(Collections.emptyList());
		return repo;
	}

	private static String readProperty(Properties properties, String key) {
		final String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalArgumentException(key);
		}
		return value;
	}

}
