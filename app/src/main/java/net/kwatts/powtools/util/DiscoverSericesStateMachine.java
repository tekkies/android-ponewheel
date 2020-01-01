package net.kwatts.powtools.util;

import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;

class DiscoverSericesStateMachine extends StateMachine {

    public DiscoverSericesStateMachine() {
        super(new State("Connecting"), new State("Sevices Discovered"));
    }
}
