package ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.processors;

import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.handlers.ErrorManager;
import ar.edu.itba.pdc.chinese_whispers.xmpp_protocol.interfaces.OutputConsumer;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
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
    public final static int MAX_AMOUNT_OF_BYTES = 200;//TODO 10 * 1024; // We allow up to 10 KiB data inside the parser.

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


    /**
     * Constructor.
     *
     * @param outputConsumer An object that will consume output generated by this interpreter.
     */
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
        return outputConsumer.remainingSpace()/4 - amountOfStoredBytes;
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

        ParserResponse response = ParserResponse.EVERYTHING_NORMAL;
        
        try {
            for (int offset = 0; offset < length; offset++) {
                parser.getInputFeeder().feedInput(data, offset, 1);
                response = process();

                // TODO: check order of this lines...
                if (amountOfStoredBytes >= MAX_AMOUNT_OF_BYTES || parser.getDepth() > 10000) {
                    return ParserResponse.POLICY_VIOLATION;
                }
                if (ErrorManager.getInstance().parserResponseErrors().contains(response)) {
                    break;
                }
            }
        } catch (XMLStreamException e) {
            response = ParserResponse.XML_ERROR;
        }
        return response;
    }


    protected void updateStoredBytes(int status) {
        if (status != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
            amountOfStoredBytes = -1;
        }else{
            amountOfStoredBytes++;
        }
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

    /**
     * Gets the status of the parser
     *
     * @return The parser's status.
     */
    protected int getParserStatus() {
        return parserStatus;
    }

    /**
     * Makes the parser move along
     *
     * @throws XMLStreamException If this interpreter has unprocessed data. Be sure to call {@link #process()} between
     *                            calls to this method, which ensures that all data is consumed.
     */
    protected void ignoreText() throws XMLStreamException {
        while (parserStatus == AsyncXMLStreamReader.CHARACTERS && parser.hasNext()) {
            next();
        }
    }


}
