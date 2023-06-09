package org.spldev.varcs.structure;

import java.util.*;

import org.prop4j.*;
import org.spldev.varcs.*;

public class LineNode extends DataNode<String> {

	protected List<Node> ppConditions;
	protected int presenceCondition = -1;

	public LineNode(String data, int condition) {
		super(data, condition);
	}

	public int getPresenceCondition() {
		return presenceCondition;
	}

	public void setPresenceCondition(int presenceCondition) {
		this.presenceCondition = presenceCondition;
	}

	public List<Node> getPPConditions() {
		return ppConditions;
	}

	public void setPPConditions(List<Node> ppCondition) {
		ppConditions = ppCondition;
	}

	public void buildPresenceCondition(FileMap fileMap) {
		if (ppConditions != null) {
			setPresenceCondition(fileMap.getConditionDictionary().getIndexSynced(
				new Or(ppConditions)
			));
			ppConditions = null;
		}
	}

}
