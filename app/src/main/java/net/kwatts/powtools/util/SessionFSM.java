package net.kwatts.powtools.util;


import java.util.ArrayList;
import java.util.List;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;
import de.artcom.hsm.TransitionKind;

class SessionFSM {

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

        State loud = new State("loud")
                .onEnter(onEnterLoud);
        State quiet = new State("quiet")
                .onEnter(onEnterQuiet);

        quiet.addHandler("volume_up", loud, TransitionKind.External);
        loud.addHandler("volume_down", quiet, TransitionKind.External);

        Sub on = new Sub("on", new StateMachine(quiet, loud))
                .onEnter(onEnterOn);

        State off = new State("off")
                .onEnter(onEnterOff);

        on.addHandler("switched_off", off, TransitionKind.External);
        off.addHandler("switched_on", on, TransitionKind.External);

        StateMachine sm = new StateMachine(off, on);
        sm.init();

        sm.handleEvent("switched_on");
        sm.handleEvent("volume_up");
        sm.handleEvent("switched_off");
        sm.handleEvent("switched_on");
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
