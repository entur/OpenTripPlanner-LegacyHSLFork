package org.opentripplanner.ext.vectortiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.vectortiles.layers.stops.DigitransitStopPropertyMapper;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.TransitStopVertex;
import org.opentripplanner.transit.model._data.TransitModelForTest;
import org.opentripplanner.transit.model.site.Stop;

public class StopsLayerTest {

  private Stop stop;

  @BeforeEach
  public void setUp() {
    stop = TransitModelForTest.stopForTest("name", "desc", 50, 10);
  }

  @Test
  public void digitransitVehicleParkingPropertyMapperTest() {
    Graph graph = mock(Graph.class);
    graph.index = mock(GraphIndex.class);

    DigitransitStopPropertyMapper mapper = DigitransitStopPropertyMapper.create(graph);
    Map<String, Object> map = new HashMap<>();
    mapper.map(new TransitStopVertex(graph, stop, null)).forEach(o -> map.put(o.first, o.second));

    assertEquals("F:name", map.get("gtfsId"));
    assertEquals("name", map.get("name"));
    assertEquals("desc", map.get("desc"));
  }
}
