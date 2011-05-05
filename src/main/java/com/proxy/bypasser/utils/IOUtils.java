package com.proxy.bypasser.utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.tcp.TcpForwarder;



public class IOUtils {
	
	public static int READ_BUFFER_SIZE = 16384;
	
	private static Logger logger = Logger.getLogger(IOUtils.class);
	
	/**
	 * Reads all bytes directly available from given input stream without blocking
	 * @param is
	 * @return the read data
	 * @throws IOException
	 */
	public static BytesArray readAvailableFromIS(InputStream is, boolean doNotCheckAvailableFirstRead) throws IOException {
		List<BytesArray> dataReadArray = new LinkedList<BytesArray>();
		
		// Read as much as possible
		int totalReadSize = 0;
		if (doNotCheckAvailableFirstRead || is.available() != 0) {
			do {
				byte[] newData = new byte[READ_BUFFER_SIZE];
				logger.debug("Blocking read on inputstream.");
				int readSize = is.read(newData, 0, READ_BUFFER_SIZE);
				if (readSize > 0 ){
					dataReadArray.add(new BytesArray(newData, readSize));
					totalReadSize += readSize;
				}
			} while (is.available() != 0);
		}
		// Reconstruct the bytes array
		return mergeBytesArrays(dataReadArray);
	}
	
	private static BytesArray mergeBytesArrays(List<BytesArray> dataReadArray) {
		if (dataReadArray.size() > 0) {
			int totalReadSize = 0;
			for (BytesArray ba : dataReadArray) {
				totalReadSize += ba.getSize();
			}
			
			int copiedSize = 0;
			byte[] data = new byte[totalReadSize];
			for (BytesArray ba : dataReadArray) {
				System.arraycopy(ba.getData(), 0, data, copiedSize, ba.getSize());
				copiedSize += ba.getSize();
			}
			return new BytesArray(data, copiedSize);
		}
		return new BytesArray(null, 0);
	}
	
	public static String byteArrayToHexString(byte[] b) {
	    StringBuffer sb = new StringBuffer(b.length * 2);
	    for (int i = 0; i < b.length; i++) {
	      int v = b[i] & 0xff;
	      if (v < 16) {
	        sb.append('0');
	      }
	      sb.append(Integer.toHexString(v));
	    }
	    return sb.toString().toUpperCase();
	  }
	
	
}
