package org.spldev.varcs.analyzer.cpp;

import java.io.*;
import java.util.*;

public class RawPresenceCondition implements Serializable {

	private static final long serialVersionUID = 231L;

	private final String formula;

	public RawPresenceCondition(String formula) {
		this.formula = formula;
	}

	public String getFormula() {
		return formula;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(formula);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		return Objects.equals(formula, ((RawPresenceCondition) obj).formula);
	}

	@Override
	public String toString() {
		return "RawPresenceCondition [formula=" + formula + "]";
	}

}
