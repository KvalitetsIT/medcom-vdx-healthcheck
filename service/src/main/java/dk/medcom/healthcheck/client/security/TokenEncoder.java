package dk.medcom.healthcheck.client.security;

import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TokenEncoder {

    public static final Logger logger = LoggerFactory.getLogger(TokenEncoder.class);

    public String encode(SecurityToken token) {
        Element tokenXML = token.getToken();
        String documentAsString = getStringFromDoc(tokenXML.getOwnerDocument());
        logger.debug("Token issued by STS [token={}]", documentAsString);
        return encode(documentAsString);
    }

    private String encode(String token) {
        return base64Encode(token);
    }

    private String getStringFromDoc(Document doc) {
        DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();
        return lsSerializer.writeToString(doc);
    }

    private String base64Encode(String in)  {
        return Base64.getEncoder().encodeToString(in.getBytes(StandardCharsets.UTF_8));
    }
}
