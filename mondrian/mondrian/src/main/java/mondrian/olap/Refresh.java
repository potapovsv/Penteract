// Copyright (c) 2021 Sergei Semenkov.  All rights reserved.

package mondrian.olap;

import java.io.PrintWriter;

public class Refresh extends QueryPart {
    private final String cubeName;

    Refresh(
            String cubeName)
    {
        this.cubeName = cubeName;
    }

    @Override
    public void unparse(PrintWriter pw) {
        pw.print("REFRESH CUBE [" + cubeName + "]");
    }

    @Override
    public Object[] getChildren() {
        return new Object[] {cubeName};
    }

    public String getCubeName() {
        return cubeName;
    }
}
