package com.proxy.bypasser.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.proxy.bypasser.crypt.PrivacyMaker;
import com.proxy.bypasser.http.SecureHttpServer;

public class ServerMain {

	/**
	 * @param args
	 * @throws ClassNotFoundException 
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws HttpException 
	 * @throws IllegalBlockSizeException 
	 * @throws BadPaddingException 
	 * @throws InvalidKeyException 
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, HttpException {
		ApplicationContext context =
		    new ClassPathXmlApplicationContext("contexts/applicationContext-server.xml");
		
		// Init privacy maker
		PrivacyMaker pm = context.getBean("privacy.maker", PrivacyMaker.class);
		pm.init();
		SecureHttpServer server = context.getBean("secure.http.server", SecureHttpServer.class);
		server.run();
	}

}
