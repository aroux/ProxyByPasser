package com.proxy.bypasser.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.proxy.bypasser.crypt.PrivacyMaker;
import com.proxy.bypasser.http.SecureHttpClient;
import com.proxy.bypasser.services.ServiceInfo;
import com.proxy.bypasser.services.ServicesManager;

public class ClientMain {

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
	 * @throws CloneNotSupportedException 
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, HttpException, CloneNotSupportedException {
		ApplicationContext context =
		    new ClassPathXmlApplicationContext("contexts/applicationContext-client.xml");
		
		// Init privacy maker
		PrivacyMaker pm = context.getBean("privacy.maker", PrivacyMaker.class);
		pm.init();
		
		// Run clients for all services
		SecureHttpClient client = context.getBean("secure.http.client", SecureHttpClient.class);
		ServicesManager servicesManager = context.getBean("services.manager", ServicesManager.class);
		List<ServiceInfo> services = servicesManager.getServices();
		for (ServiceInfo serviceInfo : services) {
			SecureHttpClient clientClone = (SecureHttpClient) client.clone();
			clientClone.setServiceInfo(serviceInfo);
			new Thread(clientClone).start();
		}
	}

}
