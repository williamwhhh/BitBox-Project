package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.lang.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;



public class EncryptHelper {

    public EncryptHelper() {
        Security.addProvider(new BouncyCastleProvider());

    }

    /**
     * This method and the private methods below are copied from
     * https://stackoverflow.com/questions/43978146/how-to-convert-ssh-rsa-public-key-to-pem-pkcs1-public-key-format-using-java-7
     * Recover RSA public key from ssh-rsa public key string
     * @param encodedRSAPublicKey Base64 encoded ssh-rsa public key string
     * @return a public key
     */
   public PublicKey convertEncodedRSAPublicKey(String encodedRSAPublicKey) {
        PublicKey publicKey  = null;

        byte[] decodedPublicKey  = Base64.getDecoder().decode(encodedRSAPublicKey);

        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedPublicKey);

            AtomicInteger position = new AtomicInteger();

            //first read algorithm, should be ssh-rsa
            String algorithm = readString(byteBuffer, position);
            System.out.println(algorithm);
            assert "ssh-rsa".equals(algorithm);

            // than read exponent
            BigInteger publicExponent = readMpint(byteBuffer, position);

            // than read modulus
            BigInteger modulus = readMpint(byteBuffer, position);

            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, publicExponent);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            publicKey = kf.generatePublic(keySpec);

            System.out.printf("Modulus: %X%n", modulus);
            System.out.printf("Public exponent: %d %n", publicExponent);
            System.out.printf("%s, is RSAPublicKey: %b%n", publicKey.getClass().getName(), publicKey instanceof RSAPublicKey);

        } catch (Exception e) {
            System.out.println("Exception occurs");
        }

        return publicKey;
    }

    private BigInteger readMpint(ByteBuffer buffer, AtomicInteger pos){
        byte[] bytes = readBytes(buffer, pos);
        if(bytes.length == 0){
            return BigInteger.ZERO;
        }
        return new BigInteger(bytes);
    }

    private String readString(ByteBuffer buffer, AtomicInteger pos){
        byte[] bytes = readBytes(buffer, pos);
        if(bytes.length == 0){
            return "";
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private byte[] readBytes(ByteBuffer buffer, AtomicInteger pos){
        int SIZEOF_INT = 4;
        int len = buffer.getInt(pos.get());
        byte buff[] = new byte[len];
        for(int i = 0; i < len; i++) {
            buff[i] = buffer.get(i + pos.get() + SIZEOF_INT);
        }
        pos.set(pos.get() + SIZEOF_INT + len);
        return buff;
    }

    /**
     * Read the list of authorised keys from the configuration file, and store identities with their corresponding
     * public keys in a HashMap
     * @return a HashMap that store the authorised keys
     */
    public HashMap readPublicKeys() {
        HashMap<String, PublicKey> authorisedKeyHashMap = new HashMap();

        String[] authorisedKeys = Configuration.getConfigurationValue("authorized_keys").split(",");
        for(String authorisedKey: authorisedKeys) {
            String[] key_identity = authorisedKey.split(" ");
            String identity = key_identity[1];
            String key = key_identity[0].replace("ssh-rsa", "");
            PublicKey publicKey = convertEncodedRSAPublicKey(key);

            authorisedKeyHashMap.put(identity, publicKey);
        }

        return authorisedKeyHashMap;
    }

    /**
     * Get the client's own private key from file
     * @return The client's private key
     */
    public PrivateKey getPrivateKey() {
        PrivateKey privateKey = null;
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
            privateKey = generatePrivateKey(factory, "bitboxclient_rsa");
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException | IOException e) {
            System.out.println("Exception occurs");
        }
        return privateKey;
    }

    private PrivateKey generatePrivateKey(KeyFactory factory, String filename) throws InvalidKeySpecException, FileNotFoundException, IOException {
        PemFile pemFile = new PemFile(filename);
        byte[] content = pemFile.getPemObject().getContent();
        PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
        return factory.generatePrivate(privKeySpec);
    }

    /**
     * Generate an AES secret key for encrypting and decrypting payload messages between client and peer
     * @return A base64 encoded secret key string
     */
    public String generateAESkey() {

        String secretKeyString = "";

        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            SecureRandom secureRandom = new SecureRandom();
            keyGenerator.init(128, secureRandom);

            SecretKey secretKey = keyGenerator.generateKey();

            // Convert secret key to key string
            byte[] secretKeyBytes = secretKey.getEncoded();
            secretKeyString = Base64.getEncoder().encodeToString(secretKeyBytes);

        } catch (NoSuchAlgorithmException e) {
            System.out.println("Exception occurs");
        }
        return secretKeyString;
    }

    /**
     * Using the client's public key to encrypt the secret key
     * @param secretKeyString Base64 encoded secrete key string
     * @param publicKey The client's public key
     * @return Base64 encoded, encrypted secret key string
     */
    public String sshEncrypt(String secretKeyString, PublicKey publicKey) {

        String encryptedSecretKey = "";

        // Convert secret key string to byte array for encryption
        byte[] secretKeyBytes = secretKeyString.getBytes();

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

            try {
                cipher.init(Cipher.PUBLIC_KEY, publicKey);

                try {
                    // Encrypt secret key byte array and convert encrypted key to string
                    byte[] cipherText = cipher.doFinal(secretKeyBytes);
                    encryptedSecretKey = Base64.getEncoder().encodeToString(cipherText);

                } catch (IllegalBlockSizeException | BadPaddingException e) {
                    System.out.println("Exception occurs");
                }
            } catch (InvalidKeyException e) {
                System.out.println("Exception occurs");
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            System.out.println("Exception occurs");
        }
        return encryptedSecretKey;
    }

    /**
     * Using the client's own private key to decrypt the secret key received
     * @param encryptedSecretKey Base64 encoded, encrypted secret key string
     * @param privateKey The client's own private key
     * @return Base64 encoded, decrypted secret key string
     */
    public String sshDecrypt(String encryptedSecretKey, PrivateKey privateKey) {

        String secretKeyString = "";

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

            try {

                cipher.init(Cipher.PRIVATE_KEY, privateKey);

                try {
                    // Decrypt encrypted encoded secret key
                    byte[] encryptedSecretKeyByte = Base64.getDecoder().decode(encryptedSecretKey);
                    byte[] encodedSecretKey = cipher.doFinal(encryptedSecretKeyByte);

                    // Convert encoded secret key to key string
                    secretKeyString = new String(encodedSecretKey);

                } catch (IllegalBlockSizeException | BadPaddingException e) {
                    System.out.println("Exception occurs");
                }
            } catch (InvalidKeyException e) {
                System.out.println("Exception occurs");
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            System.out.println("Exception occurs");
        }

        return secretKeyString;
    }

    /**
     * Using the share secret key to encrypt payload message
     * @param message payload message to be encrypted
     * @param secretKeyString Base64 encoded secret key string
     * @return the encrypted payload message
     */
    public String aesEncrypt(String message, String secretKeyString) {

        String encryptedMessage = "";

        // Convert secret key string to secret key
        byte[] secretKeyBytes = Base64.getDecoder().decode(secretKeyString);
        SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, "AES");

        // Append newline character to the end of the json string
        String completeMessage = message + "\n";

        // Generate padding if needed
        if (completeMessage.length() % 16 > 0) {
            int paddingLength = 16 - (completeMessage.length() % 16);
            PaddingGenerator paddingGenerator = new PaddingGenerator(paddingLength);
            String padding = paddingGenerator.generatePadding();
            completeMessage = completeMessage + padding;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            try {
                // Encrypt complete message
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                System.out.println("Complete message:" + completeMessage.split("\n")[0]);
                byte[] plainText = completeMessage.getBytes("UTF-8");
                byte[] cipherText = cipher.doFinal(plainText);
                encryptedMessage = Base64.getEncoder().encodeToString(cipherText);

                } catch (InvalidKeyException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException e) {
                    System.out.println("Exception occurs");
                }
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                System.out.println("Exception occurs");
            }

        return encryptedMessage;
    }

    /**
     * Using the share secret key to decrypt payload message received
     * @param message the encrypted payload message
     * @param secretKeyString Base64 encoded secret key string
     * @return the decrypted payload message
     */
    public String aesDecrypt(String message, String secretKeyString) {

        String payloadMessage = "";

        // Convert secret key string to secret key
        byte[] secretKeyBytes = Base64.getDecoder().decode(secretKeyString);
        SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, "AES");

        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            try {
                cipher.init(Cipher.DECRYPT_MODE, secretKey);

                try {
                    // Decrypt encrypted message
                    byte[] cipherText = Base64.getDecoder().decode(message);
                    byte[] plainText = cipher.doFinal(cipherText);


                    // Extract json string from decrypted message
                    String decryptedMessage = new String(plainText, "UTF-8");
                    System.out.println("Decrypted complete message: " + decryptedMessage.split("\n")[0]);

                    int newlineIndex = 0;
                    for (int i = decryptedMessage.length()-1; i > 0; i--){
                        String s = Character.toString(decryptedMessage.charAt(i));
                        String prevs = Character.toString(decryptedMessage.charAt(i-1));
                        if (s.equals("\n") && prevs.equals("}")){
                            newlineIndex = i;
                            break;
                        }
                    }

                    // Drop new line character (and padding)
                    payloadMessage = decryptedMessage.substring(0, newlineIndex);

                } catch (UnsupportedEncodingException| IllegalBlockSizeException | BadPaddingException e) {
                    System.out.println("Exception occurs");
                }
            } catch (InvalidKeyException e) {
                System.out.println("Exception occurs");
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            System.out.println("Exception occurs");
        }

        return payloadMessage;
    }

    /**
     * Other methods used for system testing
     */
    public KeyPair generateRSAKeyPair() {

        KeyPair keyPair = null;

        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.out.println("Exception occurs");
        }
        return keyPair;
    }

    public void writePemFile(Key key, String description, String filename) throws IOException {
        PemFile pemFile = new PemFile(key, description);
        pemFile.write(filename);
    }

    public PublicKey generatePublicKey(KeyFactory factory, String filename) throws InvalidKeySpecException, IOException {
        PemFile pemFile = new PemFile(filename);
        byte[] content = pemFile.getPemObject().getContent();
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
        return factory.generatePublic(pubKeySpec);
    }

    public String encodeRSAPublicKey(RSAPublicKey publicKey){

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String publicKeyString = "";

        try {
            byte[] name = "ssh-rsa".getBytes("US-ASCII");
            write(name, buf);
            write(publicKey.getPublicExponent().toByteArray(), buf);
            write(publicKey.getModulus().toByteArray(), buf);

            byte[] publicKeyByte = buf.toByteArray();
            publicKeyString = Base64.getEncoder().encodeToString(publicKeyByte);
        } catch (IOException e) {
            System.out.println("Exception occurs");
        }
        return publicKeyString;
    }

    private void write(byte[] str, OutputStream os) throws IOException {
        for (int shift = 24; shift >= 0; shift -= 8)
            os.write((str.length >>> shift) & 0xFF);
        os.write(str);
    }

    public PublicKey getPublicKey(String identity, HashMap publicKeyHashMap) {

        PublicKey publicKey = (PublicKey) publicKeyHashMap.get(identity);
        return publicKey;
    }


    /**
     * Internal helper class: paddingGenerator
     * Modified from http://java2novice.com/java-collections-and-util/random/string/
     */
    private class PaddingGenerator {

        private static final String CHAR_LIST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        private int paddingLength;

        public PaddingGenerator(int paddingLength) {
            this.paddingLength = paddingLength;
        }

        public String generatePadding() {

            StringBuffer randStr = new StringBuffer();
            for(int i=0; i<paddingLength; i++){
                int number = getRandomNumber();
                char ch = CHAR_LIST.charAt(number);
                randStr.append(ch);
            }
            return randStr.toString();
        }

        private int getRandomNumber() {
            int randomInt = 0;
            Random randomGenerator = new Random();
            randomInt = randomGenerator.nextInt(CHAR_LIST.length());
            if (randomInt - 1 == -1) {
                return randomInt;
            } else {
                return randomInt - 1;
            }
        }
    }

}
