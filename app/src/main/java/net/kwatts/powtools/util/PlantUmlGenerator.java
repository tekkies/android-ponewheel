package net.kwatts.powtools.util;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
            appendLine(String.format("state %s {", state));

            getDescendantStates(state);

            appendLine("}");
        }

            appendLine("@enduml");
        writeToFile(plantUml.toString());
        Timber.i(plantUml.toString());
            return plantUml.toString();
        }

    private void writeToFile(String string) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("/sdcard/bluetooth.plantuml", "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        writer.println(string);
        writer.close();
    }

    private Object getDescendantStates(State state) {
        Method getDescendantStatesMethod = null;
        try {
            getDescendantStatesMethod = state.getClass().getDeclaredMethod("getDescendantStates");
            getDescendantStatesMethod.setAccessible(true);
            Object decendentStates = getDescendantStatesMethod.invoke(state);
            return decendentStates;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
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
