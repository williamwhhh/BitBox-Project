// Code below is copied from https://www.txedo.com/blog/java-generate-rsa-keys-write-pem-file/
// and https://www.txedo.com/blog/java-read-rsa-keys-pem-file/

package unimelb.bitbox;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.Key;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

public class PemFile {

    private PemObject pemObject;

    public PemFile (Key key, String description) {
        this.pemObject = new PemObject(description, key.getEncoded());
    }

    public void write(String filename) throws IOException {
        PemWriter pemWriter = new PemWriter(new OutputStreamWriter(new FileOutputStream(filename)));
        try {
            pemWriter.writeObject(this.pemObject);
        } finally {
            pemWriter.close();
        }
    }

    public PemFile(String filename) throws IOException {
        PemReader pemReader = new PemReader(new InputStreamReader(new FileInputStream(filename)));
        try {
            this.pemObject = pemReader.readPemObject();
        } finally {
            pemReader.close();
        }
    }

    public PemObject getPemObject() {
        return pemObject;
    }
}
