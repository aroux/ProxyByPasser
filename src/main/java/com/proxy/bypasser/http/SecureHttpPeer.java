package com.proxy.bypasser.http;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.NoSuchAlgorithmException;

import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.SerializableEntity;

import com.proxy.bypasser.crypt.PrivacyMaker;
import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.io.SecureInputStream;
import com.proxy.bypasser.io.SecureOutputStream;
import com.proxy.bypasser.tcp.TcpForwarder;
import com.proxy.bypasser.utils.IOUtils;

public class SecureHttpPeer {
	
	private PrivacyMaker pm;

	protected TcpForwarder tcpForwarder;
	
	public SecureHttpPeer() {
	}
	
	private Object readObjectFromEntity(HttpEntity entity) throws IllegalStateException, IOException, ClassNotFoundException {
			InputStream is = entity.getContent();
			SecureInputStream sis = new SecureInputStream(is, pm);
			ObjectInputStream ois = new ObjectInputStream(sis);
			Object object = ois.readObject();
			ois.close();
			is.close();
			return object;
	}
	
	public Request readRequestFromEntity(HttpEntity entity) throws IllegalStateException, IOException, ClassNotFoundException {
		return (Request) readObjectFromEntity(entity);
	}
	
	public Response readResponseFromEntity(HttpEntity entity) throws IllegalStateException, IOException, ClassNotFoundException {
		return (Response) readObjectFromEntity(entity);
	}
	
	protected BytesArray readAllBytesFromEntity(HttpEntity entity) throws IOException {
		InputStream is = entity.getContent();
		BytesArray bytesArray = IOUtils.readAvailableFromIS(is, true);
		is.close();
		return bytesArray;
	}
	
	protected HttpEntity genEncryptedEntityFromObject(Object o) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(new SecureOutputStream(bos, pm));
		//ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(o);
	    oos.flush();
	    oos.close();
	    bos.close();
	    ByteArrayEntity bae = new ByteArrayEntity(bos.toByteArray());
	    Object ob = readObjectFromEntity(bae);
	    return bae;
	}
	
	public void setPm(PrivacyMaker pm) {
		this.pm = pm;
	}
	
}
