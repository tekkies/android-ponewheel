package net.kwatts.powtools.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;
import uk.co.tekkies.hsm.plantuml.PlantUmlBuilder;
import uk.co.tekkies.hsm.plantuml.PlantUmlUrlEncoder;

public class InkeyCollatorTest {

    @Test
    public void canCollateGoodStream() {
        byte[] stream = Util.StringToByteArrayFastest("098e56:6ded2854e0bb3ee3eb77e58974ee956227");
        InkeyCollator inkeyCollator = new InkeyCollator();
        inkeyCollator.append(stream);
        Assert.assertTrue(inkeyCollator.isFound());
    }

    @Test
    public void canCollateBadStream() {
        byte[] stream = Util.StringToByteArrayFastest("deadbeef:098e56:6ded2854e0bb3ee3eb77e58974ee956227:deadbeef");
        InkeyCollator inkeyCollator = new InkeyCollator();
        inkeyCollator.append(stream);
        Assert.assertTrue(inkeyCollator.isFound());
    }
}
