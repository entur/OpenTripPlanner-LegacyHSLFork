package org.opentripplanner.graph_builder.annotation;

public class NonUniqueRouteName extends GraphBuilderAnnotation {

    private static final long serialVersionUID = 1L;

    public static final String FMT = "Route had non-unique name. Generated one to ensure uniqueness of TripPattern names: {}";

    final String generatedRouteName;

    public NonUniqueRouteName(String generatedRouteName) {
        this.generatedRouteName = generatedRouteName;
    }

    @Override
    public String getMessage() {
        return String.format(FMT, generatedRouteName);
    }
}
