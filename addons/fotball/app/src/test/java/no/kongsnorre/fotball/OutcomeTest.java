package no.kongsnorre.fotball;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OutcomeTest {

    private static Nifs.Match match(int homeId, int awayId, int home, int away) {
        Nifs.Match m = new Nifs.Match();
        m.homeId = homeId;
        m.awayId = awayId;
        m.homeScore = home;
        m.awayScore = away;
        return m;
    }

    @Test public void outcomeIsFromTheTeamsSide() {
        assertEquals("T", Nifs.outcome(match(4, 14, 2, 0), 14));   // Stabæk 2-0 Odd
        assertEquals("S", Nifs.outcome(match(4, 14, 2, 0), 4));
        assertEquals("U", Nifs.outcome(match(17, 9, 1, 1), 17));
        assertEquals("S", Nifs.outcome(match(9, 17, 0, 3), 17));
    }
}
