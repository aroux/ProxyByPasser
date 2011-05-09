package com.proxy.bypasser.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import com.proxy.bypasser.crypt.PrivacyMaker;

public class SecureOutputStream extends OutputStream {

	private PrivacyMaker pm;
	
	private OutputStream underStream;
	
	private List<byte[]> buffer;
	private int bufferSize;
	
	public SecureOutputStream(OutputStream arg0, PrivacyMaker pm) {
		this.underStream = arg0;
		buffer = new ArrayList<byte[]>();
		bufferSize = 0;
		this.pm = pm;
	}
	
	@Override
	public void write(int arg0) throws IOException {
		byte[] data = {(byte) arg0};
		buffer.add(data);
		bufferSize += 1;
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}
	
	@Override
	public void flush() throws IOException {
		if (bufferSize > 0) {
			byte[] data = new byte[bufferSize];
			int currentPosition = 0;
			for (byte[] d : buffer) {
				System.arraycopy(d, 0, data, currentPosition, d.length);
				currentPosition += d.length;
			}
			try {
				byte[] encryptedData = pm.encrypt(data, bufferSize);
				underStream.write(encryptedData);
			} catch (InvalidKeyException e) {
				throw new IOException(e);
			} catch (BadPaddingException e) {
				throw new IOException(e);
			} catch (IllegalBlockSizeException e) {
				throw new IOException(e);
			}
			
			underStream.flush();
			buffer = new ArrayList<byte[]>();
			bufferSize = 0;
		}
	}
	
	//@Override
	public void flush2() throws IOException {
		underStream.flush();
	}


}
