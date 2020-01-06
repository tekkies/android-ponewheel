package net.kwatts.powtools;

import de.artcom.hsm.State;

public class DisabledState extends State {
    public static final String ID = "Connection Disabled";

    public DisabledState() {
        super(ID);
    }

}
