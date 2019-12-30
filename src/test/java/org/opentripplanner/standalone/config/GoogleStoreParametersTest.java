package org.opentripplanner.standalone.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GoogleStoreParametersTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

    private GoogleStoreParameters subject;


    @Test
    public void testLoadFromJson() throws JsonProcessingException {
        // given:
        subject = GoogleStoreParameters.fromJson(
                MAPPER.readTree(
                        "{ uriScheme: 'myGs', credentialsFilename : 'cfile' }"
                )
        );

        // then:
        assertEquals("myGs", subject.uriScheme());
        assertEquals("cfile", subject.credentialsFile());
        assertEquals(
                "GoogleStoreParameters{uriScheme='myGs', credentialsFilename='cfile'}",
                subject.toString()
        );
    }

    @Test
    public void testDefaults() {
        // given:
        subject = GoogleStoreParameters.fromJson(MissingNode.getInstance());

        // then:
        assertEquals("gs", subject.uriScheme());
        assertNull(subject.credentialsFile());
        assertEquals(
                "GoogleStoreParameters{uriScheme='gs', credentialsFilename=null}",
                subject.toString()
        );
    }
}