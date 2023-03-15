package org.opentripplanner.raptor.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IncValueRelaxFunctionTest {

  @Test
  void relaxTime() {
    assertEquals(100, IncValueRelaxFunction.ofIncreasingTime(1.0, 0).relax(100));
    assertEquals(75, IncValueRelaxFunction.ofIncreasingTime(1.5, 0).relax(50));
    assertEquals(100, IncValueRelaxFunction.ofIncreasingTime(1.0, 50).relax(50));
    assertEquals(150, IncValueRelaxFunction.ofIncreasingTime(1.25, 25).relax(100));
    assertEquals(16, IncValueRelaxFunction.ofIncreasingTime(32.99 / 32.0, 0).relax(16));
    assertEquals(17, IncValueRelaxFunction.ofIncreasingTime(33.01 / 32.0, 0).relax(16));
  }

  @Test
  void relaxCost() {
    assertEquals(100, IncValueRelaxFunction.ofCost(1.0).relax(100));
    assertEquals(75, IncValueRelaxFunction.ofCost(1.5).relax(50));
    assertEquals(100, IncValueRelaxFunction.ofCost(1.0, 50).relax(50));
    assertEquals(150, IncValueRelaxFunction.ofCost(1.25, 25).relax(100));
    assertEquals(16, IncValueRelaxFunction.ofCost(32.99 / 32.0, 0).relax(16));
    assertEquals(17, IncValueRelaxFunction.ofCost(33.01 / 32.0, 0).relax(16));

    assertThrows(IllegalArgumentException.class, () -> IncValueRelaxFunction.ofCost(-0.1));
    assertThrows(IllegalArgumentException.class, () -> IncValueRelaxFunction.ofCost(4.01));
    assertThrows(IllegalArgumentException.class, () -> IncValueRelaxFunction.ofCost(1, -1));
    assertThrows(
      IllegalArgumentException.class,
      () -> IncValueRelaxFunction.ofCost(1, IncValueRelaxFunction.SLACK_COST_MAX + 1)
    );
  }

  @Test
  void testToString() {
    assertEquals("f()=16/16 * v + 12", IncValueRelaxFunction.ofIncreasingTime(1.0, 12).toString());
  }

  @Test
  void testEqualsAndHashCode() {
    var a = IncValueRelaxFunction.ofIncreasingTime(1.0, 12);
    var same = IncValueRelaxFunction.ofIncreasingTime(1.0, 12);
    var diffRatio = IncValueRelaxFunction.ofIncreasingTime(17.0 / 16.0, 12);
    var diffSlack = IncValueRelaxFunction.ofIncreasingTime(1.0, 13);

    assertEquals(a, a);
    assertEquals(a, same);
    assertEquals(a.hashCode(), same.hashCode());
    assertNotEquals(a, diffRatio);
    assertNotEquals(a.hashCode(), diffRatio.hashCode());
    assertNotEquals(a, diffSlack);
    assertNotEquals(a.hashCode(), diffSlack.hashCode());
  }
}
