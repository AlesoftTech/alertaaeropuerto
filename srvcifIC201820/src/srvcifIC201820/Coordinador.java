package srvcifIC201820;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinador {

	private static ServerSocket ss;
	private static final String MAESTRO = "MAESTRO: ";
	static java.security.cert.X509Certificate certSer; /* acceso default */
	static KeyPair keyPairServidor; /* acceso default */

	private static int poolSize;
	private static int numTransacciones;
	private static int respuesta;

	public static void generarLog() {
		String titulo = "Log_pool_tamanio" + poolSize + "_num_trans" + numTransacciones + "_respuesta" + respuesta
				+ ".txt";
		File logPath = new File(titulo);
		String fecha = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(new Date());
		try {
			final BufferedWriter escritor = new BufferedWriter(new FileWriter(logPath.getPath()));
			writer.write("Tamaño del pool: " + poolSize);
			writer.newLine();
			writer.write("Transacciones: " + numTransacciones);
			writer.newLine();
			writer.write("Replica: " + respuesta);
			writer.newLine();
			writer.write("Hora del inicio del proceso: " + fecha);
			writer.newLine();
			writer.close();
		} catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}

	}

	private static void escribirEnElLog(String line) {
		try {
			String titulo = "Log_pool_tamanio" + poolSize + "_num_trans" + numTransacciones + "_respuesta" + respuesta
					+ ".txt";
			File logPath = new File(titulo);
			BufferedWriter escritor = new BufferedWriter(new FileWriter(logPath.getPath(), true));
			escritor.write(line + System.getProperty("line.separator"));
			escritor.close();

		} catch (IOException e) {
			e.printStackTrace();
			// TODO: handle exception
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		// System.out.println(MAESTRO + "Establezca puerto de conexion:");
		// InputStreamReader isr = new InputStreamReader(System.in);
		// BufferedReader br = new BufferedReader(isr);
		// int ip = Integer.parseInt(br.readLine());
		// System.out.println(MAESTRO + "Empezando servidor maestro en puerto " + ip);
		// // Adiciona la libreria como un proveedor de seguridad.
		// // Necesario para crear certificados.
		// Security.addProvider(new
		// org.bouncycastle.jce.provider.BouncyCastleProvider());
		// keyPairServidor = Seg.grsa();
		// certSer = Seg.gc(keyPairServidor);
		//
		//
		//
		// int idThread = 0;
		// // Crea el socket que escucha en el puerto seleccionado.
		// ss = new ServerSocket(ip);
		// System.out.println(MAESTRO + "Socket creado.");
		// while (true) {
		// try {
		// Socket sc = ss.accept();
		// System.out.println(MAESTRO + "Cliente " + idThread + " aceptado.");
		// //Delegado3 d3 = new Delegado3(sc,idThread);
		// idThread++;
		// //d3.start();
		// } catch (IOException e) {
		// System.out.println(MAESTRO + "Error creando el socket cliente.");
		// e.printStackTrace();
		// }
		// }
		System.out.println(MAESTRO + "Establezca puerto de conexion:");
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		int ip = Integer.parseInt(br.readLine());
		System.out.println(MAESTRO + "Empezando servidor maestro en puerto " + ip);
		// Adiciona la libreria como un proveedor de seguridad.
		// Necesario para crear certificados.
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		keyPairServidor = Seg.grsa();
		certSer = Seg.gc(keyPairServidor);

		try {
			poolSize = Integer.parseInt(args[0]);
			numTransacciones = Integer.parseInt(args[1]);
			respuesta = Integer.parseInt(args[2]);
		} catch (Exception e) {
			System.out.println(
					"establezca: el numero de Threads en el pool, el numero de transacciones y el numero de replica");
			return;
		}

		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(tamPool);
		List<Future<Double[]>> resultados = new ArrayList<Future<Double[]>>();
		startLog();

		int idThread = 0;

		// Crea el socket que escucha en el puerto seleccionado.
		ss = new ServerSocket(ip);
		System.out.println(MAESTRO + "Socket creado.");
		while (idThread < numTrans) {
			try {
				Socket sc = ss.accept();
				System.out.println(MAESTRO + "Cliente " + idThread + " aceptado.");
				Future<Double[]> future = executor.submit(new Delegado3(sc, idThread));
				idThread++;
				resultados.add(future);
			} catch (IOException e) {
				System.out.println(MAESTRO + "Error creando el socket cliente.");
				e.printStackTrace();
			}
		}

		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {

		}

		double meanVerify = 0;
		double meanQuery = 0;
		double meanCPU = 0;
		double stdVerify = 0;
		double stdQuery = 0;
		double stdCPU = 0;
		double transComplete = 0;

		for (Future<Double[]> result : resultados) {
			appendToLog("Tiempo de verificacion: " + String.format("%.5f", result.get()[0]) + " - Tiempo de consulta: "
					+ String.format("%.5f", result.get()[1]) + " - Consumo de CPU: "
					+ String.format("%.4f%%", result.get()[3] * 100));
			meanVerify += result.get()[0];
			meanQuery += result.get()[1];
			transComplete += result.get()[2];
			meanCPU += result.get()[3];
		}

		meanVerify = meanVerify / transComplete;
		meanQuery = meanQuery / transComplete;
		meanCPU = meanCPU / transComplete;

		for (Future<Double[]> result : resultados) {
			stdVerify += Math.pow(result.get()[0] - meanVerify, 2);
			stdQuery += Math.pow(result.get()[1] - meanQuery, 2);
			stdCPU += Math.pow(result.get()[3] - meanCPU, 2);
		}

		stdVerify = Math.sqrt(stdVerify / transComplete);
		stdQuery = Math.sqrt(stdQuery / transComplete);
		stdCPU = Math.sqrt(stdCPU / transComplete);

		appendToLog("");
		appendToLog("**********************************");
		appendToLog("Tiempo promedio de la verificacion: " + String.format("%.5f", meanVerify) + " ms");
		appendToLog("Desviacion estandar de la verificacion: " + String.format("%.5f", stdVerify) + " ms");
		appendToLog("Tiempo promedio de la consulta: " + String.format("%.5f", meanQuery) + " ms");
		appendToLog("Desviacion estandar de la consulta: " + String.format("%.5f", stdQuery) + " ms");
		appendToLog("Transacciones perdidas: " + (int) (numTrans - transComplete));
		appendToLog("Consumo promedio CPU: " + String.format("%.4f%%", meanCPU * 100));
		appendToLog("Desviacion estandar Consumo CPU: " + String.format("%.4f%%", stdCPU * 100));
		ss.close();
		System.out.println(MAESTRO + "Termina.");
		System.exit(0);

	}

}
