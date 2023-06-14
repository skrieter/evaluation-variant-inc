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
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class RepositoryProperties implements Serializable {

    private static final int FETCH_TIMEOUT = 300;

    private static final long serialVersionUID = -3214562836646175851L;

    private String forkName;
    private String name;
    private String masterBranchName;
    private String orgLocation;

    private List<RepositoryProperties> forks;

    private transient Git git;

    public List<RepositoryProperties> getForks() {
        return forks;
    }

    public void setForks(List<RepositoryProperties> forks) {
        this.forks = forks;
    }

    public String getForkName() {
        return forkName;
    }

    public void setForkName(String forkName) {
        this.forkName = forkName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMasterBranchName() {
        return masterBranchName;
    }

    public void setMasterBranchName(String masterBranchName) {
        this.masterBranchName = masterBranchName;
    }

    public String getLocation() {
        return orgLocation;
    }

    public void setLocation(String orgLocation) {
        this.orgLocation = orgLocation;
    }

    public Git getGit() {
        return git;
    }

    public boolean initRepo(int forkLimit, boolean clone, boolean forceFetch) {
        try {
            final Path directory = Main.repositoriesDirectory.resolve(getName());
            final List<RepositoryProperties> forksList = getForks();
            if (Files.exists(directory)) {
                Logger.logInfo("Opening git: " + getName());
                git = Git.open(directory.toFile());
            } else if (getLocation() != null) {
                if ((forksList.size() < forkLimit) || clone) {
                    Logger.logInfo("Cloning git: " + getLocation());
                    git = Git.cloneRepository() //
                            .setDirectory(directory.toFile()) //
                            .setURI(getLocation()) //
                            .setNoCheckout(true) //
                            .call();
                } else {
                    Logger.logInfo("Ignoring: " + getName() + " (No repository found)");
                    return false;
                }
            } else {
                throw new IllegalArgumentException();
            }

            Logger.logInfo("Adding forks...");
            Main.tabFormatter.incTabLevel();
            Logger.logDebug("Number of forks: " + forksList.size());
            Logger.logDebug("Force fetch: " + (forceFetch ? "yes" : "no"));
            try {
                if (forksList.size() < forkLimit) {
                    final Set<String> remotes = git.remoteList().call().stream()
                            .map(RemoteConfig::getName)
                            .collect(Collectors.toSet());
                    final Set<String> newRemotes = new HashSet<>();
                    int count = 0;
                    for (final RepositoryProperties repository : forksList) {
                        count++;
                        final String forkName = repository.getForkName();
                        final String forkLocataion = repository.getLocation();
                        try {
                            final boolean newRemote = remotes.add(forkName);
                            if (newRemote) {
                                Logger.logProgress("Adding remote " + count + "/" + forksList.size() + ": " + forkName);
                                git.remoteAdd()
                                        .setName(forkName)
                                        .setUri(new URIish(forkLocataion))
                                        .call();
                                newRemotes.add(forkName);
                            }
                        } catch (final Exception e) {
                            Logger.logError(e.getMessage());
                        }
                    }
                    count = 0;
                    for (final RepositoryProperties repository : forksList) {
                        count++;
                        final String forkName = repository.getForkName();
                        final String forkMaster = repository.getMasterBranchName();
                        try {
                            if (forceFetch || newRemotes.contains(forkName)) {
                                Logger.logProgress(
                                        "Fetching remote " + count + "/" + forksList.size() + ": " + forkName);
                                git.fetch()
                                        .setRemote(forkName)
                                        .setRefSpecs(new RefSpec("+refs/heads/" + forkMaster + ":refs/remotes/"
                                                + forkName + "/" + forkMaster))
                                        .setTimeout(FETCH_TIMEOUT) //
                                        .call();
                            }
                        } catch (final Exception e) {
                            Logger.logError(e.getMessage());
                        }
                    }
                    return true;
                } else {
                    Logger.logInfo("Ignoring: " + getName() + " (More than " + forkLimit + " forks)");
                    return false;
                }
            } finally {
                Main.tabFormatter.decTabLevel();
            }
        } catch (final Exception e) {
            Logger.logError(e);
            return false;
        }
    }
}
