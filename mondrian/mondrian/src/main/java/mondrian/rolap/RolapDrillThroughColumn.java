/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2021-2022 Sergei Semenkov
// All Rights Reserved.
*/

package mondrian.rolap;

import mondrian.olap.OlapElement;

public abstract class RolapDrillThroughColumn {
    private final String  name;

    protected RolapDrillThroughColumn(String name) {
        this.name = name;
    }

    public String getName() { return this.name; };
    public abstract OlapElement getOlapElement();
}

