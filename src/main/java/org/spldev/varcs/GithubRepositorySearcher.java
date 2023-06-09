package org.spldev.varcs;

import org.spldev.varcs.git.*;

public class GithubRepositorySearcher {

	public static void main(String[] args) {
		GithubRepositoryConverter.searchRepository("language:C++ language:C stars:>5000 forks:<1000 size:<1000000");
		System.out.println("Finished!");
	}

}
