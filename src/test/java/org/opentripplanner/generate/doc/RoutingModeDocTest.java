package org.opentripplanner.generate.doc;

import static org.opentripplanner.framework.io.FileUtils.assertFileEquals;
import static org.opentripplanner.framework.io.FileUtils.readFile;
import static org.opentripplanner.framework.io.FileUtils.writeFile;
import static org.opentripplanner.generate.doc.framework.DocsTestConstants.DOCS_ROOT;
import static org.opentripplanner.generate.doc.framework.DocsTestConstants.TEMPLATE_ROOT;
import static org.opentripplanner.generate.doc.framework.TemplateUtil.replaceSection;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.opentripplanner.generate.doc.framework.DocBuilder;
import org.opentripplanner.generate.doc.framework.GeneratesDocumentation;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transit.model.basic.TransitMode;

@GeneratesDocumentation
public class RoutingModeDocTest {

  private static final File TEMPLATE = new File(TEMPLATE_ROOT, "RoutingModes.md");
  private static final File OUT_FILE = new File(DOCS_ROOT, "RoutingModes.md");

  @Test
  public void updateDocs() {
    // Read and close inout file (same as output file)
    String doc = readFile(TEMPLATE);
    String original = readFile(OUT_FILE);

    var streetBuilder = new DocBuilder();

    // Street modes
    streetBuilder.header(2, "Street modes", "Street modes");
    // TODO: Shouln't typedescription be static, as it is for the whole of StreetMode?
    // Or add another field to the DocumentedEnum that would describe the enum as a whole?
    streetBuilder.addSection(StreetMode.CAR.typeDescription());

    Arrays
      .stream(StreetMode.values())
      .sorted(Comparator.comparing(Enum::name))
      .filter(m -> m != StreetMode.NOT_SET)
      .forEach(m -> {
        streetBuilder.header(4, m.name(), m.name());
        streetBuilder.addSection(m.enumValueDescription());
      });

    doc = replaceSection(doc, "street-modes", streetBuilder.toString());

    // Transit modes
    var transitBuilder = new DocBuilder();
    transitBuilder.header(2, "Transit modes", "Transit modes");

    // TODO: Shouln't typedescription be static, as it is for the whole of TransitMode?
    // Or add another field to the DocumentedEnum that would describe the enum as a whole?
    transitBuilder.addSection(TransitMode.BUS.typeDescription());

    Arrays
      .stream(TransitMode.values())
      .sorted(Comparator.comparing(Enum::name))
      .forEach(m -> {
        transitBuilder.header(4, m.name(), m.name());
        transitBuilder.addSection(m.enumValueDescription());
      });

    doc = replaceSection(doc, "transit-modes", transitBuilder.toString());

    writeFile(OUT_FILE, doc);

    assertFileEquals(original, OUT_FILE);
  }
}
