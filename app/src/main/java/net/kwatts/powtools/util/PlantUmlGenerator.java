package net.kwatts.powtools.util;

import java.lang.reflect.Field;
import java.util.List;

import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import timber.log.Timber;

class PlantUmlGenerator {
    StringBuilder plantUml;
    public String getPlanUml(StateMachine stateMachine) {
            plantUml = new StringBuilder();

            plantUml.append("@startuml\n" +
                    "\n" +
                    "title Simple Composite State Model\n" +
                    "[*] --> NeilDiamond\n" +
                    "state NeilDiamond \n" +
                    "\n" +
                    "state \"Neil Diamond\" as NeilDiamond {\n" +
                    "  state Dancing\n" +
                    "  state Singing\n" +
                    "  state Smiling\n" +
                    "  Dancing --> Singing\n" +
                    "  Singing --> Smiling\n" +
                    "  Smiling --> Dancing\n" +
                    "}\n" +
                    "\n");


        List<State> stateList = getStateList(stateMachine);

        for (State state:stateList) {
            appendLine(String.format("state %s", state));
        }

            appendLine("@enduml");
            Timber.i(plantUml.toString());
            return plantUml.toString();
    }

    private List<State> getStateList(StateMachine stateMachine) {
        try {
            Field mStateList = stateMachine.getClass().getDeclaredField("mStateList");
            mStateList.setAccessible(true);
            List<State> stateList = (List<State>) mStateList.get(stateMachine);
            return stateList;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void appendLine(String string) {
        plantUml.append(string);
        plantUml.append(System.lineSeparator());
    }
}
