package org.opentripplanner.ext.datastore.gs;

import org.junit.Test;
import org.opentripplanner.datastore.FileType;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class GsDataSourceRepositoryTest {

    private GsDataSourceRepository subject = new GsDataSourceRepository(null);

    @Test
    public void description() {
        assertEquals("Google Cloud Storage", subject.description());
    }

    @Test(expected = IllegalArgumentException.class)
    public void findSource() throws Exception {
        subject.findSource(new URI("file:/a.txt"), FileType.UNKNOWN);
    }
}