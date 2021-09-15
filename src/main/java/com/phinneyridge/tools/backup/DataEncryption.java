/*
 * Copyright 2021 PhinneyRidge.com
 *        Licensed under the Apache License, Version 2.0 (the "License");
 *        you may not use this file except in compliance with the License.
 *        You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *        Unless required by applicable law or agreed to in writing, software
 *        distributed under the License is distributed on an "AS IS" BASIS,
 *        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *        See the License for the specific language governing permissions and
 *        limitations under the License.
 */
package com.phinneyridge.tools.backup;

//import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;

public class DataEncryption {
    Provider provider = null; // = Security.getProvider("BC");
    private static final String IV = "encryptionIntVec";
    private final String AES_Transformation = "AES/CBC/PKCS5Padding";
    public Random random = new Random();

    public DataEncryption() {
        /*
        if (provider == null) {
            provider = new BouncyCastleProvider();
            Security.addProvider(provider);
        }
         */
    }

    /*
    KeyStore keyStore;
    KeyStore getKeyStore( ){
        if (keyStore == null) {
            try {
                keyStore = KeyStore.getInstance("bks");
                KeyStore keyStore = KeyStore.getInstance("bks");
                keyStore.load(new FileInputStream("keys/store/utility.bks"), "initialtrust".toCharArray());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return keyStore;
    }
     */

    public void cipherStreamEncrypt(String passPhrase, File inputFile, File outputFile) {

        FileOutputStream fileOutput = null;
        FileInputStream fileInput = null;
        try {
            if (!outputFile.exists()) {
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
            }
            outputFile.setWritable(true,false);
            fileOutput = new FileOutputStream(outputFile);
            fileInput = new FileInputStream(inputFile);
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_Transformation);
            byte[] ivBytes = new byte[16];
            byte[] saltBytes = new byte[8];
            random.nextBytes(saltBytes); // randomize the salt
            random.nextBytes(ivBytes); // randomize the ivBytes
            SecretKey secretKey = generateSecretKey(passPhrase, saltBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            //output = new CipherOutputStream(fileOutput,cipher);
            byte[] filebytes = new byte[1024];
            byte[]encryptedBytes;
            // file begins with 8 byte salt (PBE - password base encryption)
            fileOutput.write(saltBytes);
            // file then contains  16 byte initialization vector (used in AES encryption)
            fileOutput.write(ivBytes);
            // the aes encrypted contents now follows
            long fileSize = inputFile.length();
            // next we begin by encrypting the ivBytes.
            // when we decrypt the contents we use that to determine
            // if the right key was used.
            encryptedBytes = cipher.update(ivBytes);
            fileOutput.write(encryptedBytes);
            int bytesRead;
            // we now write out the encrypted file content
            if (fileSize > 0) {
                // this can be skipped for empty files  (size = 0);
                // The "fileInput.read" operation throws an "IOException: Stream Closed" for empty files
                while ((bytesRead = fileInput.read(filebytes, 0, 1024)) > 0) {
                    encryptedBytes = cipher.update(filebytes, 0, bytesRead);
                    fileOutput.write(encryptedBytes);
                }
            }
            encryptedBytes = cipher.doFinal();
            fileOutput.write(encryptedBytes);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            noSuchAlgorithmException.printStackTrace();
        } catch (NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                BadPaddingException | InvalidAlgorithmParameterException e) {
            // these exceptions are not expected
        } catch (IOException e) {
            // there was a problem writing the output to file;
        } finally {
            try {
                fileInput.close();
                fileOutput.close();
            } catch (IOException e) {
            }
        }


    }

    public void cipherStreamDecrypt(String passPhrase, File inputFile, File outputFile) throws InvalidKeyException {

        FileOutputStream fileOutput = null;
        FileInputStream fileInput = null;

        try {
            if (!outputFile.exists()) {
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
            }
            fileOutput = new FileOutputStream(outputFile);
            fileInput = new FileInputStream(inputFile);
        } catch (IOException e) {
            System.out.println("error opening files");
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_Transformation);
            byte[] saltBytes = new byte[8];
            byte[] ivBytes = new byte[16];
            // the encrypted file begins with 8 salt bytes
            fileInput.read(saltBytes);
            // the encrypted file begins with 16 initialization vector bytes
            fileInput.read(ivBytes);
            SecretKey secretKey = generateSecretKey(passPhrase, saltBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,iv);
            byte[] filebytes = new byte[1024];
            byte[]decryptedBytes;
            boolean firstTime = true;
            while (fileInput.available()>0) {
                int bytesRead = fileInput.read(filebytes,0, 1024);
                decryptedBytes = cipher.update(filebytes,0,bytesRead);
                if (firstTime) {
                    for (int i = 0; i<ivBytes.length; i++) {
                        if (ivBytes[i] != decryptedBytes[i]) {
                            throw new InvalidKeyException(
                                    "key used for decryption does not match key used for encryption");
                        }
                    }
                    firstTime = false;
                }
                fileOutput.write(decryptedBytes);
            }
            decryptedBytes = cipher.doFinal();
            fileOutput.write(decryptedBytes);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException |
                NoSuchPaddingException | InvalidAlgorithmParameterException e) {
            // These exceptions are not expected
        } catch (IOException e) {
            // this indicates a problem reading the file
        } finally {
            try {
                fileInput.close();
                fileOutput.close();
            } catch (IOException e) {
            }
        }

    }

    public String getHashName(String name) {
        return convertBytesToHex(Hash.MD5.checksumBytes(name));
    }

    public String convertBytesToHex(byte[] bytes) {
        return new BigInteger(1, bytes).toString(16);
    }

    public byte[] convertHexToBytes(String hexString) {
        byte[] bigintBytes = new BigInteger(hexString, 16).toByteArray();
        // BigInteger use byte[0] to express the sign +/-
        // return the bytes without the sign byte (which incidentally 0x00 is used to mean positive)
        if (bigintBytes[0] == 0x00) return Arrays.copyOfRange(bigintBytes, 1, bigintBytes.length);
        return bigintBytes;
    }

/*
    public String getKeyStorePassword(String certName) {
        String loc = new File("").getAbsolutePath() + "\\keys\\private\\pass/" + certName + ".pw";
        File keystoreFile = new File(loc);
        return new String(readFile(keystoreFile.getPath()));
    }
*/
    /**
     * Enumeration of available Hash Algorithms along with access to their checksum computation
     */
    public enum Hash {
        /**
         * MD5 hash
         */
        MD5("MD5"),

        /**
         * SHA1 hash
         */
        SHA1("SHA-1"),

        /**
         * SHA224 hash
         */
        SHA224("SHA-224"),

        /**
         * SHA256 hash
         */
        SHA256("SHA-256"),

        /**
         * SHA384 hash
         */
        SHA384("SHA-384"),

        /**
         * SHA512 hash
         */
        SHA512("SHA-512");

        private String name;

        Hash(String name) {
            this.name = name;
        }

        /**
         * get the hash algorithms name
         *
         * @return the name of the Hash algorithm
         */
        public String getName() {
            return name;
        }

        /**
         * compute the checksum for the given input file
         *
         * @param input file to compute checksum on
         * @return the hash algorithms value
         */
        public byte[] checksumBytes(File input) {
            try (InputStream in = new FileInputStream(input)) {
                MessageDigest digest = MessageDigest.getInstance(getName());
                byte[] block = new byte[4096];
                int length;
                while ((length = in.read(block)) > 0) {
                    digest.update(block, 0, length);
                }
                return digest.digest();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * compute the checksum for the given input file
         *
         * @param input file to compute checksum on
         * @return the hash algorithms value expressed as a hexadecimal string
         */
        public String checksum(File input) {
            byte[] bytes = checksumBytes(input);
            return new BigInteger(1, bytes).toString(16);
        }

        /**
         * compute the checksum for the given data
         *
         * @param data byte array containing the data to compute the checksum for
         * @return the hash algorithms value
         */
        public byte[] checksumBytes(byte[] data) {
            ByteArrayInputStream is = new ByteArrayInputStream(data);
            try {
                MessageDigest digest = MessageDigest.getInstance(getName());
                byte[] block = new byte[4096];
                int length;
                while ((length = is.read(block)) > 0) {
                    digest.update(block, 0, length);
                }
                return digest.digest();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * compute the checksum for the given data
         *
         * @param data byte array containing the data to compute the checksum for
         * @return the hash algorithms value expressed as a hexadecimal string
         */
        public String checksum(byte[] data) {
            byte[] bytes = checksumBytes(data);
            return new BigInteger(1, bytes).toString(16);
        }


        /**
         * compute the checksum for the given data
         *
         * @param data String containing the data to compute the checksum for
         * @return the hash algorithms value
         */
        public byte[] checksumBytes(String data) {
            return checksumBytes(data.getBytes());
        }

        /**
         * compute the checksum for the given data
         *
         * @param data String containing the data to compute the checksum for
         * @return the hash algorithms value expressed as a hexadecimal string
         */
        public String checksum(String data) {
            byte[] bytes = checksumBytes(data.getBytes());
            return new BigInteger(1, bytes).toString(16);
        }


    }

    public byte[] readFile(String filepath) {
        try {
            Path filePath = Paths.get(filepath);
            return Files.readAllBytes(filePath);
        } catch (IOException e) {

        }
        return null;
    }

    /**
     * Encrypt the given data using the given secretkey & ivSpec. The encrypted data is written out
     * clear text iv (byte[16]), AES encrypted data,
     * @param data the data to be encrypted
     * @param password used for generating the secretKey used for AES encryption - a 32 byte array
     * @param iv the 16 byte initialization vector
     * @return
     * @throws java.lang.Exception
     */
    byte[] encrypt(byte[] data, byte[] password, byte[] iv) throws java.lang.Exception {
    // Encrypt the given data using the given secretkey & ivSpec
        return null;
    }

    /**
     * generate a secret key using the given pass phrase
     * @param passPhrase the password or phase that is need to obtain the encryption key
     * @param salt an array of 8 random bytes used by the PBE algorithm
     * @return the secret key to use for the given secret.
     */
    public SecretKey generateSecretKey(String passPhrase, byte[]salt) {
        //SecretKey secretKey = new SecretKeySpec(secret, "AES");
        SecretKeyFactory factory = null;
        SecretKey secretKey = null;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, 10000, 128);
            SecretKey tmp = factory.generateSecret(spec);
            secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return secretKey;
    }

}
