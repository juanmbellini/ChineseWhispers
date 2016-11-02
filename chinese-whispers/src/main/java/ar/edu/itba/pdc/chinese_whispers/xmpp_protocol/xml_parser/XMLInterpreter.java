package ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.xml_parser;

import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.interfaces.ApplicationProcessor;
import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.interfaces.OutputConsumer;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;

import javax.xml.stream.XMLStreamException;
import java.util.Deque;

/**
 * Basic byte-level XML interpreter. Handles reading incomplete and invalid XML, as well as "leeting" messages when
 * appropriate and ignoring messages when silenced. The interpreter is instanced with an output byte {@link Deque},
 * which receives the output generated by {@link #process()}.
 */
public class XMLInterpreter {

    private final AsyncXMLInputFactory inputFactory;
    private final AsyncXMLStreamReader<AsyncByteArrayFeeder> parser;

    private int status = 0;
    private boolean isSilenced;
    private boolean silenceRequested;
    //	private boolean isL337ed;
//	private boolean l337Requested;
    private boolean isInBodyTag;
    private boolean isInMessageTag;

    // TODO: get configuration stuff from a configuration provider.


    /**
     * Object that will perform data processing.
     */
    private final ApplicationProcessor applicationProcessor;

    /**
     * Object that will consume output (i.e. parsed) messages.
     */
    private final OutputConsumer outputConsumer;

    /**
     * Constructs a new interpreter.
     *
     * @param applicationProcessor Object that will perform data processing.
     * @param outputConsumer       The object that will consume output (i.e. parsed) data.
     */
    public XMLInterpreter(ApplicationProcessor applicationProcessor, OutputConsumer outputConsumer) {
        inputFactory = new InputFactoryImpl();
        parser = inputFactory.createAsyncForByteArray();
        this.applicationProcessor = applicationProcessor;
        this.outputConsumer = outputConsumer;
    }


    /**
     * Adds bytes to be processed by the interpreter.
     *
     * @param data The data to process.
     * @throws XMLStreamException If this interpreter has unprocessed data. Be sure to call {@link #process()} between
     *                            calls to this method, which ensures that all data is consumed.
     */
    public ParserResponse feed(byte[] data) {
        // TODO: check repeated code
        try {
            parser.getInputFeeder().feedInput(data, 0, data.length);
            return process();
        } catch (XMLStreamException e) {
            e.printStackTrace();
            //TODO catch
            return ParserResponse.XML_ERROR;
        }

    }


    // TODO: Why should this method be called if user is silenced?

    /**
     * Processes all fed data. Transforms messages if leeted, ignores messages if silenced, and sets an error state on
     * invalid XML. Sends all processed data to the Deque specified upon instantiation.
     *
     * @return The number of bytes offered to the output Deque, or -1 if the interpreter is in error state.
     */
    private ParserResponse process() throws XMLStreamException {
        if (!parser.hasNext()) {
            return ParserResponse.EVERYTHING_NORMAL;
        }
        StringBuilder readXML = new StringBuilder();
        while (parser.hasNext()) {
            status = parser.next();
            switch (status) {
                case AsyncXMLStreamReader.START_ELEMENT:
                    //Update status when starting a non-nested element
                    if (parser.getDepth() <= 2) {
//						isL337ed = l337Requested; TODO: check this: Now is commented because the processor will decide if it has to perform l337 processing
                        isSilenced = silenceRequested;
                    }
                    if (parser.getLocalName().equals("body")) {
                        isInBodyTag = true;
                    } else if (parser.getLocalName().equals("message")) {
                        isInMessageTag = true;
                    }

                    //Only process content if NOT message tag or NOT silenced
                    if (!(isInMessageTag && isSilenced)) {
                        readXML.append("<");
                        //Name (and namespace prefix if necessary)
                        if (!parser.getName().getPrefix().isEmpty()) {
                            readXML.append(parser.getPrefix()).append(":");
                        }
                        readXML.append(parser.getLocalName());

                        //Namespaces
                        int namespaceCount = parser.getNamespaceCount();
                        if (namespaceCount > 0) {
                            readXML.append(" ");
                            for (int i = 0; i < namespaceCount; i++) {
                                readXML.append("xmlns");
                                if (!parser.getNamespacePrefix(i).isEmpty()) {
                                    readXML.append(":")
                                            .append(parser.getNamespacePrefix(i));
                                }
                                readXML.append("=\'")
                                        .append(parser.getNamespaceURI(i))
                                        .append("\'")
                                        .append(i < namespaceCount - 1 ? " " : "");
                            }
                        }

                        //Attributes (with namespace prefixes if necessary)
                        int attrCount = parser.getAttributeCount();
                        if (attrCount > 0) {
                            readXML.append(" ");
                            for (int i = 0; i < attrCount; i++) {
                                if (!parser.getAttributePrefix(i).isEmpty()) {
                                    readXML.append(parser.getAttributePrefix(i))
                                            .append(":");
                                }
                                readXML.append(parser.getAttributeLocalName(i))
                                        .append("=\'")
                                        .append(parser.getAttributeValue(i))
                                        .append("\'")
                                        .append(i < attrCount - 1 ? " " : "");
                            }
                        }
                        readXML.append(">");
                    }
                    break;
                case AsyncXMLStreamReader.CHARACTERS:
                    //Only process content if NOT message tag or NOT silenced
                    if (!(isInMessageTag && isSilenced)) {
                        //Append l337ed or normal characters as appropriate
                        applicationProcessor.processMessageBody(readXML, parser.getText().toCharArray(), isInBodyTag);
                    }
                    break;
                case AsyncXMLStreamReader.END_ELEMENT:
                    //Only process content if NOT message tag or NOT silenced
                    if (!(isInMessageTag && isSilenced)) {
                        readXML.append("</");
                        if (!parser.getName().getPrefix().isEmpty()) {
                            readXML.append(parser.getPrefix()).append(":");
                        }
                        readXML.append(parser.getLocalName());
                        readXML.append(">\n");
                    }

                    //Update status
                    if (parser.getLocalName().equals("body")) {
                        isInBodyTag = false;
                    } else if (parser.getLocalName().equals("message")) {
                        isInMessageTag = false;
                    }
                    break;
                case AsyncXMLStreamReader.EVENT_INCOMPLETE:
                    System.out.println(readXML);
                    byte[] bytes = readXML.toString().getBytes();
                    outputConsumer.consumeMessage(bytes);
                    return ParserResponse.EVERYTHING_NORMAL;
                case -1:
                    //TODO throw exception? Remove sout
                    System.out.println("XML interpreter entered error state (invalid XML)");
                    return ParserResponse.XML_ERROR;
            }
        }
        byte[] bytes = readXML.toString().getBytes();
        outputConsumer.consumeMessage(bytes);
        return ParserResponse.EVERYTHING_NORMAL;
    }

    /**
     * Sets whether this stream is silenced. Silenced streams discard all <message> stanzas.
     * <b>NOTE:</b> This setting takes effect upon reaching the next stanza.
     *
     * @param silenced Whether this stream is silenced.
     */
    public void setSilenced(boolean silenced) {
        silenceRequested = silenced;
    }


    // TODO: This is commented because it's the processor who decides if l337 processing is done
    //	/**
//	 * Sets whether this stream is "leeted." Leeted streams transform certain alphabetic characters inside <body>
//	 * stanzas into similar-looking numbers.
//	 * <b>NOTE:</b> This setting takes effect upon reaching the next <body> tag.
//	 *
//	 * @param l337ed Whether this stream is leeted.
//	 */
//	public void setL337ed(boolean l337ed) {
//		l337Requested = l337ed;
//	}

}
