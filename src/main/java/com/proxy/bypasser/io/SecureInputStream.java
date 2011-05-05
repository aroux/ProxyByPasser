package com.proxy.bypasser.io;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import com.proxy.bypasser.crypt.PrivacyMaker;
import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.utils.IOUtils;

public class SecureInputStream extends InputStream {

	private PrivacyMaker pm;
	
	private InputStream underStream;
	
	private BytesArray currentBuffer;
	
	private int currentPosition;
	
	public SecureInputStream(InputStream arg0, PrivacyMaker pm) throws IOException {
		underStream = arg0;
		currentPosition = 0;
		currentBuffer = new BytesArray(null, 0);
		this.pm = pm;
	}
	
	@Override
	public int read() throws IOException {
		if (currentBuffer.getSize() == currentPosition) {
			try {
				BytesArray data = IOUtils.readAvailableFromIS(underStream, true);
				if (data.getSize() == 0) return -1;
				System.out.println("SecureInputStream -> read: " + data.getSize());
				byte rawDecryptedData[] = pm.decrypt(data.getData(), data.getSize());
				currentBuffer = new BytesArray(rawDecryptedData, rawDecryptedData.length);
				currentPosition = 0;
			} catch (InvalidKeyException e) {
				throw new IOException(e);
			} catch (BadPaddingException e) {
				throw new IOException(e);
			} catch (IllegalBlockSizeException e) {
				throw new IOException(e);
			}
		}
		//System.out.println("Current position: " + currentPosition);
		byte b = currentBuffer.getData()[currentPosition++];
		//System.out.println("byte: " + (0x000000FF & (int) b));
		return 0x000000FF & (int) b;
	}

}
