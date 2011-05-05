package com.proxy.bypasser.io.exc;

import java.io.IOException;

public class FatalIOException extends IOException {

	private static final long serialVersionUID = 6991926356811778921L;
	
	public FatalIOException(IOException e) {
		super(e);
	}
}
