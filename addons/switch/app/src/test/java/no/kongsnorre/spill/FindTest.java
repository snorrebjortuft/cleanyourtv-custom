package no.kongsnorre.spill;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FindTest {

    @Test public void findsTheSwitchByItsCecName() {
        SwitchActivity.Input apple = new SwitchActivity.Input("hw2", "Apple TV");
        SwitchActivity.Input sw = new SwitchActivity.Input("hw3", "Nintendo Switch 2");
        assertEquals(sw, SwitchActivity.find(Arrays.asList(apple, sw)));
    }

    @Test public void noNameNoGuess() {
        assertNull(SwitchActivity.find(Collections.singletonList(new SwitchActivity.Input("hw1", "HDMI 1"))));
    }
}
