package io.pivotal.pde.sample;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
//import com.gemstone.gemfire.cache.Region;
//import com.gemstone.gemfire.cache.client.ClientCache;
//import com.gemstone.gemfire.cache.client.ClientCacheFactory;
//import com.gemstone.gemfire.cache.client.ClientRegionShortcut;


public class Test {


	private static int parseIntArg(String in, String message){
    	int result = 0;

    	try{
    		result = Integer.parseInt(in);
    	} catch(NumberFormatException nfx){
    		System.err.println(message);
    		System.exit(1);
    	}
		return result;
	}

	private static void printUsage(){
		System.out.println("usage: Test --locator=host[port] --sleep=100 --threads=10");
		System.out.println("       --sleep is in milliseconds");
		System.out.println("       --threads may not exceed 64");
	}

	private static String LOCATOR_ARG="--locator=";
	private static String SLEEP_ARG = "--sleep=";
	private static String THREADS_ARG = "--threads=";
	private static Pattern LOCATOR_PATTERN= Pattern.compile("(\\S+)\\[(\\d{1,5})\\]");

	private static String locatorHost = "";
	private static int locatorPort = 0;
	private static int sleep = 0;
	private static int threads = 1;

	private static Region<String,String> lockRegion;
	private static Region<String, Integer> dataRegion;

	private static void parseArgs(String []args){
    	for(String arg:args){
    		if (arg.startsWith(LOCATOR_ARG)){
    			String val = arg.substring(LOCATOR_ARG.length());
    			Matcher m = LOCATOR_PATTERN.matcher(val);
    			if (!m.matches()){
    				System.out.println("argument \"" + val + "\" does not match the locator pattern \"host[port]\"");
    				System.exit(1);
    			} else {
    				locatorHost = m.group(1);
    				locatorPort = parseIntArg(m.group(2), "locator port must be a number");
    			}
    		} else if (arg.startsWith(SLEEP_ARG)){
    			String val = arg.substring(SLEEP_ARG.length());
    			sleep = parseIntArg(val, "sleep argument must be a number");
    		} else if (arg.startsWith(THREADS_ARG)){
    			String val = arg.substring(THREADS_ARG.length());
    			threads = parseIntArg(val, "threads argument must be a number");
    		} else {
    			System.out.println("unrecognized argument: " + arg);
    			System.exit(1);
    		}
    	}

    	if (locatorHost.length() == 0){
    		System.out.println("--locator argument is required");
    		System.exit(1);
    	}

    	if (sleep < 0){
    		System.out.println("sleep argument may not be negative");
    		System.exit(1);
    	}

	}

	private static void clearRegion(Region r){
		Set<Object> keys = r.keySetOnServer();
		for(Object k: keys) r.remove(k);
		System.out.println("cleared region: " + r.getName());
	}

	public static void main( String[] args )
    {
		if (args.length == 0){
			printUsage();
			System.exit(1);
		}

		parseArgs(args);

    	ClientCache cache = new ClientCacheFactory().addPoolLocator(locatorHost, locatorPort).create();
    	dataRegion = cache.<String,Integer>createClientRegionFactory(ClientRegionShortcut.PROXY).create("data-region");
    	lockRegion = cache.<String,String>createClientRegionFactory(ClientRegionShortcut.PROXY).create("locking-region");

    	clearRegion(dataRegion);
    	clearRegion(lockRegion);

    	String [] accounts = {"A","B","C","D","E"};

    	Stats []stats = new Stats[accounts.length];
    	for(int i=0;i < accounts.length; ++i) stats[i] = new Stats();

		Worker []workers = new Worker[threads];
		for(int i=0;i<threads; ++i){
			workers[i] = new Worker(accounts);
			workers[i].start();
		}

		System.out.println("\nTest Running. Hit Enter to Stop.");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		try {
			reader.readLine();
		} catch(IOException iox){
			// ok
		}

		// wait for enter

		for(int i=0;i<threads; ++i){
			try {
				workers[i].shutdown();
				Stats []workerStats = workers[i].getStats();
				for(int j=0;j<accounts.length; ++j) stats[j].accumulate(workerStats[j]);
			} catch(InterruptedException x){
				System.out.println("interrupted while waiting for worker thread to stop ");
			}
		}

		//personRegion.close();
		cache.close();

		System.out.println("\nRun Results");
		System.out.println("===================================================");
		for (int i=0;i < accounts.length; ++i){
			System.out.println(String.format("%s: txns=%7d bal=%9d", accounts[i], stats[i].getTransactions(), stats[i].getBalance()));
		}
		System.out.println();
    }


	private static class Worker extends Thread {

		private String []accounts;
		private AtomicBoolean running;
		private Random rand;
		private Stats [] stats;
		private DateFormat dateFormat;
		private PrintWriter log;

		public Stats []getStats() { return this.stats;}

		public Worker(String []accounts){
			super();
			this.setDaemon(false);
			this.accounts = accounts;
			this.running = new AtomicBoolean(true);
			this.rand = new Random(17);
			this.stats = new Stats[accounts.length];
			for(int i=0; i< accounts.length; ++i) this.stats[i] = new Stats();
			this.dateFormat =  new SimpleDateFormat("HH:mm:ss.SSS");
		}

		public void shutdown() throws InterruptedException {
			if (running.compareAndSet(true, false)){
				this.interrupt();
				this.join();
			}
			log.close();
		}

		private void doStep(){
			int n = rand.nextInt(accounts.length);
			String acct = this.accounts[n];
			Stats stats = this.stats[n];
			String locker = lockRegion.putIfAbsent(acct, Thread.currentThread().getName() + dateFormat.format(new Date()));
			if (locker == null){
				log.println(String.format("%d %s LOCKED", System.currentTimeMillis(), acct));
				Integer currBal = dataRegion.get(acct);
				if (currBal == null) currBal = new Integer(0);
				int randomAmount = rand.nextInt(100);
				if (randomAmount == 0) randomAmount = 10;
				Integer newBal = currBal.intValue() + randomAmount;

				dataRegion.put(acct, newBal);

				lockRegion.remove(acct);
				log.println(String.format("%d %s UNLOCKED", System.currentTimeMillis(), acct));
				stats.incrementBalance(randomAmount);
			} else {
				log.println(String.format("%d %s LOCKFAILED", System.currentTimeMillis(), acct));
			}
		}

		@Override
		public void run(){
			try {
				log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(Thread.currentThread().getName() + ".log")));
			} catch(IOException iox){
				throw new RuntimeException("Error opening thread log file", iox);
			}
			while(running.get()){
				try {
					doStep();
				} catch (Exception x){
					System.err.println("XXXXXXXXXXXX");
					x.printStackTrace(System.err);
				}

				if (sleep > 0 && !this.isInterrupted()){
					try {
						Thread.sleep(sleep);
					} catch(InterruptedException x){
						// no bigs
					}
				}
			}
		}
	}
}
