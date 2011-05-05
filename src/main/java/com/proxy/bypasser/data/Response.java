package com.proxy.bypasser.data;
import java.io.Serializable;


public class Response implements Serializable {

	private static final long serialVersionUID = 8233699034595620267L;

	private final ResponseType responseType;
	
	private final BytesArray data;
	
	public Response(ResponseType requestType, BytesArray data) {
		super();
		this.responseType = requestType;
		this.data = data;
	}

	public BytesArray getData() {
		return data;
	}
	
	public ResponseType getResponseType() {
		return responseType;
	}
	
	public enum ResponseType implements Serializable {
		OK(0), ERROR(1), NO_DATA(2);
		
		private final int code;

		private ResponseType(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}
	}
}
