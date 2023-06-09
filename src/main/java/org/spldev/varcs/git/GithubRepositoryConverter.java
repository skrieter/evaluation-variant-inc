package org.spldev.varcs.git;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

import org.eclipse.egit.github.core.*;
import org.eclipse.egit.github.core.client.*;
import org.eclipse.egit.github.core.service.*;
import org.spldev.varcs.*;

import de.featjar.util.logging.Logger;

public class GithubRepositoryConverter implements RepositoryConverter {

	private static final int SEARCH_PAGE_LIMIT = Integer.MAX_VALUE;

	private static final RepositoryService repositoryService;

	static {
		final GitHubClient gitHubClient = new GitHubClient();
		final Console console = System.console();
		if (console != null) {
			console.printf("Username: ");
			console.flush();
			final String name = console.readLine();
			final String password = new String(console.readPassword("Password: "));
			gitHubClient.setCredentials(name, password);
		} else {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
				System.out.print("Username: ");
				final String name = reader.readLine();
				System.out.print("Password (Warning: Password will be visible!): ");
				final String password = reader.readLine();
				gitHubClient.setCredentials(name, password);
			} catch (final IOException e) {
				Logger.logError(e);
			}
		}
		RepositoryService tempService;
		try {
			final User user = new UserService(gitHubClient).getUser();
			if (user != null) {
				Logger.logInfo("GitHub authentication successful! Logged in as: " + user.getLogin());
				tempService = new RepositoryService(gitHubClient);
			} else {
				Logger.logInfo("GitHub authentication failed! Using unauthenticated requests.");
				tempService = new RepositoryService();
			}
		} catch (final Exception e) {
			Logger.logInfo("GitHub authentication failed! Using unauthenticated requests.");
			tempService = new RepositoryService();
		}
		repositoryService = tempService;
	}

	public static void searchRepository(String searchQuery) {
		final Date date = new Date();
		final String fullDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(date);
		final String fileDate = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(date);
		final Path outDir = Paths.get("sources/systems_searched_" + fileDate);
		final List<String> systemList = new ArrayList<>();
		try {
			Files.createDirectories(outDir);
			for (int i = 1; i < SEARCH_PAGE_LIMIT; i++) {
				final List<SearchRepository> repos = repositoryService.searchRepositories(searchQuery, i);
				if (repos.isEmpty()) {
					break;
				}
				System.out.println("Page " + i + ": " + repos.size() + " elements");
				for (final SearchRepository repo : repos) {
					final Properties properties = new Properties();
					properties.setProperty("location", repo.getUrl());
					properties.setProperty("search_date", fullDate);
					properties.setProperty("search_query", searchQuery);
					final String name = repo.getName();
					final String string = name + ".github";
					try (BufferedWriter out = Files.newBufferedWriter(outDir.resolve(string), StandardCharsets.UTF_8,
						StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
						properties.store(out, null);
						systemList.add(name);
					} catch (final Exception e) {
					}
				}
			}
			Files.write(outDir.resolve("systems.list"), systemList, StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE);
		} catch (final IOException e1) {
			e1.printStackTrace();
		}
	}

	private final Path propertiesFile;

	public GithubRepositoryConverter(Path file) {
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
		Logger.logInfo("Querying GitHub...");
		final RepositoryProperties repo = new RepositoryProperties();
		repo.setLocation(url);
		final RepositoryId repositoryId = RepositoryId.createFromUrl(repo.getLocation());

		final Repository gitHubRepo = repositoryService.getRepository(repositoryId);
		convert(gitHubRepo, repo);

		Logger.logInfo("Fetching forklist...");
		Main.tabFormatter.incTabLevel();
		final ArrayList<RepositoryProperties> forkList = new ArrayList<>();
		int pageCount = 0;
		for (final Collection<Repository> gitHubForkPage : repositoryService.pageForks(repositoryId, 1000)) {
			Logger.logProgress("Page " + ++pageCount);
			for (final Repository gitHubFork : gitHubForkPage) {
				final RepositoryProperties fork = new RepositoryProperties();
				convert(gitHubFork, fork);
				forkList.add(fork);
			}
		}
		Main.tabFormatter.decTabLevel();
		repo.setForks(forkList);

		return repo;
	}

	private static void convert(Repository gitHubRepo, RepositoryProperties repo) {
		final String[] urlSegments = gitHubRepo.getUrl().split("/");
		repo.setName(urlSegments[urlSegments.length - 1]);
		repo.setForkName(urlSegments[urlSegments.length - 2]);
		repo.setMasterBranchName(gitHubRepo.getMasterBranch());
		repo.setLocation(gitHubRepo.getCloneUrl());
	}

	private static String readProperty(Properties properties, String key) {
		final String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalArgumentException(key);
		}
		return value;
	}

}
