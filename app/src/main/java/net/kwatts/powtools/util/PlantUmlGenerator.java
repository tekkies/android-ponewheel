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
    StringBuilder transitions;
    public String getPlanUml(StateMachine stateMachine) {
            plantUml = new StringBuilder();
            transitions = new StringBuilder();

            plantUml.append("@startuml\n" +
                    "\n" +
                    "title State Model\n");

        List<State> stateList = getStateList(stateMachine);

        for (State state:stateList) {
            appendLine(String.format("state %s {", getName(state)));
            //getTransitions(state);

            List<State> descendantStates = getDescendantStates(state);
            for (State descendantState: descendantStates) {
                appendLine(String.format("    state %s", getName(descendantState)));
            }

            appendLine("}");
        }

            appendLine("@enduml");
        writeToFile(plantUml.toString());
        Timber.i(plantUml.toString());
            return plantUml.toString();
        }

        /*
    private void getHandlers(State state) {
         //mHandlers
        try {
            Field mStateList = state.getClass().getDeclaredField("mHandlers");
            mStateList.setAccessible(true);
            LinkedListMultimap<String, Handler> stateList = (List<State>) mStateList.get(state);
            return stateList;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    */


    private String getName(State state) {
        return state.getId().replace(" ","_");
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

    private List<State> getDescendantStates(State state) {
        Method getDescendantStatesMethod = null;
        try {
            getDescendantStatesMethod = state.getClass().getDeclaredMethod("getDescendantStates");
            getDescendantStatesMethod.setAccessible(true);
            Object decendentStates = getDescendantStatesMethod.invoke(state);
            return (List<State>) decendentStates;
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
