package net.kwatts.powtools.connection.states;

import de.artcom.hsm.State;

public class DisabledState extends State {
    public static final String ID = "Connection Disabled";

    public DisabledState() {
        super(ID);
    }

}
