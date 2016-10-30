package ar.edu.itba.pdc.chinese_whispers.connection;

import java.nio.channels.SelectionKey;

/**
 * Created by jbellini on 27/10/16.
 */
public interface TCPServerHandler extends TCPHandler {

	/**
	 * Handles the accept operation.
	 *
	 * @param key The key that contains the socket channel that has a pending accept.
	 */
	void handleAccept(SelectionKey key);
}