package org.spldev.varcs.git;

import org.spldev.varcs.*;

public interface RepositoryConverter {

	RepositoryProperties getRepository();

	RepositoryProperties getRepository(String url) throws Exception;

}
