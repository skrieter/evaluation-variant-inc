package org.spldev.varcs.structure;

import java.util.*;

import org.spldev.varcs.*;

public class ConditionalNode {

	protected int conditionIndex = -1;

	public int getCondition() {
		return conditionIndex;
	}

	public void setCondition(int conditionIndex) {
		this.conditionIndex = conditionIndex;
	}

	public boolean isActive(Map<Object, Boolean> assignment, NodeDictionary nodeDictionary) {
		return nodeDictionary.getCondition(conditionIndex).getValue(assignment) == true;
	}

}
