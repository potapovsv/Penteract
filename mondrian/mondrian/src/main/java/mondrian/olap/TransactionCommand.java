// Copyright (c) 2021 Sergei Semenkov.  All rights reserved.

package mondrian.olap;

import java.io.PrintWriter;

public class TransactionCommand extends QueryPart {

    public enum Command {
        BEGIN,
        COMMIT,
        ROLLBACK
    }
    private final TransactionCommand.Command command;

    TransactionCommand(TransactionCommand.Command command)
    {
        this.command = command;
    }

    @Override
    public void unparse(PrintWriter pw) {
        pw.print( this.command.name() + "TRANSACTION");
    }

    @Override
    public Object[] getChildren() {
        return new Object[] {this.command};
    }

    public TransactionCommand.Command getCommand() {
        return this.command;
    }
}
