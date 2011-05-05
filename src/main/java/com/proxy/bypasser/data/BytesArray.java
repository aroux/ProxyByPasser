package com.proxy.bypasser.data;

import java.io.Serializable;

public class BytesArray implements Serializable {
	
	private static final long serialVersionUID = -4424065905102418021L;

	private final Integer size;
	
	private final byte[] data;
	
	public BytesArray(byte[] data, int size) {
		super();
		this.size = size;
		this.data = data;
	}

	public byte[] getData() {
		return data;
	}
	
	public int getSize() {
		return size;
	}
}