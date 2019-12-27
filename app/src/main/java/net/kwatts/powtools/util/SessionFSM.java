package net.kwatts.powtools.util;


import java.util.ArrayList;
import java.util.List;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;
import de.artcom.hsm.TransitionKind;

class SessionFSM {

    public static final String SWITCHED_ON = "switched_on";
    public static final String VOLUME_UP = "volume_up";
    public static final String SWITCHED_OFF = "switched_off";
    public static final String VOLUME_DOWN = "volume_down";
    public static final String OFF = "off";
    public static final String ON = "on";
    public static final String LOUD = "loud";
    public static final String QUIET = "quiet";
    private int hoorays = 0;

    //FsmState state;

    public SessionFSM() {

    }

    public void init() {




        Action onEnterLoud = new Action() {
            @Override
            public void run() {

            }
        };
        Action onEnterQuiet = new Action() {
            @Override
            public void run() {

            }
        };
        Action onEnterOn = new Action() {
            @Override
            public void run() {

            }
        };
        Action onEnterOff = new Action() {
            @Override
            public void run() {

            }
        };

        State loud = new State(LOUD)
                .onEnter(onEnterLoud);
        State quiet = new State(QUIET)
                .onEnter(onEnterQuiet);

        quiet.addHandler(VOLUME_UP, loud, TransitionKind.External);
        loud.addHandler(VOLUME_DOWN, quiet, TransitionKind.External);

        Sub on = new Sub(ON,
                new StateMachine(quiet, loud))
                .onEnter(onEnterOn);

        State off = new State(OFF)
                .onEnter(onEnterOff);

        on.addHandler(SWITCHED_OFF, off, TransitionKind.External);
        off.addHandler(SWITCHED_ON, on, TransitionKind.External);

        StateMachine sm = new StateMachine(off, on);
        sm.init();

        sm.handleEvent(SWITCHED_ON);
        sm.handleEvent(VOLUME_UP);
        sm.handleEvent(VOLUME_DOWN);
        sm.handleEvent(VOLUME_UP);
        sm.handleEvent(SWITCHED_OFF);
        sm.handleEvent(SWITCHED_ON);
/*
        StateMachineBuilder stateMachine = new StateMachineBuilder();
        stateMachine
                .State(InitialState.class)
                    .on(StartEvent.class).execute(StartAction.class).moveTo(ScanningState.class)
                .State(ScanningState.class).Start();
*/






        //state.Post(new StartAction());


        //Timber.i("We got back %s", userName);
    }


/*
private class InitialState extends FsmState {
}

private class FsmState {
    public void Post(Action action) {
    }
}

    private class Event {

    }

    private class StartEvent {
    }


    private class Action {

    }

    private class StartAction extends Action {
    }

    private class StateMachineBuilder {
        ArrayList<Class> states = new ArrayList<Class>();
        public StateMachineBuilder State(Class stateClass) {
            states.add(stateClass);
            return this;
        }

        public StateMachineBuilder on(Class event) {
            return this;
        }


        public StateMachineBuilder execute(Class action) {
        }

        public StateMachineBuilder moveTo(Class<ScanningState> scanningStateClass) {
            return this;
        }

        public void Start() {

        }
    }

    private class ScanningState {
    }*/
}
