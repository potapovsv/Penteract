/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2022 Sergei Semenkov
// All Rights Reserved.
*/

package mondrian.xmla;

/*
Using in Execute Command Alter
 */
public class ServerObject {
    private String databaseID;

    public ServerObject(String databaseID){
        this.databaseID = databaseID;
    }

    public String getDatabaseID() {
        return this.databaseID;
    }
}
