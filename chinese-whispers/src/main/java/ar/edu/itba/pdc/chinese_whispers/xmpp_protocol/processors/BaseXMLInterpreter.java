package ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.processors;

import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.interfaces.OutputConsumer;
import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.xml_parser.ParserResponse;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;

import javax.xml.stream.XMLStreamException;

/**
 * Created by jbellini on 11/11/16.
 */
public abstract class BaseXMLInterpreter {

    /**
     * Says how many bytes this interpreter can hold at most.
     */
    public final static int MAX_AMOUNT_OF_BYTES = 10 * 1024; // We allow up to 10 KiB data inside the parser.

    /**
     * The XML parser.
     */
    protected final AsyncXMLStreamReader<AsyncByteArrayFeeder> parser;
    /**
     * Holds the last parser's event
     */
    private int parserStatus;
    /**
     * Holds how many bytes the parser has in its internal buffer.
     */
    protected int amountOfStoredBytes;

    /**
     * Object that will consume output.
     */
    protected final OutputConsumer outputConsumer;


    protected BaseXMLInterpreter(OutputConsumer outputConsumer) {
        this.parser = new InputFactoryImpl().createAsyncForByteArray();
        this.outputConsumer = outputConsumer;
        this.amountOfStoredBytes = 0;
    }


    /**
     * Returns how many bytes can be fed to this interpreter.
     *
     * @return The amount of bytes that can be fed to this interpreter
     */
    public int remainingSpace() {
        return (outputConsumer.remainingSpace() - amountOfStoredBytes) / 4;
    }

    /**
     * Adds bytes to be processed by the interpreter.
     *
     * @param data   The data to process.
     * @param length The amount of data that will be processed.
     * @return The result of processing the given data.
     * @throws XMLStreamException If this interpreter has unprocessed data. Be sure to call {@link #process()} between
     *                            calls to this method, which ensures that all data is consumed.
     */
    public ParserResponse feed(byte[] data, int length) {

        if (data == null || length < 0 || length > data.length) {
            throw new IllegalArgumentException(); // return internal server error?
        }
        if (length > remainingSpace()) {
            return ParserResponse.POLICY_VIOLATION;
        }


        // TODO: check repeated code
        try {
            for (int offset = 0; offset < length; offset++) {
                parser.getInputFeeder().feedInput(data, offset, 1);
                process();
                // TODO: check order of this lines...
                if (amountOfStoredBytes >= MAX_AMOUNT_OF_BYTES || parser.getDepth() > 10000) {
                    return ParserResponse.POLICY_VIOLATION;
                }
            }
        } catch (XMLStreamException e) {
            return ParserResponse.XML_ERROR;
        }
        return ParserResponse.EVERYTHING_NORMAL;
    }


    protected abstract ParserResponse process() throws XMLStreamException;


    /**
     * Reads until the next XML event, as specified by {@link AsyncXMLStreamReader#next()}.
     *
     * @return The current event code.
     */
    protected int next() throws XMLStreamException {
        parserStatus = parser.next();
        return parserStatus;
    }

    protected int getParserStatus() {
        return parserStatus;
    }


}
