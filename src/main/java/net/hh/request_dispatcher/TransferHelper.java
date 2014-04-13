package net.hh.request_dispatcher;

import org.apache.log4j.Logger;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import org.zeromq.ZMsg;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Created by hartmann on 4/10/14.
 */
class TransferHelper {

    private static final Logger log = Logger.getLogger(TransferHelper.class);

    /**
     * Closes socket on ETERM
     */
    public static void sendMessage(ZMQ.Socket socket, Serializable request, int callbackId) throws IOException {
        try {
            ZMsg out = new ZMsg();

            out.push(SerializationHelper.serialize(request));
            out.push(int2bytes(callbackId));
            out.push(new byte[0]); // Add empty frame as REQ envelope

            boolean rc = out.send(socket);
            if (!rc) throw new ZMQException.IOException(new IOException("Error sending message"));
        } catch (ZMQException e) {
            if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()){
                log.debug("Received ETERM. Closing socket.");
                socket.close();
                throw new IOException(e);
            } else {
                throw new IOException(e);
            }
        }
    }

    /**
     * Receive and parse multipart message.
     * Closes socket on ETERM
     *
     * @param socket    zmq socket to receive message on
     * @param flag      parameters passed to ZMsg.recvMsg()
     * @return reply    null if no message is received
     *
     * @throws ProtocolException    RequestDispatcher protocol violated
     * @throws IOException          other IOErrors, e.g. ETERM
     */
    public static TransferWrapper recvMessage(ZMQ.Socket socket, int flag) throws IOException {
        {
            try {
                ZMsg message = ZMsg.recvMsg(socket, flag);

                if (message == null) {
                    throw new ProtocolException("No message received.");
                }

                ZFrame[] parts = message.toArray(new ZFrame[3]);

                // Expect message to have three parts:
                // 0. Empty Delimiter Frame
                // 1. Serialized callback ID
                // 2. Serialized payload

                if (parts.length != 3) {
                    throw new ProtocolException("Wrong number of Frames. Expected 3.");
                }
                if (parts[0].size() != 0) {
                    throw new ProtocolException("First frame is not empty.");
                }

                return new TransferWrapper(
                        SerializationHelper.deserialize(parts[2].getData()),
                        bytes2int(parts[1].getData())
                );
            } catch (ZMQException e) {
                if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()){
                    log.debug("Received ETERM. Closing socket.");
                    socket.close();
                    throw new IOException(e);
                } else {
                    throw new IOException(e);
                }
            }
        }
    }


    public static byte[] int2bytes(int i) {
        return BigInteger.valueOf(i).toByteArray();
    }

    public static int bytes2int(byte[] data) {
        return new BigInteger(data).intValue();
    }

    public static class ProtocolException extends IOException {
        public ProtocolException(String message) {
            super(message);
        }
    }

}