package org.opentripplanner.standalone.config;


/**
 * This class serve as a base class for parameters used to configure a repository. Most repositories
 * would define their own extension to this, subclassing this class.
 * <p>
 * See the {@link GoogleStoreParameters} for an example usage.
 */
public class RepositoryParameters {
    private final String uriScheme;

    RepositoryParameters(String uriScheme) {
        this.uriScheme = uriScheme;
    }

    /**
     * The URI scheme(namespace) to use for this repository. All URIs in the 'dataSources' section
     * of the build config with this scheme will be routed to a instance of a repository created
     * using this config. This allows for configuring multiple repositories of the same type like
     * "gs1" and "gs2", to access to Google Storage drives with different configuration.
     * <p>
     * This parameter is optional. Each repository implementation should provide their own default
     * value for this parameter.
     */
    public String uriScheme() {
        return uriScheme;
    }

    @Override
    public String toString() {
        return "RepositoryParameters{" + "uriScheme='" + uriScheme + '\'' + '}';
    }
}
