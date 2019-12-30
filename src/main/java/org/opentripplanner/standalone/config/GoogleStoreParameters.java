package org.opentripplanner.standalone.config;

import com.fasterxml.jackson.databind.JsonNode;

public class GoogleStoreParameters extends RepositoryParameters {

    private static final String DEFAULT_URI_NAMESPACE = "gs";

    private final String credentialsFilename;

    private final String isNull = null;

    public static GoogleStoreParameters fromJson(JsonNode node) {
        String uriNameSpaceSchema = node.path("uriScheme").asText(DEFAULT_URI_NAMESPACE);
        String credentialsFilename = node.path("credentialsFilename").asText(null);
        return new GoogleStoreParameters(uriNameSpaceSchema, credentialsFilename);
    }

    private GoogleStoreParameters(String uriScheme, String credentialsFilename) {
        super(uriScheme);
        this.credentialsFilename = credentialsFilename;
    }

    /**
     * Local file system path to Google Cloud Platform service accounts credentials file. The
     * credentials is used to access GS urls. When using GS from outside of the bucket cluster you
     * need to provide a path the the service credentials. Environment variables in the path is
     * resolved.
     * <p>
     * Example: {@code "credentialsFile" : "${MY_GOC_SERVICE}"} or
     * {@code "app-1-3983f9f66728.json" : "~/"}
     * <p>
     * This is a path to a file on the local file system, not an URI.
     * <p>
     * This parameter is optional.
     */
    public String credentialsFile() {
        return credentialsFilename;
    }


    public String toString() {
        return "GoogleStoreParameters{"
                + "uriScheme='" + uriScheme() + '\''
                + ", credentialsFilename=" +
                (credentialsFilename == null ? "null" : '\'' + credentialsFilename + '\'')
                + '}';
    }
}
