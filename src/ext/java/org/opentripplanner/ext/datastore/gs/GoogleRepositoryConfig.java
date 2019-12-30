package org.opentripplanner.ext.datastore.gs;

import org.opentripplanner.datastore.RepositoryConfig;

public class GoogleRepositoryConfig implements RepositoryConfig {
    private final String credentialsFilename;
    private final String uriScheme;

    public GoogleRepositoryConfig(String credentialsFilename, String uriScheme) {
        this.credentialsFilename = credentialsFilename;
        this.uriScheme = uriScheme;
    }

    @Override
    public String uriScheme() {
        return uriScheme;
    }

    /**
     * Local file system path to Google Cloud Platform service accounts credentials file. The
     * credentials is used to access GS urls. When using GS from outside of the bucket cluster you
     * need to provide a path the the service credentials.
     * <p>
     * This is a path to a file on the local file system.
     * <p>
     * This parameter is optional.
     */
    String credentialsFilename() {
        return credentialsFilename;
    }

}
