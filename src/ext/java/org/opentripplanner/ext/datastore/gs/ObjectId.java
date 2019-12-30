package org.opentripplanner.ext.datastore.gs;

import com.google.cloud.storage.BlobId;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ObjectId {
    /**
     * GCS URL pattern for the Scheme Specific Part, without the 'gs:' prefix Not all rules are
     * validated here, but the following is:
     * <ul>
     *     <li>Bucket names must contain only lowercase letters, numbers, dashes (-),
     *     underscores (_), and dots (.)
     *     <li>Bucket names must contain 3 to 222 characters.
     *     <li>Object names must be at least one character.
     *     <li>Object names should avoid using control characters
     *     this is enforced here, and is strictly just a strong recommendation.
     * </ul>
     * One exception is allowed, the object name may be an empty string({@code ""}), this is used to
     * create a virtual root directory.
     */
    private static final Pattern GS_URL_PATTERN = Pattern.compile(
            "//([\\p{Lower}\\d_.-]{3,222})/([^\\p{Cntrl}]+)?"
    );

    private final String uriScheme;
    private final BlobId blobId;


    ObjectId(String uriScheme, BlobId blobId) {
        this.uriScheme = uriScheme;
        this.blobId = blobId;
    }

    @Override
    public String toString() {
        return toUriString();
    }

    boolean isZipFile() {
        return blobId.getName().endsWith(".zip");
    }

    BlobId blobId() {
        return blobId;
    }

    String uriScheme() {
        return uriScheme;
    }

    static ObjectId toObjectId(URI uri) {
        Matcher m = GS_URL_PATTERN.matcher(uri.getSchemeSpecificPart());

        if(m.matches()) {
            return new ObjectId(uri.getScheme(), BlobId.of(m.group(1), dirName(m.group(2))));
        }
        throw new IllegalArgumentException(
                "The '" + uri + "' is not a legal Google Cloud Storage "
                        + "URL on format: 'gs://bucket-name/object-name'."
        );
    }

    String toUriString() {
        return uriScheme + "://" + blobId.getBucket() + "/" + blobId.getName();
    }

    URI toUri() {
        try {
            return new URI(toUriString());
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getLocalizedMessage(), e);
        }
    }

    boolean isRoot() {
        return "".equals(blobId.getName());
    }


    /* private methods */

    private static String dirName(String objectDir) {
        return objectDir == null ? "" : objectDir;
    }
}
