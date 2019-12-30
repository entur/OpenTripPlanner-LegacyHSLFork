package org.opentripplanner.ext.datastore.gs;

import com.google.cloud.storage.BlobId;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ObjectIdTest {

    @Test
    public void toObjectId() throws URISyntaxException {
        ObjectId objectId = ObjectId.toObjectId(new URI("gs1://nalle/puh"));
        assertEquals("gs1", objectId.uriScheme());
        assertEquals("nalle", objectId.blobId().getBucket());
        assertEquals("puh", objectId.blobId().getName());
    }

    @Test
    public void toBlobIdWithInvalidURL() throws URISyntaxException {
        // given:
        String illegalBucketName = "gs://n/puh";
        try {
            // when:
            ObjectId.toObjectId(new URI(illegalBucketName));

            fail("An exception is expected");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(illegalBucketName));
        }
    }

    @Test
    public void toUri() {
        // Given
        ObjectId subject = new ObjectId("gs", BlobId.of("bucket", "blob"));

        // Then
        assertEquals("gs://bucket/blob",  subject.toUriString());
        assertEquals("gs://bucket/blob",  subject.toUri().toString());
    }

    @Test
    public void testRoot() throws URISyntaxException {
        ObjectId root = ObjectId.toObjectId(new URI("gs://bucket/"));
        assertEquals("gs://bucket/", root.toString());

        URI uri = root.toUri();
        assertEquals("gs://bucket/",  uri.toString());

        BlobId blobId = root.blobId();
        assertEquals("bucket",  blobId.getBucket());
        assertEquals("",  blobId.getName());
    }
}