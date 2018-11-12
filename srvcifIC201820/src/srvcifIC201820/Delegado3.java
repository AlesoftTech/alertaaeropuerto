package srvcifIC201820;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Random;

import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;

public class Delegado3 extends Thread {
	// Constantes
	public static final String OK = "OK";
	public static final String ALGORITMOS = "ALGORITMOS";
	public static final String HMACMD5 = "HMACMD5";
	public static final String HMACSHA1 = "HMACSHA1";
	public static final String HMACSHA256 = "HMACSHA256";
	public static final String CERTSRV = "CERTSRV";
	public static final String CERCLNT = "CERCLNT";
	public static final String SEPARADOR = ":";
	public static final String HOLA = "HOLA";
	public static final String INICIO = "INICIO";
	public static final String ERROR = "ERROR";
	public static final String REC = "recibio-";
	// Atributos
	private Socket sc = null;
	private String dlg;
	private byte[] mybyte;
	
	Delegado3 (Socket csP, int idP) {
		sc = csP;
		dlg = new String("delegado " + idP + ": ");
		try {
		    mybyte = new byte[520]; 
		    mybyte = Coordinador.certSer.getEncoded( );
		} catch (Exception e) {
			System.out.println("Error creando encoded del certificado para el thread" + dlg);
			e.printStackTrace();
		}
	}
	
	public void run() {
		String linea;
	    System.out.println(dlg + "Empezando atencion.");
	        try {
				PrintWriter ac = new PrintWriter(sc.getOutputStream() , true);
				BufferedReader dc = new BufferedReader(new InputStreamReader(sc.getInputStream()));

				/***** Fase 1: Inicio *****/
				linea = dc.readLine();
				if (!linea.equals(HOLA)) {
					ac.println(ERROR);
				    sc.close();
					throw new Exception(dlg + ERROR + REC + linea +"-terminando.");
				} else {
					ac.println(OK);
					System.out.println(dlg + REC + linea + "-OK, continuando.");
				}
				
				/***** Fase 2: Algoritmos *****/
				linea = dc.readLine();
				if (!(linea.contains(SEPARADOR) && linea.split(SEPARADOR)[0].equals(ALGORITMOS))) {
					ac.println(ERROR);
					sc.close();
					throw new Exception(dlg + ERROR + REC + linea +"-terminando.");
				}
				
				String[] algoritmos = linea.split(SEPARADOR);
				if (!algoritmos[1].equals(Seg.DES) && !algoritmos[1].equals(Seg.AES) &&
					!algoritmos[1].equals(Seg.BLOWFISH) && !algoritmos[1].equals(Seg.RC4)){
					ac.println(ERROR);
					sc.close();
					throw new Exception(dlg + ERROR + "Alg.Simetrico" + REC + algoritmos + "-terminando.");
				}
				if (!algoritmos[2].equals(Seg.RSA)) {
					ac.println(ERROR);
					sc.close();
					throw new Exception(dlg + ERROR + "Alg.Asimetrico." + REC + algoritmos + "-terminando.");
				}
				if (!(algoritmos[3].equals(HMACMD5) || algoritmos[3].equals(HMACSHA1) ||
					  algoritmos[3].equals(HMACSHA256))) {
					ac.println(ERROR);
					sc.close();
					throw new Exception(dlg + ERROR + "AlgHash." + REC + algoritmos + "-terminando.");
				}
				System.out.println(dlg + REC + linea + "-OK, continuando.");
				ac.println(OK);

				/***** Fase 3: Recibe certificado del cliente *****/				
				String strCertificadoCliente = dc.readLine();				
				byte[] certificadoCbytes = new byte[520];
				certificadoCbytes = toByteArray(strCertificadoCliente);
				CertificateFactory creador = CertificateFactory.getInstance("X.509");
				InputStream in = new ByteArrayInputStream(certificadoCbytes);
				X509Certificate certificadoCliente = (X509Certificate)creador.generateCertificate(in);
				System.out.println(dlg + "recibio certificado del cliente. -OK, continuando.");
				ac.println(OK);
				
				/***** Fase 4: Envia certificado del servidor *****/
				ac.println(toHexString(mybyte));
				System.out.println(dlg + "envio certificado del servidor. continuando.");				
				linea = dc.readLine();
				if (!(linea.equals(OK))) {
					ac.println(ERROR);
					throw new Exception(dlg + ERROR + REC + linea + "-terminando.");
				}
				System.out.println(dlg + "recibio-" + linea + "-OK, continuando.");

				/***** Fase 5: Envia llave simetrica *****/
				SecretKey simetrica = Seg.kgg(algoritmos[1]);
				byte [ ] ciphertext1 = Seg.ae(simetrica.getEncoded(), 
						                 certificadoCliente.getPublicKey(), algoritmos[2]);
				ac.println(toHexString(ciphertext1));
				System.out.println(dlg + "envio llave simetrica al cliente. -OK, continuando.");
				
				/***** Fase 6: Confirma llave simetrica *****/
				linea = dc.readLine();
				byte[] llaveS = Seg.ad(
						toByteArray(linea), Coordinador.keyPairServidor.getPrivate(), algoritmos[2]);
				if (!toHexString(llaveS).equals(toHexString(simetrica.getEncoded()))) {
					ac.println(ERROR);
					throw new Exception(dlg + ERROR + "Problema confirmando llave. terminando.");
				}
				ac.println(OK);
				
				/***** Fase 7: Lectura de la consulta *****/
				linea = dc.readLine();				
				String datos = new String(Seg.sd(toByteArray(linea), simetrica, algoritmos[1]));
				linea = dc.readLine();
				byte[] hmac = toByteArray(linea);					
				boolean verificacion = Seg.vi(datos.getBytes(), simetrica, algoritmos[3], hmac);
				if (verificacion) {
					System.out.println(dlg + "verificacion de integridad. -OK, continuando.");
					boolean rta = esta(datos);
					if (rta) 
					  ac.println(OK + ":DEBE");
					else 
						ac.println(OK + ":PAZYSALVO");
				} else {
					ac.println(ERROR);
					throw new Exception(dlg + "Error en verificacion de integridad. -terminando.");
				}
				
		        sc.close();
		        System.out.println(dlg + "Termino exitosamente.");
				
	        } catch (Exception e) {
	          e.printStackTrace();
	        }
	}
	
	private boolean esta(String inDato) {
		int num = Integer.parseInt(inDato);
		Random rand = new Random(); 
		int value = rand.nextInt(10);
		while (value==0)
		    value=rand.nextInt();
		return ((num - value)%2)==1;
	}
	
	public String toHexString(byte[] array) {
	    return DatatypeConverter.printHexBinary(array);
	}

	public byte[] toByteArray(String s) {
	    return DatatypeConverter.parseHexBinary(s);
	}
	
}
