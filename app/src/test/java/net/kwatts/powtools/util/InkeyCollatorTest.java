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

    public static final String GOOD_INKEY = "098e56:6ded2854e0bb3ee3eb77e58974ee956227";

    @Test
    public void canCollateGoodStream() {
        byte[] stream = Util.StringToByteArrayFastest(GOOD_INKEY);
        InkeyCollator inkeyCollator = new InkeyCollator();
        inkeyCollator.append(stream);
        Assert.assertTrue(inkeyCollator.isFound());
        Assert.assertArrayEquals(Util.StringToByteArrayFastest(GOOD_INKEY), inkeyCollator.get());
    }

    @Test
    public void canCollateBadStream() {
        byte[] stream = Util.StringToByteArrayFastest("deadbeef:"+GOOD_INKEY+":deadbeef");
        InkeyCollator inkeyCollator = new InkeyCollator();
        inkeyCollator.append(stream);
        Assert.assertTrue(inkeyCollator.isFound());
        Assert.assertArrayEquals(Util.StringToByteArrayFastest(GOOD_INKEY), inkeyCollator.get());
    }

    @Test
    public void canCollatepartialKey() {
        byte[] stream = Util.StringToByteArrayFastest("deadbeef:098e:"+GOOD_INKEY+":deadbeef");
        InkeyCollator inkeyCollator = new InkeyCollator();
        inkeyCollator.append(stream);
        Assert.assertTrue(inkeyCollator.isFound());
        Assert.assertArrayEquals(Util.StringToByteArrayFastest(GOOD_INKEY), inkeyCollator.get());
    }
}
