/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2021 Sergei Semenkov
// All Rights Reserved.
*/

package mondrian.rolap;

public abstract class RolapAction {
    private final String name;
    private final String caption;
    private final String description;

    public RolapAction(
            String name,
            String caption,
            String description
    ) {
        this.name = name;
        this.caption = caption;
        this.description = description;
    }

    public String getName() { return this.name; }

    public String getCaption() { return this.caption; }

    public String getDescription() { return this.description; }
}

