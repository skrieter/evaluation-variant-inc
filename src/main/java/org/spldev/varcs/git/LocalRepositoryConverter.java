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
package org.spldev.varcs.git;

import de.featjar.util.logging.Logger;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.spldev.varcs.*;

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
