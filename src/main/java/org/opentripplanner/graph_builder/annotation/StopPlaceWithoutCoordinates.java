package org.opentripplanner.graph_builder.annotation;

public class StopPlaceWithoutCoordinates extends GraphBuilderAnnotation {

    private static final long serialVersionUID = 1L;

    public static final String FMT = "%s  does not contain any coordinates.";

    final String stopPlaceId;

    public StopPlaceWithoutCoordinates(String stopPlaceId) {
        this.stopPlaceId = stopPlaceId;
    }

    @Override
    public String getMessage() {
        return String.format(FMT, stopPlaceId);
    }
}
