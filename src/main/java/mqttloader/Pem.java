/*
 * Copyright 2020 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mqttloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import mqttloader.Constants.PemFormat;

public class Pem {
	private final byte[] keyData;
    private final List<byte[]> certDataList;
    private PemFormat format;

    public Pem(String filePath) {
        List<String> originalLines = null;
        try {
            originalLines = Files.readAllLines(Paths.get(filePath), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> parsedLines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for(String line: originalLines){
            PemFormat f = checkBeginString(line);
            if(f!=null) {
                format = f;
                sb.append(line.replace(f.getBegin(), "").trim());
            } else if((f=checkEndString(line))!=null) {
                sb.append(line.replace(f.getEnd(), "").trim());
                parsedLines.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(line.trim());
            }
        }

        if (format == PemFormat.RFC7468_CERT || format == PemFormat.X509) {
            keyData = null;
            certDataList = new ArrayList<byte[]>();
            for (String line : parsedLines) {
                certDataList.add(Base64.getDecoder().decode(line));
            }
        } else {
            certDataList = null;
            keyData = Base64.getDecoder().decode(parsedLines.get(0));
        }
    }

    private PemFormat checkBeginString(String str){
        for(PemFormat format: PemFormat.values()){
            if(str.contains(format.getBegin())){
                return format;
            }
        }
        return null;
    }

    private PemFormat checkEndString(String str){
        for(PemFormat format: PemFormat.values()){
            if(str.contains(format.getEnd())){
                return format;
            }
        }
        return null;
    }

    public List<Certificate> getCerts() {
        CertificateFactory certFactory;
        List<Certificate> certs = null;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
            certs = new ArrayList<Certificate>();
            for(byte[] certData : certDataList) { 
                certs.add(certFactory.generateCertificate(new ByteArrayInputStream(certData)));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        return certs;
    }

    public PrivateKey getPrivateKey() {
        KeySpec keySpec = null;
        RuntimeException exception = new RuntimeException("Could not obtain Private Key.");

        switch (format) {
            case PKCS1: {
                keySpec = parsePKCS1(keyData);
                break;
            }
            case PKCS8: {
                keySpec = new PKCS8EncodedKeySpec(keyData);
                break;
            }
            default:
                throw exception;
        }

        String[] algorithms = new String[]{"RSA", "DSA", "EC"};
        for(String alg : algorithms){
            try {
                return KeyFactory.getInstance(alg).generatePrivate(keySpec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                exception.addSuppressed(e);
            }
        }
        throw exception;
    }

    private KeySpec parsePKCS1(byte[] keyData) {
        int pkcs1Length = keyData.length;
        int pkcs8Length = pkcs1Length + 22; // As stated below, PKCS#8 needs 26 bytes in addition to PKCS#1 data. pkcs8Length is used as the value of SEQUENCE length and it does not include the first 4 bytes (0x30, 0x82, length-upper-byte, length-lower-byte).
        byte[] pkcs8Header = new byte[] {
            0x30,    // "SEQUENCE"
            (byte) 0x82,    // "The following two bytes indicate the length of this field"
            (byte) ((pkcs8Length >> 8) & 0xff), // Extract upper byte of the total length
            (byte) (pkcs8Length & 0xff), // Extract lower byte of the total length
            // Example: if total length is 294 bytes, 294 = 0x0126 = 0b 00000001 00100110 => 8bit right shift => 00000001 & 11111111 = 00000001
            // 294 = 0x0126 = 0b 00000001 00100110 => 00000001 00100110 & 00000000 11111111 = 00100110
            0x2,    // "INTEGER" for version
            0x1,    // length: 1 byte
            0x0,    // value: 0
            0x30,   // "SEQUENCE" for privateKeyAlgorithm ID
            0xD,    // length: 13 bytes
            0x6,    // "OBJECT IDENTIFIER"
            0x9,    // length: 9 bytes
            0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0xD, 0x1, 0x1, 0x1,  // value: 1.2.840.113549.1.1.1 - RSA encryption
            0x5, 0x0, // "NULL" with length field 0x00
            0x4,    // "OCTET STRING" for PKCS#1 data
            (byte) 0x82,    // "The following two bytes indicate the length of this field"
            (byte) ((pkcs1Length >> 8) & 0xff), // Extract upper byte
            (byte) (pkcs1Length & 0xff) // Extract lower byte
        };
        byte[] pkcs8bytes = ByteBuffer.allocate(pkcs8Header.length + keyData.length).put(pkcs8Header).put(keyData).array();
        return new PKCS8EncodedKeySpec(pkcs8bytes);
    }
}
