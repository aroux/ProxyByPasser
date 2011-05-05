package com.proxy.bypasser.crypt;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

public class PrivacyMaker {

	private String algorithm;
	private Integer keySize;
	private String dataPath;
	private String keyFileName;

	private Key key = null;
	private Cipher cipher = null;

	private File getDataDirectory() {
		String homeDirectory = System.getProperty("user.home");
		String absoluteConfigPath = homeDirectory + File.separator
				+ dataPath;
		File f = new File(absoluteConfigPath);
		if (f.exists()) {
			if (!f.isDirectory()) {
				throw new RuntimeException(dataPath
						+ " already exists, but is not a directory.");
			}
		} else {
			f.mkdir();
		}
		return f;
	}

	private void generateKey() throws NoSuchAlgorithmException {
		KeyGenerator kg = KeyGenerator.getInstance(algorithm);
		kg.init(keySize);
		key = kg.generateKey();
	}

	private void readKeyFromFile(File file) throws FileNotFoundException,
			IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		key = (Key) ois.readObject();
		ois.close();
	}

	private void writeKeyToFile(File file) throws FileNotFoundException,
			IOException {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
				file));
		oos.writeObject(key);
		oos.close();
	}

	private void readKey() throws NoSuchAlgorithmException,
			FileNotFoundException, IOException, ClassNotFoundException {
		File dataDir = getDataDirectory();
		File keyFile = new File(dataDir.getAbsolutePath() + File.separator
				+ keyFileName);
		if (!keyFile.exists()) {
			generateKey();
			writeKeyToFile(keyFile);
		} else {
			readKeyFromFile(keyFile);
		}
	}

	public void init() throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException {
		cipher = Cipher.getInstance(algorithm);
		readKey();
	}

	public byte[] encrypt(byte[] input, int size) throws InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {
		cipher.init(Cipher.ENCRYPT_MODE, key);
		return cipher.doFinal(input, 0, size);
	}

	public byte[] decrypt(byte[] encryptionBytes, int size)
			throws InvalidKeyException, BadPaddingException,
			IllegalBlockSizeException {
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] recoveredBytes = cipher.doFinal(encryptionBytes, 0, size);
		return recoveredBytes;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public void setKeySize(Integer keySize) {
		this.keySize = keySize;
	}

	public void setDataPath(String dataPath) {
		this.dataPath = dataPath;
	}

	public void setKeyFileName(String keyFileName) {
		this.keyFileName = keyFileName;
	}
}
