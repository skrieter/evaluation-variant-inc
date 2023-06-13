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
