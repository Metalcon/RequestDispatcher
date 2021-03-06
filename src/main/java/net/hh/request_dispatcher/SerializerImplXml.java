package net.hh.request_dispatcher;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.BaseException;
import org.apache.commons.lang3.SerializationException;

import java.io.Serializable;

/**
 * Warpper for XStream serialization
 * http://xstream.codehaus.org/tutorial.html
 *
 * Created by hartmann on 3/30/14.
 */
class SerializerImplXml implements Serializer {

    private static final XStream xStream = new XStream();

    public byte[] serialize(Serializable o) {
        try {
            return xStream.toXML(o).getBytes();
        } catch (BaseException e) {
            throw new SerializationException(e);
        }
    }

    public Object deserialize(byte[] data) throws CheckedSerializationException {
        try {
            return xStream.fromXML(new String(data));
        } catch (BaseException e) {
            throw new CheckedSerializationException(e);
        }
    }

}
